package org.mmisw.orr.ont

import com.mongodb.casbah.Imports._
import com.typesafe.scalalogging.slf4j.Logging

import org.scalatra.servlet.{FileItem, SizeConstraintExceededException, FileUploadSupport}
import javax.servlet.annotation.MultipartConfig
import java.io.File
import java.net.{URI, URISyntaxException}
import org.mmisw.orr.ont.db.{OntologyVersion, Ontology}
import org.joda.time.DateTime
import scala.util.{Failure, Success, Try}
import com.novus.salat._
import com.novus.salat.global._

import org.mmisw.orr.ont.swld.ontUtil


@MultipartConfig(maxFileSize = 5*1024*1024)
class OntController(implicit setup: Setup) extends BaseController
      with FileUploadSupport with Logging {

  //configureMultipartHandling(MultipartConfig(maxFileSize = Some(5 * 1024 * 1024)))

  error {
    case e: SizeConstraintExceededException =>
      error(413, "The file you uploaded exceeded the 5MB limit.")
  }

  /*
   * Registers a new ontology entry.
   */
  post("/") {
    val uri = require(params, "uri")
    val name = require(params, "name")
    val orgNameOpt = params.get("orgName")
    val user = verifyUser(params.get("userName"))

    orgNameOpt match {
      case Some(orgName) =>
        orgsDAO.findOneById(orgName) match {
          case None =>
            error(400, s"'$orgName' invalid organization")

          case Some(org) =>
            verifyAuthenticatedUser(org.members :+ "admin": _*)
        }

      case None =>
        // No org given. TODO could user submit on her own behalf?
        missing("orgName")
    }

    // ok, go ahead with registration
    val owners = getRequestOwners
    val (fileItem, format) = getFileAndFormat
    val (version, date) = getVersion

    ontDAO.findOneById(uri) match {
      case None =>
        validateUri(uri)

        writeOntologyFile(uri, version, fileItem, format)

        val ontVersion = OntologyVersion(name, user.userName, format, new DateTime(date))
        val ont = Ontology(uri, orgNameOpt,
          owners = owners,
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

  /*
   * General ontology request
   */
  get("/(.*)".r) {
    params.get("uri") match {
      case Some(uri) => resolveUri(uri)

      case None =>
        val someSuffix = multiParams("captures").toList(0).length > 0
        if (someSuffix) selfResolve
        else {
          // TODO what exactly to report for the list of all ontologies?
          ontDAO.find(MongoDBObject()) map { ont =>
            getLatestVersion(ont) match {
              case Some((ontVersion, version)) =>
                val ores = PendOntologyResult(ont.uri, ontVersion.name, sortedVersionKeys(ont))
                grater[PendOntologyResult].toCompactJSON(ores)

              case None =>  // should not happen
                bug(s"'${ont.uri}', no versions registered")
            }
          }
        }
    }
  }

  /*
   * Dispatches organization OR user ontology request (.../ont/xyz)
   */
  get("/:xyz") {
    val xyz = require(params, "xyz")

    orgsDAO.findOneById(xyz) match {
      case Some(org) =>
        org.ontUri match {
          case Some(ontUri) => resolveUri(ontUri)
          case None =>
            try selfResolve
            catch {
              case exc: AnyRef =>
                logger.info(s"EXC in selfResolve: $exc")
                // TODO dispatch some synthetic response as in previous Ont
                error(500, s"TODO: generate summary for organization '$xyz'")
            }
        }
      case None =>
        usersDAO.findOneById(xyz) match {
          case Some(user) =>
            user.ontUri match {
              case Some(ontUri) => resolveUri(ontUri)
              case None => error(404, s"No ontology found for: '$xyz'")
            }
          case None => error(404, s"No organization or user by given name: '$xyz'")
        }
    }
  }

  /*
   * Updates a given version or adds a new version.
   *
   * TODO handle self-uri in put
   */
  put("/") {
    val uri = require(params, "uri")
    val versionOpt = params.get("version")
    val user = verifyUser(params.get("userName"))

    val (ont, _, _) = getOntVersion(uri, versionOpt)

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
   *
   * TODO handle self-uri in delete
   */
  delete("/") {
    val uri = require(params, "uri")
    val versionOpt = params.get("version")
    val user = verifyUser(params.get("userName"))

    val (ont, _, _) = getOntVersion(uri, versionOpt)

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

    versionOpt match {
      case Some(version) => deleteVersion(uri, version, user)
      case None          => deleteOntology(uri, user)
    }
  }

  delete("/!/all") {
    verifyAuthenticatedUser("admin")
    ontDAO.remove(MongoDBObject())
  }

  val versionFormatter = new java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss")

  def getOntVersion(uri: String, versionOpt: Option[String]): (Ontology, OntologyVersion, String) = {
    val ont = ontDAO.findOneById(uri).getOrElse(error(404, s"'$uri' is not registered"))
    versionOpt match {
      case Some(v) =>
        val ontVersion = ont.versions.getOrElse(v, error(404, s"'$uri', version '$v' is not registered"))
        (ont, ontVersion, v)

      case None =>
        // latest version
        getLatestVersion(ont) match {
          case Some((ontVersion, version)) =>
            (ont, ontVersion, version)
          case None =>
            // should not happen
            bug(s"'$uri', no versions registered")
        }
    }
  }

  def resolveUri(uri: String) = {
    val (ont, ontVersion, version) = getOntVersion(uri, params.get("version"))

    // format is the one given, if any, or the one in the db:
    val format = params.get("format").getOrElse(ontVersion.format)

    // todo: determine whether the request is for file contents, or metadata.

    if (format == "!md") {
      val versions = sortedVersionKeys(ont)
      val ores = PendOntologyResult(ont.uri, ontVersion.name, versions)
      grater[PendOntologyResult].toCompactJSON(ores)
    }
    else getOntologyFile(uri, version, format)
  }

  def selfResolve = {
    val uri = request.getRequestURL.toString
    logger.debug(s"self-resolving $uri")
    resolveUri(uri)
  }

  /** latest version first */
  def sortedVersionKeys(ont: Ontology): List[String] =
    ont.versions.keys.toList.sorted(Ordering[String].reverse)

  def getLatestVersion(ont: Ontology): Option[(OntologyVersion,String)] = {
    val versions = sortedVersionKeys(ont)
    versions.headOption match {
      case Some(version) => Some((ont.versions.get(version).get, version))
      case None => None
    }
  }

  /**
   * Verifies the given organization and the userName against that organization.
   */
  def verifyOrgAndUser(orgName: String, userName: String, orgMustExist: Boolean = false): String = {
    orgsDAO.findOneById(orgName) match {
      case None =>
        if (orgMustExist) bug(s"'$orgName' organization must exist")
        else error(400, s"'$orgName' invalid organization")
      case Some(org) =>
        if (org.members.contains(userName)) orgName
        else error(401, s"user '$userName' is not a member of organization '$orgName'")
    }
  }

  /**
   * Verifies the organization and the userName against that organization.
   */
  def verifyOrgAndUser(orgNameOpt: Option[String], userName: String): String = orgNameOpt match {
    case None => missing("orgName")
    case Some(orgName) => verifyOrgAndUser(orgName, userName)
  }

  def validateUri(uri: String) {
    try new URI(uri)
    catch {
      case e: URISyntaxException => error(400, s"invalid URI '$uri': ${e.getMessage}")
    }
  }

  def getRequestOwners = {
    // if owners is given, verify them in general (for either new entry or new version)
    val owners = multiParams("owners").filter(_.trim.length > 0).toSet.toList
    owners foreach verifyUser
    logger.debug(s"owners=$owners")
    owners
  }

  def getFileAndFormat = {
    val fileItem = fileParams.getOrElse("file", missing("file"))

    // todo make format param optional
    val format = require(params, "format")

    logger.info(s"uploaded file=${fileItem.getName} size=${fileItem.getSize} format=$format")
    //val fileContents = new String(fileItem.get(), fileItem.charset.getOrElse("utf8"))
    //val contentType = file.contentType.getOrElse("application/octet-stream")

    (fileItem, format)
  }

  def getVersion = {
    // for now, the version is always automatically assigned
    val now = new java.util.Date()
    val version = versionFormatter.format(now)
    val date    = dateFormatter.format(now)
    (version, date)
  }

  def verifyOwner(userName: String, ont: Ontology) = {
    if (ont.owners.length > 0) {
      if (!ont.owners.contains(userName)) error(401, s"'$userName' is not an owner of '${ont.uri}'")
    }
    else ont.orgName match {
      case Some(orgName) =>
        verifyOrgAndUser(orgName, userName, orgMustExist = true)

      case None => // TODO handle no-organization case
    }

  }

  /**
   * Adds a new version of a registered ontology.
   */
  def addVersion(uri: String, user: db.User) = {
    val nameOpt = params.get("name")
    val owners = getRequestOwners
    val (fileItem, format) = getFileAndFormat
    val (version, date) = getVersion

    var ontVersion = OntologyVersion("", user.userName, format, new DateTime(date))

    ontDAO.findOneById(uri) match {
      case None => error(404, s"'$uri' is not registered")

      case Some(ont) =>
        verifyOwner(user.userName, ont)

        var update = ont

        // if owners explicitly given, use it:
        if (params.contains("owners")) {
          update = update.copy(owners = owners.toSet.toList)
        }

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
  def updateVersion(uri: String, version: String, user: db.User) = {
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
  def deleteVersion(uri: String, version: String, user: db.User) = {
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
  def deleteOntology(uri: String, user: db.User) = {
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

  def writeOntologyFile(uri: String, version: String,
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

  def getOntologyFile(uri: String, version: String, reqFormat: String) = {

    val baseDir = setup.filesConfig.getString("baseDirectory")
    val ontsDir = new File(baseDir, "onts")

    val uriEnc = uri.replace('/', '|')

    val uriDir = new File(ontsDir, uriEnc)

    val versionDir = new File(uriDir, version)

    val format = ontUtil.storedFormat(reqFormat)

    val file = new File(versionDir, s"file.$format")

    if (file.canRead) {
      // already exists, just return it
      contentType = formats(format)
      file
    }
    else try {
      // TODO determine base format for conversions
      val fromFile = new File(versionDir, "file.rdf")
      ontUtil.convert(uri, fromFile, fromFormat = "rdf", file, toFormat = format) match {
        case Some(resFile) =>
          contentType = formats(format)
          resFile
        case _ =>
          error(406, s"Format '$format' not available for uri='$uri' version='$version'")
           // TODO include accepted formats
      }
    }
    catch {
      case exc: Exception => // likely com.hp.hpl.jena.shared.NoWriterForLangException
        val exm = s"${exc.getClass.getName}: ${exc.getMessage}"
        error(500, s"cannot create format '$format' for uri='$uri' version='$version': $exm")
    }
  }

}
