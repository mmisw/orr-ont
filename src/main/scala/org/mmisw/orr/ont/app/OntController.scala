package org.mmisw.orr.ont.app

import java.io.File
import javax.servlet.annotation.MultipartConfig

import com.mongodb.casbah.Imports._
import com.novus.salat._
import com.novus.salat.global._
import com.typesafe.scalalogging.{StrictLogging => Logging}
import org.json4s._
import org.json4s.native.JsonMethods._
import org.mmisw.orr.ont._
import org.mmisw.orr.ont.db.{OntVisibility, Ontology, OntologyVersion}
import org.mmisw.orr.ont.service._
import org.mmisw.orr.ont.swld.{M2RModel, V2RModel, ontUtil}
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
   * General ontology or term request
   */
  get("/") {
    params.get("uri") match {
      case Some(uri) => resolveOntOrTermUri(uri)

      case None => params.get("ouri") match {
        case Some(uri) => resolveOntUri(uri)

        case None => params.get("turi") match {
          case Some(uri) => resolveTermUri(uri)

          case None => resolveOnts()
        }
      }
    }
  }

  get("/sbjs") {
    val uri = require(params, "uri")
    getSubjects(uri)
  }

  get("/sbjs/external") {
    val uri = require(params, "uri")
    getSubjectsExternal(uri)
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
    val uri            = requireParam("uri")
    val originalUriOpt = getParam("originalUri")  // for fully-hosted mode
    val name           = requireParam("name")
    val orgNameOpt     = getParam("orgName")
    val user           = verifyUser(getParam("userName"))

    val ownerName = orgNameOpt match {
      case Some(orgName) => verifyOrgName(orgName)
      case None          => "~" + user.userName
    }

    // visibility will be "owner" if not explicitly given
    val versionVisibility = OntVisibility.withName(
      getParam("visibility").getOrElse("owner").toLowerCase)

    // TODO capture version_status from parameter
    val version_status: Option[String] = None

    val (version, date) = getVersion

    val ontFileWriter = getOntFileWriter(user)

    Created(createOntology(uri, originalUriOpt, name, version,
      versionVisibility, version_status,
      date, ontFileWriter, ownerName))
  }

  /*
   * Updates a given version or adds a new version.
   */
  put("/") {
    val uri            = requireParam("uri")
    val originalUriOpt = getParam("originalUri")  // for fully-hosted mode
    val versionOpt     = getParam("version")
    val visibilityOpt  = getParam("visibility")
    val user           = verifyUser(getParam("userName"))

    val (ont, _, _) = resolveOntologyVersion(uri, versionOpt)

    verifyOwnerName(ont.ownerName)

    val ontFileWriterOpt: Option[OntFileWriter] = getOntFileWriterOpt(user) orElse {
      getParam("metadata") map (getOntFileWriterWithMetadata(uri, versionOpt, _))
    }

    val doVerifyOwner = false  // already verified above

    versionOpt match {
      case None =>
        val ontFileWriter = ontFileWriterOpt.getOrElse(
          error(400, "creation of new version requires specification of contents " +
            "(file upload, embedded contents, or new metadata"))

        createOntologyVersion(uri, originalUriOpt, user,
          versionVisibility = OntVisibility.withName(visibilityOpt.getOrElse("owner").toLowerCase),
          nameOpt = getParam("name"),
          ontFileWriter, doVerifyOwner)

      case Some(version) =>
        updateOntologyVersion(uri, originalUriOpt, version,
          versionVisibilityOpt = visibilityOpt map OntVisibility.withName,
          nameOpt = getParam("name"),
          user, doVerifyOwner)
    }
  }

  private def visibilityFilter(userOrgNames: List[String], osr: OntologySummaryResult): Boolean = {
    val vis = osr.visibility.getOrElse(OntVisibility.owner)
    vis match {
      case OntVisibility.public => true
      case OntVisibility.user   => authenticatedUser.isDefined
      case OntVisibility.owner  =>
        if (authenticatedUser.isEmpty) false
        else osr.ownerName match {
          case Some(ownerName) =>
            OntOwner(ownerName) match {
              case UserOntOwner(ownerUserName) => ownerUserName == authenticatedUser.get.userName
              case OrgOntOwner(orgName)        => userOrgNames.contains(orgName)
            }

          case None => false
        }
    }
  }

  private def verifyOwnerName(ownerName: String): Unit = {
    OntOwner(ownerName) match {
      case OrgOntOwner(orgName)   => verifyOrgName(orgName)
      case UserOntOwner(userName) => verifyIsUserOrAdminOrExtra(Set(userName))
    }
  }

  private def verifyOrgName(orgName: String): String = {
    orgsDAO.findOneById(orgName) match {
      case Some(org) =>
        verifyIsUserOrAdminOrExtra(org.members)
        orgName

      case None => bug(s"org '$orgName' should exist")
    }
  }

  /*
   * Deletes a particular version or the whole ontology entry.
   */
  delete("/") {
    val uri = require(params, "uri")
    val versionOpt = params.get("version")
    val user = verifyUser(params.get("userName"))

    val (ont, _, _) = resolveOntologyVersion(uri, versionOpt)

    verifyOwnerName(ont.ownerName)

    val doVerifyOwner = false  // already verified above

    versionOpt match {
      case Some(version) => deleteOntologyVersion(uri, version, user, doVerifyOwner)
      case None          => deleteOntology(uri, user, doVerifyOwner)
    }
  }

  delete("/!/all") {
    verifyIsAdminOrExtra()
    ontService.deleteAll()
  }

  ///////////////////////////////////////////////////////////////////////////

  // used for just uploaded file
  private case class FileItemWriter(format: String, fileItem: FileItem) extends AnyRef with OntFileWriter {
    override def write(destFile: File) {
      fileItem.write(destFile)
    }
  }

  // used for previously uploaded file
  private case class FileWriter(format: String, file: File) extends AnyRef with OntFileWriter {
    override def write(destFile: File) {
      java.nio.file.Files.copy(
        java.nio.file.Paths.get(file.getPath),
        java.nio.file.Paths.get(destFile.getPath),
        java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }
  }

  // used for embedded contents
  private case class StringWriter(format: String, contents: String) extends AnyRef with OntFileWriter {
    override def write(destFile: File) {
      java.nio.file.Files.write(destFile.toPath,
        contents.getBytes(java.nio.charset.StandardCharsets.UTF_8))
    }
  }

  // used to create new version based on another version where all metadata gets replaced
  private case class UpdateWithMetadataWriter(format:       String,
                                              ont:          Ontology,
                                              ontVersion:   OntologyVersion,
                                              version:      String,
                                              newMetadata:  Map[String, JValue]
                                             ) extends AnyRef with OntFileWriter {
    override def write(destFile: File): Unit = {
      val uri = ont.uri

      val (file, actualFormat) = getOntologyFile(uri, version, format)
      if (actualFormat == "v2r") {
        val oldV2r = parse(file).extract[V2RModel]
        val newV2r = oldV2r.copy(metadata = Some(newMetadata))
        java.nio.file.Files.write(destFile.toPath,
          newV2r.toPrettyJson.getBytes(java.nio.charset.StandardCharsets.UTF_8))
      }
      else if (actualFormat == "m2r") {
        val oldM2r = parse(file).extract[M2RModel]
        val newM2r = oldM2r.copy(metadata = Some(newMetadata))
        java.nio.file.Files.write(destFile.toPath,
          newM2r.toPrettyJson.getBytes(java.nio.charset.StandardCharsets.UTF_8))
      }
      else {
        val ontModel = ontUtil.loadOntModel(uri, file, actualFormat)
        ontUtil.replaceMetadata(uri, ontModel, newMetadata)
        ontUtil.writeModel(uri, ontModel, actualFormat, destFile)
      }
    }
  }

  private def createOntology(uri:             String,
                             originalUriOpt:  Option[String],
                             name:            String,
                             version:         String,
                             versionVisibility: OntVisibility.Value,
                             version_status:  Option[String],
                             date:            String,
                             ontFileWriter:   OntFileWriter,
                             ownerName:       String
                            ) = {
    val user = requireAuthenticatedUser
    logger.debug(s"""
         |createOntology:
         | user:           $user
         | uri:            $uri
         | originalUri:    $originalUriOpt
         | name:           $name
         | version:        $version
         | versionVisibility: $versionVisibility
         | version_status: $version_status
         | date:           $date
         | ownerName:      $ownerName
         | ontFileWriter.format: ${ontFileWriter.format}
         |""".stripMargin)

    Try(ontService.createOntology(uri, originalUriOpt, name, version,
          versionVisibility = versionVisibility,
          version_status = version_status,
          date = date,
          userName = user.userName,
          ownerName = ownerName,
          ontFileWriter = ontFileWriter
    )) match {
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
    *
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
    if (keys.nonEmpty) {
      keys foreach (key => query = query.updated(key, params.get(key).get))
      logger.debug(s"GET query=$query")
    }
    query
  }

  private val versionFormatter = new java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss")

  private def resolveOnts() = {
    val query = getQueryFromParams(params.keySet) - "jwt"  // - sigParamName)
    val privileged = checkIsAdminOrExtra
    val ontologies = ontService.getOntologies(query, privileged).toList
    val resultOntologies = if (privileged) ontologies
    else {
      val userOrgNames: List[String] = authenticatedUser match {
        case Some(u) => orgService.getUserOrganizationNames(u.userName)
        case None => Nil
      }
      ontologies filter (visibilityFilter(userOrgNames, _))
    }
    resultOntologies map { osr =>
      grater[OntologySummaryResult].asDBObject(osr)//.toCompactJSON
    }
  }

  private def resolveOntOrTermUri(uri: String) = {
    ontService.resolveOntology(uri) match {
      case Some(ont) => completeOntologyUriResolution(ont)
      case None      => resolveTermUri(uri)
    }
  }

  private def resolveOntUri(uri: String) = {
    ontService.resolveOntology(uri) match {
      case Some(ont) => completeOntologyUriResolution(ont)
      case None      => error(404, s"'$uri': No such ontology")
    }
  }

  private def completeOntologyUriResolution(ont: Ontology) = {
    val versionOpt: Option[String] = params.get("version")
    val (ontVersion, version) = resolveOntologyVersion(ont, versionOpt)

    // format is the one given if any, or the one in the db:
    val reqFormat = params.get("format").getOrElse(ontVersion.format)

    // format=!md is our mechanism to request for metadata

    if (reqFormat == "!md") {
      // include 'versions' even when a particular version is requested
      val versionsOpt = Some(ont.sortedVersionKeys)
      val ores = ontService.getOntologySummaryResult(ont, ontVersion, version,
        privileged = checkIsAdminOrExtra,
        includeMetadata = true,
        versionsOpt)
      grater[OntologySummaryResult].toCompactJSON(ores)
    }
    else {
      val (file, actualFormat) = getOntologyFile(ont.uri, version, reqFormat)
      contentType = formats(actualFormat)
      file
    }
  }

  private def resolveTermUri(uri: String): String = {
    val formatOpt = params.get("format")
    tsService.resolveTermUri(uri, formatOpt, acceptHeader) match {
      case Right(TermResponse(result, resultContentType)) =>
        contentType = resultContentType
        result

      case Left(exc) =>
        exc match {
          case NoSuchTermFormat(_, format) => error(406, s"invalid format=$format")
          case CannotQueryTerm(_, msg)     => error(400, s"error querying uri=$uri: $msg")
          case _                           => error500(exc)
        }
    }
  }

  private def getSubjects(uri: String) = {
    logger.debug(s"getSubjects: uri=$uri")
    val (ont, ontVersion, version) = resolveOntologyVersion(uri)
    val ores = ontService.getOntologySubjects(ont, ontVersion, version, includeMetadata = true)
    grater[OntologySubjectsResult].toCompactJSON(ores)
  }

  private def getSubjectsExternal(uri: String) = {
    logger.debug(s"getSubjectsExternal: uri=$uri")
    ontService.getExternalOntologySubjects(uri) match {
      case Success(ores) =>
        grater[ExternalOntologySubjectsResult].toCompactJSON(ores)

      case Failure(t) =>
        error(400, CannotLoadExternalOntology(uri, t).details)
    }
  }

  /**
    * Gets OntFileWriter for purposes of brand new ontology registration
    * according to given relevant parameters.
    */
  private def getOntFileWriter(user: db.User): OntFileWriter = {
    getOntFileWriterOpt(user).getOrElse(
      error(400, s"no suitable parameters were specified to indicate contents"))
  }

  /**
    * Gets OntFileWriter for purposes on new **version**
    * according to given relevant parameters
    */
  private def getOntFileWriterOpt(user: db.User): Option[OntFileWriter] = {
    Option(if (fileParams.isDefinedAt("file"))
      getOntFileWriterForJustUploadedFile
    else if (getParam("contents").isDefined)
      getOntFileWriterForGivenContents
    else if (getParam("uploadedFilename").isDefined && getParam("uploadedFormat").isDefined)
      getOntFileWriterForPreviouslyUploadedFile(user.userName)
    else null)
  }

  private def getOntFileWriterForJustUploadedFile: OntFileWriter = {
    val fileItem = fileParams.getOrElse("file", missing("file"))

    // todo make format param optional
    val format = requireParam("format")

    logger.debug(s"uploaded file=${fileItem.getName} size=${fileItem.getSize} format=$format")
    //val fileContents = new String(fileItem.get(), fileItem.charset.getOrElse("utf8"))
    //val contentType = file.contentType.getOrElse("application/octet-stream")

    FileItemWriter(format, fileItem)
  }

  private def getOntFileWriterForGivenContents: OntFileWriter = {
    val contents = requireParam("contents")
    val format   = requireParam("format")
    logger.debug(s"getOntFileWriterForGivenContents: format=$format contents=`$contents`")
    StringWriter(format, contents)
  }

  private def getOntFileWriterForPreviouslyUploadedFile(userName: String): OntFileWriter = {
    val filename = requireParam("uploadedFilename")
    val format   = requireParam("uploadedFormat")
    logger.debug(s"getOntFileWriterForPreviouslyUploadedFile: filename=$filename format=$format")
    FileWriter(format, ontService.getUploadedFile(userName, filename))
  }

  private def getOntFileWriterWithMetadata(uri: String,
                                           versionOpt: Option[String],
                                           metadata: String
                                          ): OntFileWriter = {

    val (ont, ontVersion, version) = resolveOntologyVersion(uri, versionOpt)
    logger.debug(s"getOntFileWriterWithMetadata: metadata=`$metadata`")
    val newMetadata = parse(metadata).extract[Map[String, JValue]]
    UpdateWithMetadataWriter(ontVersion.format, ont, ontVersion, version, newMetadata)
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
                                    versionVisibility: OntVisibility.Value,
                                    nameOpt:        Option[String],
                                    ontFileWriter:  OntFileWriter,
                                    doVerifyOwner:  Boolean = true
                                   ) = {
    val (version, date) = getVersion

    // TODO capture version_status from parameter
    val version_status: Option[String] = None

    Try(ontService.createOntologyVersion(uri, originalUriOpt, nameOpt, user.userName,
        version, versionVisibility, version_status,
        date, ontFileWriter, doVerifyOwner)) match {
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
   * Updates a particular version.
   * TODO handle other updatable pieces
   */
  private def updateOntologyVersion(uri:            String,
                                    originalUriOpt: Option[String],
                                    version:        String,
                                    versionVisibilityOpt: Option[OntVisibility.Value],
                                    nameOpt:        Option[String],
                                    user:           db.User,
                                    doVerifyOwner:  Boolean = true
                                   ) = {
    Try(ontService.updateOntologyVersion(uri, originalUriOpt,
        version,
        versionVisibilityOpt,
        nameOpt,
        user.userName,
        doVerifyOwner)
    ) match {
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
  private def deleteOntologyVersion(uri: String,
                                    version: String,
                                    user: db.User,
                                    doVerifyOwner: Boolean = true) = {

    Try(ontService.deleteOntologyVersion(uri, version, user.userName, doVerifyOwner)) match {
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
  private def deleteOntology(uri: String,
                             user: db.User,
                             doVerifyOwner: Boolean = true) = {

    Try(ontService.deleteOntology(uri, user.userName, doVerifyOwner)) match {
      case Success(ontologyResult) => ontologyResult

      case Failure(exc: NoSuch)                       => error(404, exc.details)
      case Failure(exc: NotAMember)                   => error(401, exc.details)
      case Failure(exc: CannotDeleteOntology)         => error500(exc)
      case Failure(exc)                               => error500(exc)
    }
  }
}
