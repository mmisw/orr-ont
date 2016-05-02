package org.mmisw.orr.ont.app

import java.io.File
import javax.servlet.annotation.MultipartConfig

import com.mongodb.casbah.Imports._
import com.novus.salat._
import com.novus.salat.global._
import com.typesafe.scalalogging.{StrictLogging => Logging}
import org.mmisw.orr.ont._
import org.mmisw.orr.ont.service._
import org.scalatra.Created
import org.scalatra.servlet.{FileItem, FileUploadSupport, SizeConstraintExceededException}

import scala.util.{Failure, Success, Try}


/**
 * Controller for the "ont" API.
 */
@MultipartConfig(maxFileSize = 10*1024*1024)
class OntController(implicit setup: Setup,
                    ontService: OntService,
                    tsService: TripleStoreService
                   ) extends BaseOntController
      with FileUploadSupport with Logging {

  //configureMultipartHandling(MultipartConfig(maxFileSize = Some(5 * 1024 * 1024)))

  error {
    case e: SizeConstraintExceededException =>
      error(413, "The file you uploaded exceeds the 10MB limit.")
  }

  /*
   * General ontology request
   */
  get("/") {
    params.get("uri") match {
      case Some(uri) => resolveUri(uri)
      case None =>
        val query = getQueryFromParams(params.keySet) - "jwt"  // - sigParamName)
        val onts = ontService.getOntologies(query, checkIsAdminOrExtra)
        onts map { osr =>
          grater[OntologySummaryResult].asDBObject(osr)//.toCompactJSON
        }
    }
  }

  /*
   * Uploads an ontology file.
   */
  post("/upload") {
    val u = authenticatedUser.getOrElse(halt(401, s"unauthorized"))
    try {
      val ontFileWriter = getOntFileWriterForJustUploadedFile
      val uploadedFileInfo = ontService.saveUploadedOntologyFile(u.userName, ontFileWriter)
      uploadedFileInfo
    }
    catch { case e: Throwable =>
      e.printStackTrace()
      throw e
    }
  }

  /*
   * Registers a new ontology entry.
   */
  post("/") {
    val uri            = require(params, "uri")
    val originalUriOpt = params.get("originalUri")  // for fully-hosted mode
    val name           = require(params, "name")
    val orgName        = require(params, "orgName")
    val user           = verifyUser(params.get("userName"))

    // TODO allow absent orgName so user can submit on her own behalf?

    orgsDAO.findOneById(orgName) match {
      case None =>
        error(400, s"'$orgName' invalid organization")

      case Some(org) =>
        verifyIsUserOrAdminOrExtra(org.members)
    }

    // TODO capture version_status from parameter
    val version_status: Option[String] = None

    // TODO capture contact_name (from parameter, or by parsing ontology metadata)
    val contact_name: Option[String] = None

    val (version, date) = getVersion

    val ontFileWriter = getOntFileWriter(user)

    Created(createOntology(uri, originalUriOpt, name, version,
      version_status, contact_name, date, ontFileWriter, orgName))
  }

  /*
   * Updates a given version or adds a new version.
   */
  put("/") {
    val uri            = require(params, "uri")
    val originalUriOpt = params.get("originalUri")  // for fully-hosted mode
    val versionOpt     = params.get("version")
    val user           = verifyUser(params.get("userName"))

    val (ont, _, _) = resolveOntology(uri, versionOpt)

    ont.orgName match {
      case Some(orgName) =>
        orgsDAO.findOneById(orgName) match {
          case None =>
            bug(s"org '$orgName' should exist")

          case Some(org) =>
            verifyIsAuthenticatedUser(org.members + "admin")
        }

      case None =>
        bug(s"currently I expect registered ont to have org associated")
    }

    val ontFileWriter = getOntFileWriter(user)

    versionOpt match {
      case Some(version) => updateOntologyVersion(uri, originalUriOpt, version, user)
      case None          => createOntologyVersion(uri, originalUriOpt, user, ontFileWriter)
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

          case Some(org) => verifyIsAuthenticatedUser(org.members + "admin")
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
    verifyIsAdminOrExtra()
    ontService.deleteAll()
  }

  ///////////////////////////////////////////////////////////////////////////

  private case class FileItemWriter(format: String, fileItem: FileItem) extends AnyRef with OntFileWriter {
    override def write(destFile: File) {
      fileItem.write(destFile)
    }
  }

  private case class FileWriter(format: String, file: File) extends AnyRef with OntFileWriter {
    override def write(destFile: File) {
      java.nio.file.Files.copy(
        java.nio.file.Paths.get(file.getPath),
        java.nio.file.Paths.get(destFile.getPath),
        java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }
  }

  private def createOntology(uri:             String,
                             originalUriOpt:  Option[String],
                             name:            String,
                             version:         String,
                             version_status:  Option[String],
                             contact_name:    Option[String],
                             date:            String,
                             ontFileWriter:   OntFileWriter,
                             orgName:         String
                            ) = {
    val user = requireAuthenticatedUser
    logger.debug(s"""
         |createOntology:
         | user:           $user
         | uri:            $uri
         | originalUri:    $originalUriOpt
         | name:           $name
         | version:        $version
         | version_status: $version_status
         | contact_name:   $contact_name
         | date:           $date
         | orgName:        $orgName
         | ontFileWriter.format: ${ontFileWriter.format}
         |""".stripMargin)

    Try(ontService.createOntology(uri, originalUriOpt, name, version, version_status,
          contact_name, date, user.userName, orgName, ontFileWriter)) match {
      case Success(ontologyResult) =>
        loadOntologyInTripleStore(uri, reload = false)
        ontologyResult

      case Failure(exc: InvalidUri) => error(400, exc.details)
      case Failure(exc: OntologyAlreadyRegistered) => error(409, exc.details)

      case Failure(exc: Problem) => error500(exc)
      case Failure(exc)          => error500(exc)
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
    val versionOpt: Option[String] = params.get("version")
    val (ont, ontVersion, version) = resolveOntology(uri, versionOpt)

    // format is the one given if any, or the one in the db:
    val reqFormat = params.get("format").getOrElse(ontVersion.format)

    // todo: determine mechanism to request for metadata:  format=!md is preliminary

    if (reqFormat == "!md") {
      // include 'versions' if no particular version requested.
      val versionsOpt = versionOpt match {
        case None    => Some(ont.sortedVersionKeys)
        case Some(_) => None
      }
      val ores = ontService.getOntologySummaryResult(ont, ontVersion, version,
        privileged = checkIsAdminOrExtra,
        includeMetadata = true,
        versionsOpt)
      grater[OntologySummaryResult].toCompactJSON(ores)
    }
    else {
      val (file, actualFormat) = getOntologyFile(uri, version, reqFormat)
      contentType = formats(actualFormat)
      file
    }
  }

  /** get OntFileWriter according to given relevant parameters */
  private def getOntFileWriter(user: db.User): OntFileWriter = {
    if (fileParams.isDefinedAt("file"))
      getOntFileWriterForJustUploadedFile
    else
      getOntFileWriterForPreviouslyUploadedFile(user.userName)
  }

  private def getOntFileWriterForJustUploadedFile: OntFileWriter = {
    val fileItem = fileParams.getOrElse("file", missing("file"))

    // todo make format param optional
    val format = require(params, "format")

    logger.info(s"uploaded file=${fileItem.getName} size=${fileItem.getSize} format=$format")
    //val fileContents = new String(fileItem.get(), fileItem.charset.getOrElse("utf8"))
    //val contentType = file.contentType.getOrElse("application/octet-stream")

    FileItemWriter(format, fileItem)
  }

  private def getOntFileWriterForPreviouslyUploadedFile(userName: String): OntFileWriter = {
    val filename = require(params, "uploadedFilename")
    val format   = require(params, "uploadedFormat")

    logger.info(s"getOntFileWriterForPreviouslyUploadedFile: filename=$filename format=$format")

    val file = ontService.getUploadedFile(userName, filename)
    FileWriter(format, file)
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
  private def createOntologyVersion(uri:            String,
                                    originalUriOpt: Option[String],
                                    user:           db.User,
                                    ontFileWriter:  OntFileWriter
                                   ) = {
    val nameOpt = params.get("name")
    val (version, date) = getVersion

    // TODO capture version_status from parameter
    val version_status: Option[String] = None

    // TODO capture contact_name (from parameter, or by parsing ontology metadata)
    val contact_name: Option[String] = None

    Try(ontService.createOntologyVersion(uri, originalUriOpt, nameOpt, user.userName, version,
            version_status, contact_name, date, ontFileWriter)) match {
      case Success(ontologyResult) =>
        loadOntologyInTripleStore(uri, reload = true)
        ontologyResult

      case Failure(exc: NoSuch)                       => error(404, exc.details)
      case Failure(exc: NotAMember)                   => error(401, exc.details)
      case Failure(exc: CannotInsertOntologyVersion)  => error500(exc)
      case Failure(exc)                               => error500(exc)
    }
  }

  /**
   * updates a particular version.
   * Note, only the name in the particular version can be updated at the moment.
   */
  // TODO handle other pieces that can/should be updated
  private def updateOntologyVersion(uri:            String,
                                    originalUriOpt: Option[String],
                                    version:        String,
                                    user:           db.User
                                   ) = {
    val name = require(params, "name")

    Try(ontService.updateOntologyVersion(uri, originalUriOpt, version, name, user.userName)) match {
      case Success(ontologyResult) =>
        loadOntologyInTripleStore(uri, reload = true)
        ontologyResult

      case Failure(exc: NoSuch)                       => error(404, exc.details)
      case Failure(exc: NotAMember)                   => error(401, exc.details)
      case Failure(exc: CannotUpdateOntologyVersion)  => error500(exc)
      case Failure(exc)                               => error500(exc)
    }
  }

  private def loadOntologyInTripleStore(uri: String, reload: Boolean): Unit = {
    if (setup.testing.isEmpty) {
      val re = if (reload) "re" else ""
      tsService.loadUriFromLocal(uri, reload) match {
        case Right(content)  => logger.info(s"${re}loaded ontology uri=$uri in triple store $content")
        case Left(exc)       => logger.warn(s"could not ${re}load ontology in triple store", exc)
      }
    }
    else logger.warn("loadOntologyInTripleStore: under testing mode so nothing done here")
  }

  /**
   * Deletes a particular version.
   */
  private def deleteOntologyVersion(uri: String, version: String, user: db.User) = {

    Try(ontService.deleteOntologyVersion(uri, version,  user.userName)) match {
      case Success(ontologyResult) => ontologyResult

      case Failure(exc: NoSuch)                       => error(404, exc.details)
      case Failure(exc: NotAMember)                   => error(401, exc.details)
      case Failure(exc: CannotDeleteOntologyVersion)  => error500(exc)
      case Failure(exc)                               => error500(exc)
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
      case Failure(exc: CannotDeleteOntology)         => error500(exc)
      case Failure(exc)                               => error500(exc)
    }
  }
}
