package org.mmisw.orr.ont

import com.mongodb.casbah.Imports._
import com.typesafe.scalalogging.slf4j.Logging
import org.scalatra.Created

import org.scalatra.servlet.{FileItem, SizeConstraintExceededException, FileUploadSupport}
import javax.servlet.annotation.MultipartConfig
import java.io.File
import java.net.{URI, URISyntaxException}
import org.mmisw.orr.ont.db.{OntologyVersion, Ontology}
import org.joda.time.DateTime
import scala.util.{Failure, Success, Try}
import com.novus.salat._
import com.novus.salat.global._


/**
 * Controller for the "ont" API.
 */
@MultipartConfig(maxFileSize = 5*1024*1024)
class OntController(implicit setup: Setup, ontService: OntService) extends BaseController
      with FileUploadSupport with Logging {

  //configureMultipartHandling(MultipartConfig(maxFileSize = Some(5 * 1024 * 1024)))

  error {
    case e: SizeConstraintExceededException =>
      error(413, "The file you uploaded exceeded the 5MB limit.")
  }

  /*
   * General ontology request
   */
  get("/") {
    params.get("uri") match {
      case Some(uri) => resolveUri(uri)
      case None =>
        val query = getQueryFromParams(params.keySet)
        // TODO what exactly to report for the list of ontologies?
        ontService.getOntologies(query) map grater[PendOntologyResult].toCompactJSON
    }
  }

  /*
   * Registers a new ontology entry.
   */
  post("/") {
    val uri = require(params, "uri")
    val name = require(params, "name")
    val orgName = require(params, "orgName")
    val user = verifyUser(params.get("userName"))

    // TODO allow absent orgName so user can submit on her own behalf?

    orgsDAO.findOneById(orgName) match {
      case None =>
        error(400, s"'$orgName' invalid organization")

      case Some(org) =>
        verifyAuthenticatedUser(org.members :+ "admin": _*)
    }

    // ok, go ahead with registration
    val (fileItem, format) = getFileAndFormat
    val (version, date) = getVersion

    Created(createOnt(uri, name, version, date, fileItem, orgName))
  }

  /*
   * Updates a given version or adds a new version.
   */
  put("/") {
    val uri = require(params, "uri")
    val versionOpt = params.get("version")
    val user = verifyUser(params.get("userName"))

    val (ont, _, _) = resolveOntology(uri, versionOpt)

    ont.orgName match {
      case Some(orgName) =>
        orgsDAO.findOneById(orgName) match {
          case None =>
            bug(s"org '$orgName' should exist")

          case Some(org) =>
            verifyAuthenticatedUser(org.members :+ "admin": _*)
        }

      case None =>
        bug(s"currently I expect registered ont to have org associated")
    }

    // ok, authenticated user can PUT.

    versionOpt match {
      case Some(version) => updateVersion(uri, version, user)
      case None => addVersion(uri, user)
    }
  }

  /*
   * Deletes a particular version or the whole ontology entry.
   */
  delete("/") {
    val uri = require(params, "uri")
    val versionOpt = params.get("version")
    val user = verifyUser(params.get("userName"))

    deleteOnt(uri, versionOpt, user)
  }

  delete("/!/all") {
    verifyAuthenticatedUser("admin")
    ontDAO.remove(MongoDBObject())
  }

  ///////////////////////////////////////////////////////////////////////////

  private def createOnt(uri: String, name: String, version: String, date: String,
                fileItem: FileItem, orgName: String) = {

    ontDAO.findOneById(uri) match {
      case None =>
        validateUri(uri)

        writeOntologyFile(uri, version, fileItem, format)

        val ontVersion = OntologyVersion(name, user.userName, format, new DateTime(date))
        val ont = Ontology(uri, Some(orgName),
          versions = Map(version -> ontVersion))

        Try(ontDAO.insert(ont, WriteConcern.Safe)) match {
          case Success(uriR) =>
            OntologyResult(uri, version = Some(version), registered = Some(ontVersion.date))

          case Failure(exc)  => error(500, s"insert failure = $exc")
          // TODO note that it might be a duplicate key in concurrent registration
        }

      case Some(ont) =>   // bad request: existing ontology entry.
        error(409, s"'$uri' is already registered")
    }
  }

  private def deleteOnt(uri: String, versionOpt: Option[String], user: db.User) = {
    val (ont, _, _) = resolveOntology(uri, versionOpt)

    ont.orgName match {
      case Some(orgName) =>
        orgsDAO.findOneById(orgName) match {
          case None => bug(s"org '$orgName' should exist")

          case Some(org) => verifyAuthenticatedUser(org.members :+ "admin": _*)
        }

      case None =>
        bug(s"currently I expect registered ont to have org associated")
    }

    versionOpt match {
      case Some(version) => deleteVersion(uri, version, user)
      case None          => deleteOntology(uri, user)
    }
  }

  /**
   * Preliminary mapping from given parameters to a query for filtering purposes
   * @param keys keys to be considered
   * @return MongoDBObject
   */
  /*
   * TODO(low priority) more options for the query, eg., glob filtering (orgName=mmi*),
   * or perhaps allow to pass a Mongo query directly in an special
   * parameter, eg (with appropriate encoding: mq={orgName:{$in:['mmi','foo']}}
   */
  private def getQueryFromParams(keys: Set[String]): MongoDBObject = {
    var query = MongoDBObject()
    if (keys.size > 0) {
      keys foreach (key => query = query.updated(key, params.get(key).get))
      logger.debug(s"GET query=$query")
    }
    query
  }

  private val versionFormatter = new java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss")

  private def resolveUri(uri: String) = {
    val (ont, ontVersion, version) = resolveOntology(uri, params.get("version"))

    // format is the one given if any, or the one in the db:
    val reqFormat = params.get("format").getOrElse(ontVersion.format)

    // todo: determine mechanism to request for file contents or metadata:  format=!md is preliminary

    if (reqFormat == "!md") {
      val ores = PendOntologyResult(ont.uri, ontVersion.name, ont.orgName, ont.sortedVersionKeys)
      grater[PendOntologyResult].toCompactJSON(ores)
    }
    else {
      val (file, actualFormat) = getOntologyFile(uri, version, reqFormat)
      contentType = formats(actualFormat)
      file
    }
  }

  private def resolveOntology(uri: String, versionOpt: Option[String]): (Ontology, OntologyVersion, String) = {
    Try(ontService.resolveOntology(uri, versionOpt)) match {
      case Success(res)         => res
      case Failure(exc: NoSuch) => error(404, exc.message)
      case Failure(exc)         => error(500, exc.getMessage)
    }
  }

  private def getOntologyFile(uri: String, version: String, reqFormat: String): (File, String) = {
    Try(ontService.getOntologyFile(uri, version, reqFormat)) match {
      case Success(res)                   => res
      case Failure(exc: NoSuchOntFormat)  => error(406, exc.message)
      case Failure(exc) => error(500, exc.getMessage)
    }
  }

  /**
   * Verifies the given organization and the userName against that organization.
   */
  private def verifyOrgAndUser(orgName: String, userName: String): Unit = {
    orgsDAO.findOneById(orgName) match {
      case Some(org) =>
        if (!org.members.contains(userName))
          error(401, s"user '$userName' is not a member of organization '$orgName'")

      case None =>
        bug(s"'$orgName' organization must exist")
    }
  }

  private def validateUri(uri: String) {
    try new URI(uri)
    catch {
      case e: URISyntaxException => error(400, s"invalid URI '$uri': ${e.getMessage}")
    }
  }

  private def getFileAndFormat = {
    val fileItem = fileParams.getOrElse("file", missing("file"))

    // todo make format param optional
    val format = require(params, "format")

    logger.info(s"uploaded file=${fileItem.getName} size=${fileItem.getSize} format=$format")
    //val fileContents = new String(fileItem.get(), fileItem.charset.getOrElse("utf8"))
    //val contentType = file.contentType.getOrElse("application/octet-stream")

    (fileItem, format)
  }

  private def getVersion = {
    // for now, the version is always automatically assigned
    val now = new java.util.Date()
    val version = versionFormatter.format(now)
    val date    = dateFormatter.format(now)
    (version, date)
  }

  /**
   * Verifies the user can make changes or removals wrt to
   * the given ont.
   * @param userName
   * @param ont
   */
  private def verifyOwner(userName: String, ont: Ontology): Unit = {
    ont.orgName match {
      case Some(orgName) =>
        verifyOrgAndUser(orgName, userName)

      case None => // TODO handle no-organization case
        bug(s"currently I expect registered ont to have org associated")
    }
  }

  /**
   * Adds a new version of a registered ontology.
   */
  private def addVersion(uri: String, user: db.User) = {
    val nameOpt = params.get("name")
    val (fileItem, format) = getFileAndFormat
    val (version, date) = getVersion

    var ontVersion = OntologyVersion("", user.userName, format, new DateTime(date))

    ontDAO.findOneById(uri) match {
      case None => error(404, s"'$uri' is not registered")

      case Some(ont) =>
        verifyOwner(user.userName, ont)

        var update = ont

        nameOpt foreach (name => ontVersion = ontVersion.copy(name = name))
        update = update.copy(
          versions = ont.versions ++ Map(version -> ontVersion))

        logger.info(s"update: $update")
        writeOntologyFile(uri, version, fileItem, format)

        Try(ontDAO.update(MongoDBObject("_id" -> uri), update, false, false, WriteConcern.Safe)) match {
          case Success(result) =>
            OntologyResult(uri, version = Some(version), updated = Some(ontVersion.date))

          case Failure(exc)  => error(500, s"update failure = $exc")
        }
    }
  }

  /**
   * updates a particular version.
   * Note, only the name in the particular version can be updated at the moment.
   */
  // TODO handle other pieces that can/should be updated
  private def updateVersion(uri: String, version: String, user: db.User) = {
    val name = require(params, "name")
    ontDAO.findOneById(uri) match {
      case None => error(404, s"'$uri' is not registered")

      case Some(ont) =>
        verifyOwner(user.userName, ont)

        var ontVersion = ont.versions.getOrElse(version,
          error(404, s"'$uri', version '$version' is not registered"))

        ontVersion = ontVersion.copy(name = name)

        val newVersions = ont.versions.updated(version, ontVersion)
        val update = ont.copy(versions = newVersions)
        logger.info(s"update: $update")

        Try(ontDAO.update(MongoDBObject("_id" -> uri), update, false, false, WriteConcern.Safe)) match {
          case Success(result) =>
            OntologyResult(uri, version = Some(version), updated = Some(ontVersion.date))

          case Failure(exc)  => error(500, s"update failure = $exc")
        }
    }
  }

  /**
   * Deletes a particular version.
   */
  private def deleteVersion(uri: String, version: String, user: db.User) = {
    ontDAO.findOneById(uri) match {
      case None => error(404, s"'$uri' is not registered")

      case Some(ont) =>
        verifyOwner(user.userName, ont)

        ont.versions.getOrElse(version, error(404, s"'$uri', version '$version' is not registered"))

        val update = ont.copy(versions = ont.versions - version)
        logger.info(s"update: $update")

        Try(ontDAO.update(MongoDBObject("_id" -> uri), update, false, false, WriteConcern.Safe)) match {
          case Success(result) =>
            OntologyResult(uri, version = Some(version), removed = Some(DateTime.now())) //TODO

          case Failure(exc)  => error(500, s"update failure = $exc")
        }
    }
  }

  /**
   * Deletes a whole ontology entry.
   */
  private def deleteOntology(uri: String, user: db.User) = {
    ontDAO.findOneById(uri) match {
      case None => error(404, s"'$uri' is not registered")

      case Some(ont) =>
        verifyOwner(user.userName, ont)

        Try(ontDAO.remove(ont, WriteConcern.Safe)) match {
          case Success(result) =>
            OntologyResult(uri, removed = Some(DateTime.now())) //TODO

          case Failure(exc)  => error(500, s"update failure = $exc")
        }
    }
  }

  private def writeOntologyFile(uri: String, version: String,
                    file: FileItem, format: String) = {

    val baseDir = setup.filesConfig.getString("baseDirectory")
    val ontsDir = new File(baseDir, "onts")

    !uri.contains("|") || error(400, s"'$uri': invalid URI")

    val uriEnc = uri.replace('/', '|')

    val uriDir = new File(ontsDir, uriEnc)

    val versionDir = new File(uriDir, version)
    versionDir.mkdirs() || error(500, s"could not create directory: $versionDir")

    val destFilename = s"file.$format"
    val dest = new File(versionDir, destFilename)

    file.write(dest)
  }

}
