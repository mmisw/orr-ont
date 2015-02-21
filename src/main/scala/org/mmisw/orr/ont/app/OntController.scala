package org.mmisw.orr.ont.app

import java.io.File
import javax.servlet.annotation.MultipartConfig

import com.mongodb.casbah.Imports._
import com.novus.salat._
import com.novus.salat.global._
import com.typesafe.scalalogging.slf4j.Logging
import org.mmisw.orr.ont.{OntologySummaryResult, db, PendOntologyResult, Setup}
import org.mmisw.orr.ont.service._
import org.scalatra.Created
import org.scalatra.servlet.{FileItem, FileUploadSupport, SizeConstraintExceededException}

import scala.util.{Failure, Success, Try}


/**
 * Controller for the "ont" API.
 */
@MultipartConfig(maxFileSize = 5*1024*1024)
class OntController(implicit setup: Setup, ontService: OntService) extends BaseOntController
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
        ontService.getOntologies(query) map grater[OntologySummaryResult].toCompactJSON
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
        verifyAuthenticatedUser(org.members + "admin")
    }

    // TODO capture version_status from parameter
    val version_status: Option[String] = None

    // TODO capture contact_name (from parameter, or by parsing ontology metadata)
    val contact_name: Option[String] = None

    // ok, go ahead with registration
    val contents = getContentsAndFormat
    val (version, date) = getVersion

    Created(createOntology(uri, name, version, version_status, contact_name, date, contents, orgName))
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
            verifyAuthenticatedUser(org.members + "admin")
        }

      case None =>
        bug(s"currently I expect registered ont to have org associated")
    }

    // ok, authenticated user can PUT.

    versionOpt match {
      case Some(version) => updateOntologyVersion(uri, version, user)
      case None          => createOntologyVersion(uri, user)
    }
  }

  /*
   * Deletes a particular version or the whole ontology entry.
   */
  delete("/") {
    val uri = require(params, "uri")
    val versionOpt = params.get("version")
    val user = verifyUser(params.get("userName"))

    val (ont, _, _) = resolveOntology(uri, versionOpt)

    ont.orgName match {
      case Some(orgName) =>
        orgsDAO.findOneById(orgName) match {
          case None => bug(s"org '$orgName' should exist")

          case Some(org) => verifyAuthenticatedUser(org.members + "admin")
        }

      case None =>
        bug(s"currently I expect registered ont to have org associated")
    }

    versionOpt match {
      case Some(version) => deleteOntologyVersion(uri, version, user)
      case None          => deleteOntology(uri, user)
    }
  }

  delete("/!/all") {
    verifyAuthenticatedUser("admin")
    ontService.deleteAll()
  }

  ///////////////////////////////////////////////////////////////////////////

  private case class FileWriter(format: String, fileItem: FileItem) extends AnyRef with OntFileWriter {
    override def write(destFile: File) { fileItem.write(destFile)}
  }

  private def createOntology(uri: String, name: String, version: String, version_status: Option[String],
                             contact_name: Option[String],
                             date: String,
                             ontFileWriter: OntFileWriter, orgName: String) = {

    Try(ontService.createOntology(uri, name, version, version_status,
          contact_name, date, user.userName, orgName, ontFileWriter)) match {
      case Success(ontologyResult) => ontologyResult

      case Failure(exc: InvalidUri) => error(400, exc.details)
      case Failure(exc: OntologyAlreadyRegistered) => error(409, exc.details)
      case Failure(exc: Problem) => error(500, exc.details)
      case Failure(exc) => error(500, exc.getMessage)
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

  private def getContentsAndFormat = {
    val fileItem = fileParams.getOrElse("file", missing("file"))

    // todo make format param optional
    val format = require(params, "format")

    logger.info(s"uploaded file=${fileItem.getName} size=${fileItem.getSize} format=$format")
    //val fileContents = new String(fileItem.get(), fileItem.charset.getOrElse("utf8"))
    //val contentType = file.contentType.getOrElse("application/octet-stream")

    FileWriter(format, fileItem)
  }

  private def getVersion = {
    // for now, the version is always automatically assigned
    val now = new java.util.Date()
    val version = versionFormatter.format(now)
    val date = dateFormatter.format(now)
    (version, date)
  }

  /**
   * Adds a new version of a registered ontology.
   */
  private def createOntologyVersion(uri: String, user: db.User) = {
    val nameOpt = params.get("name")
    val contents = getContentsAndFormat
    val (version, date) = getVersion

    Try(ontService.createOntologyVersion(uri, nameOpt, user.userName, version, date, contents)) match {
      case Success(ontologyResult) => ontologyResult

      case Failure(exc: NoSuch)                       => error(404, exc.details)
      case Failure(exc: NotAMember)                   => error(401, exc.details)
      case Failure(exc: CannotInsertOntologyVersion)  => error(500, exc.details)
      case Failure(exc)                               => error(500, exc.getMessage)
    }
  }

  /**
   * updates a particular version.
   * Note, only the name in the particular version can be updated at the moment.
   */
  // TODO handle other pieces that can/should be updated
  private def updateOntologyVersion(uri: String, version: String, user: db.User) = {
    val name = require(params, "name")

    Try(ontService.updateOntologyVersion(uri, version, name, user.userName)) match {
      case Success(ontologyResult) => ontologyResult

      case Failure(exc: NoSuch)                       => error(404, exc.details)
      case Failure(exc: NotAMember)                   => error(401, exc.details)
      case Failure(exc: CannotUpdateOntologyVersion)  => error(500, exc.details)
      case Failure(exc)                               => error(500, exc.getMessage)
    }
  }

  /**
   * Deletes a particular version.
   */
  private def deleteOntologyVersion(uri: String, version: String, user: db.User) = {

    Try(ontService.deleteOntologyVersion(uri, version,  user.userName)) match {
      case Success(ontologyResult) => ontologyResult

      case Failure(exc: NoSuch)                       => error(404, exc.details)
      case Failure(exc: NotAMember)                   => error(401, exc.details)
      case Failure(exc: CannotDeleteOntologyVersion)  => error(500, exc.details)
      case Failure(exc)                               => error(500, exc.getMessage)
    }
  }

  /**
   * Deletes a whole ontology entry.
   */
  private def deleteOntology(uri: String, user: db.User) = {
    Try(ontService.deleteOntology(uri, user.userName)) match {
      case Success(ontologyResult) => ontologyResult

      case Failure(exc: NoSuch)                       => error(404, exc.details)
      case Failure(exc: NotAMember)                   => error(401, exc.details)
      case Failure(exc: CannotDeleteOntology)         => error(500, exc.details)
      case Failure(exc)                               => error(500, exc.getMessage)
    }
  }
}
