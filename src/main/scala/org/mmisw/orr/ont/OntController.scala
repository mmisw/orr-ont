package org.mmisw.orr.ont

import com.mongodb.casbah.Imports._
import com.typesafe.scalalogging.slf4j.Logging

import org.scalatra.servlet.{FileItem, SizeConstraintExceededException, FileUploadSupport}
import org.scalatra.FlashMapSupport
import javax.servlet.annotation.MultipartConfig
import java.io.File
import java.net.{URI, URISyntaxException}
import org.mmisw.orr.ont.db.{OntologyVersion, Ontology}
import org.joda.time.DateTime
import scala.util.{Failure, Success, Try}
import com.novus.salat._
import com.novus.salat.global._


@MultipartConfig(maxFileSize = 5*1024*1024)
class OntController(implicit setup: Setup) extends OrrOntStack
      with FileUploadSupport with FlashMapSupport
      with SimpleMongoDbJsonConversion with Logging {

  //configureMultipartHandling(MultipartConfig(maxFileSize = Some(5 * 1024 * 1024)))

  error {
    case e: SizeConstraintExceededException =>
      error(413, "The file you uploaded exceeded the 5MB limit.")
  }

  val authoritiesDAO = setup.db.authoritiesDAO
  val usersDAO       = setup.db.usersDAO
  val ontDAO         = setup.db.ontDAO

  val versionFormatter = new java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss")


  def getOntVersion(uri: String, versionOpt: Option[String]) = {
    val ont = ontDAO.findOneById(uri).getOrElse(error(404, s"'$uri' is not registered"))
    versionOpt match {
      case Some(v) =>
        val ov = ont.versions.getOrElse(v, error(404, s"'$uri', version '$v' is not registered"))
        (ov, v)

      case None =>
        val ov = ont.versions.getOrElse(ont.latestVersion,
          bug(s"'$uri', version '${ont.latestVersion}' is not registered"))
        (ov, ont.latestVersion)
    }
  }

  def resolveUri(uri: String) = {
    val (ontVersion, version) = getOntVersion(uri, params.get("version"))

    // format is the one given, if any, or the one in the db:
    val format = params.get("format").getOrElse(ontVersion.format)

    // todo: determine whether the request is for file contents, or metadata.

    // assume file contents while we test this part
    getOntologyFile(uri, version, format)
  }

  /**
   * http localhost:8080/ont/\?uri=http://mmisw.org/ont/mmi/device\&format=rdf
   */
  get("/") {
    params.get("uri") match {
      case Some(uri) => resolveUri(uri)

      case None =>
        // TODO what exactly to report?
        ontDAO.find(MongoDBObject()) map { ont =>
          val name = ont.versions.get(ont.latestVersion).get.name
          val ores = PendOntologyResult(ont.uri, name, ont.latestVersion, ont.versions.keys.toList)
          grater[PendOntologyResult].toCompactJSON(ores)
        }

    }
  }

  def verifyUser(userNameOpt: Option[String]): String = userNameOpt match {
    case None => missing("userName")
    case Some(userName) =>
      if (setup.testing) userName
      else {
        usersDAO.findOneById(userName) match {
          case None => error(400, s"'$userName' invalid user")
          case _ => userName
        }
      }
  }

  /**
   * Verifies the given authority and the userName against that authority.
   */
  def verifyAuthorityAndUser(authName: String, userName: String, authorityMustExist: Boolean = false): String = {
    if (setup.testing) authName
    else {
      authoritiesDAO.findOneById(authName) match {
        case None => 
          if (authorityMustExist) bug(s"'$authName' authority must exist")
          else error(400, s"'$authName' invalid authority")
        case Some(authority) =>
          if (authority.members.contains(userName)) authName
          else error(401, s"user '$userName' is not a member of authority '$authName'")
      }
    }
  }

  /**
   * Verifies the authority and the userName against that authority.
   */
  def verifyAuthorityAndUser(authNameOpt: Option[String], userName: String): String = authNameOpt match {
    case None => missing("authName")
    case Some(authName) => verifyAuthorityAndUser(authName, userName)
  }

  def validateUri(uri: String) {
    try new URI(uri)
    catch {
      case e: URISyntaxException => error(400, s"invalid URI '$uri': ${e.getMessage}")
    }
  }

  def getOwners = {
    // if owners is given, verify them in general (for either new entry or new version)
    val owners = multiParams("owners").filter(_.trim.length > 0).toSet.toList
    owners foreach (userName => verifyUser(Some(userName)))
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

  /**
   * posts a new ontology entry.
   *
   * http -f post localhost:8080/ont uri=http://ont1 name=example authName=mmi userName=carueda file@src/test/resources/test.rdf format=rdf
   */
  post("/") {
    val uri = require(params, "uri")
    val name = require(params, "name")
    val authNameOpt = params.get("authName")
    val userName = verifyUser(params.get("userName"))

    // TODO handle case where there is no explicit authority to verify
    // the user can submit on her own behalf.
    val authName = verifyAuthorityAndUser(authNameOpt, userName)

    val owners = getOwners
    val (fileItem, format) = getFileAndFormat
    val (version, date) = getVersion

    ontDAO.findOneById(uri) match {
      case None =>
        validateUri(uri)

        writeOntologyFile(uri, version, fileItem, format)

        val ontVersion = OntologyVersion(name, userName, format, new DateTime(date))
        val ont = Ontology(uri, version, Some(authName),
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

  def verifyOwner(userName: String, ont: Ontology) = {
    if (ont.owners.length > 0) {
      if (!ont.owners.contains(userName)) error(401, s"'$userName' is not an owner of '${ont.uri}'")
    }
    else ont.authName match {
      case Some(authName) =>
        verifyAuthorityAndUser(authName, userName, authorityMustExist = true)

      case None => // TODO handle no-authority case
    }

  }
  /**
   * posts a new version of an existing ontology entry.
   *
   * http -f post localhost:8080/ont/version uri=http://ont1 userName=carueda file@src/test/resources/test.rdf format=rdf
   */
  post("/version") {
    val uri = require(params, "uri")
    val nameOpt = params.get("name")
    val userName = verifyUser(params.get("userName"))

    val owners = getOwners
    val (fileItem, format) = getFileAndFormat
    val (version, date) = getVersion

    var ontVersion = OntologyVersion("", userName, format, new DateTime(date))

    ontDAO.findOneById(uri) match {
      case None => error(404, s"'$uri' is not registered")

      case Some(ont) =>
        verifyOwner(userName, ont)

        var update = ont

        // if owners explicitly given, use it:
        if (params.contains("owners")) {
          update = update.copy(owners = owners.toSet.toList)
        }

        nameOpt foreach (name => ontVersion = ontVersion.copy(name = name))
        update = update.copy(latestVersion = version,
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
   * Note, only the name in the particular version can be updated.
   */
  put("/version") {
    acceptOnly("uri", "version", "userName", "name")
    val uri      = require(params, "uri")
    val version  = require(params, "version")
    val userName = verifyUser(params.get("userName"))
    val name     = require(params, "name")

    ontDAO.findOneById(uri) match {
      case None => error(404, s"'$uri' is not registered")

      case Some(ont) =>
        verifyOwner(userName, ont)

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

  // deletes a particular version
  delete("/version") {
    acceptOnly("uri", "version", "userName")
    val uri      = require(params, "uri")
    val version  = require(params, "version")
    val userName = verifyUser(params.get("userName"))

    ontDAO.findOneById(uri) match {
      case None => error(404, s"'$uri' is not registered")

      case Some(ont) =>
        verifyOwner(userName, ont)

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

  // deletes a complete entry
  delete("/") {
    acceptOnly("uri", "userName")
    val uri      = require(params, "uri")
    val userName = verifyUser(params.get("userName"))

    ontDAO.findOneById(uri) match {
      case None => error(404, s"'$uri' is not registered")

      case Some(ont) =>
        verifyOwner(userName, ont)

        Try(ontDAO.remove(ont, WriteConcern.Safe)) match {
          case Success(result) =>
            OntologyResult(uri, removed = Some(DateTime.now())) //TODO

          case Failure(exc)  => error(500, s"update failure = $exc")
        }
    }
  }

  post("/!/deleteAll") {
    val map = body()
    val pw = require(map, "pw")
    val special = setup.mongoConfig.getString("pw_special")
    if (special == pw) ontDAO.remove(MongoDBObject()) else halt(401)
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

  def getOntologyFile(uri: String, version: String, format: String) = {

    val baseDir = setup.filesConfig.getString("baseDirectory")
    val ontsDir = new File(baseDir, "onts")

    val uriEnc = uri.replace('/', '|')

    val uriDir = new File(ontsDir, uriEnc)

    val versionDir = new File(uriDir, version)

    val filename = s"file.$format"

    val file = new File(versionDir, filename)

    if (file.canRead) {
      contentType = formats(format)
      file
    }
    else error(406, s"Format '$format' not available for uri='$uri' version='$version'")
         // TODO include accepted formats
  }

}
