package org.mmisw.orr.ont.service

import java.io.File
import java.net.{URI, URISyntaxException}

import com.mongodb.casbah.Imports._
import com.typesafe.scalalogging.{StrictLogging => Logging}
import org.joda.time.DateTime
import org.mmisw.orr.ont.db.{Ontology, OntologyVersion}
import org.mmisw.orr.ont.swld.{PossibleOntologyInfo, ontFileLoader, ontUtil}
import org.mmisw.orr.ont.{OntologyRegistrationResult, OntologySummaryResult, Setup}

import scala.util.{Failure, Success, Try}


trait OntFileWriter {
  val format: String
  def write(destFile: File): Unit
}

case class UploadedFileInfo(userName: String,
                            filename: String,
                            format: String,
                            possibleOntologyUris: Map[String, PossibleOntologyInfo])


/**
 * Ontology service
 */
class OntService(implicit setup: Setup) extends BaseService(setup) with Logging {

  /**
   * Returns ontology elements for a given URI and optional version.
   *
   * @param uri         ontology URI
   * @param versionOpt  optional version. If None, latest version is returned
   * @return            (ont, ontVersion, version)
   */
  def resolveOntology(uri: String, versionOpt: Option[String] = None): (Ontology, OntologyVersion, String) = {

    val ont = getOnt(uri)

    versionOpt match {
      case Some(version) =>
        val ontVersion = ont.versions.getOrElse(version, throw NoSuchOntVersion(uri, version))
        (ont, ontVersion, version)

      case None =>
        val (ontVersion, version) = getLatestVersion(ont).getOrElse(throw Bug(s"'$uri', no versions registered"))
          (ont, ontVersion, version)
    }
  }

  /**
    * Gets the ontologies satisfying the given query.
    *
    * @param query       Query
    * @param privileged  True to include privileged information
    * @return            iterator
    */
  def getOntologies(query: MongoDBObject, privileged: Boolean): Iterator[OntologySummaryResult] = {
    ontDAO.find(query) map { ont =>
      getLatestVersion(ont) match {
        case Some((ontVersion, version)) =>
          getOntologySummaryResult(ont, ontVersion, version, privileged)

        case None =>
          // This will be case when all versions have been deleted.
          // TODO perhaps allow ontology entry with no versions?
          logger.warn(s"bug: '${ont.uri}', no versions registered")
          OntologySummaryResult(ont.uri, "version?", "name?")
      }
    }
  }

  def getOntologySummaryResult(ont: Ontology, ontVersion: OntologyVersion, version: String,
                               privileged: Boolean,
                               versionsOpt: Option[List[String]] = None
  ): OntologySummaryResult = {

    val resourceTypeOpt = ontVersion.resourceType map ontUtil.simplifyResourceType
    OntologySummaryResult(
      uri          = ont.uri,
      version      = version,
      name         = ontVersion.name,
      submitter    = if (privileged) Some(ontVersion.userName) else None,
      orgName      = ont.orgName,
      author       = ontVersion.author,
      status       = ontVersion.status,
      metadata     = ontVersion.metadata,
      ontologyType = ontVersion.ontologyType,
      resourceType = resourceTypeOpt,
      versions     = versionsOpt,
      format       = Option(ontVersion.format)
    )
  }

  /**
    * Gets the ontologies satisfying the given query.
    *
    * @param query  Query
    * @return       iterator
    */
  def getOntologyUris(query: MongoDBObject): Iterator[String] = {
    for (ont <- ontDAO.find(query))
      yield ont.uri
  }

  /**
    * Gets all ontologies.
    *
    * @return       iterator
    */
  def getAllOntologyUris: Iterator[String] = {
    for (ont <- ontDAO.find(MongoDBObject()))
      yield ont.uri
  }

  private def getOnt(uri: String) = ontDAO.findOneById(uri).getOrElse(throw NoSuchOntUri(uri))

  private def getLatestVersion(ont: Ontology): Option[(OntologyVersion,String)] = {
    ont.sortedVersionKeys.headOption match {
      case Some(version) => Some((ont.versions.get(version).get, version))
      case None => None
    }
  }

  /**
   * Gets the file for a given ontology.
   *
   * @param uri        ontology URI
   * @param version    version
   * @param reqFormat  requested format
   * @return           (file, actualFormat)
   */
  def getOntologyFile(uri: String, version: String, reqFormat: String): (File,String) = {
    val uriEnc = uri.replace('/', '|')
    val uriDir = new File(ontsDir, uriEnc)
    val versionDir = new File(uriDir, version)
    val actualFormat = ontUtil.storedFormat(reqFormat)

    val file = new File(versionDir, s"file.$actualFormat")

    if (file.canRead) {
      // already exists, just return it
      (file, actualFormat)
    }
    else try {
      val (fromFile, fromFormat) = {
        // TODO needs revision
        val tryFormats = ontUtil.storedFormats
        def doTry(formats: List[String]): (File, String) = formats match {
          case format :: rest =>
            val file = new File(versionDir, "file." + format)
            if (file.exists()) (file, format)
            else doTry(rest)
          case Nil =>
            throw CannotCreateFormat(uri, version, reqFormat,
              s"Not base file was found with format in: ${tryFormats.mkString(", ")}")
        }
        doTry(tryFormats)
      }
      ontUtil.convert(uri, fromFile, fromFormat, file, toFormat = actualFormat) match {
        case Some(resFile) => (resFile, actualFormat)
        case _ => throw NoSuchOntFormat(uri, version, reqFormat) // TODO include accepted formats
      }
    }
    catch {
      case exc: Exception => // likely com.hp.hpl.jena.shared.NoWriterForLangException
        val exm = s"${exc.getClass.getName}: ${exc.getMessage}"
        throw CannotCreateFormat(uri, version, reqFormat, exm)
    }
  }

  /**
   * Creates a new ontology entry.
   */
  def createOntology(uri:            String,
                     originalUriOpt: Option[String],
                     name:           String,
                     version:        String,
                     version_status: Option[String],
                     contact_name:   Option[String],
                     date:           String,
                     userName:       String,
                     orgName:        String,
                     ontFileWriter:  OntFileWriter) = {

    if (ontDAO.findOneById(uri).isDefined) throw OntologyAlreadyRegistered(uri)

    validateUri(uri)

    val md = writeOntologyFile(uri, originalUriOpt, version, ontFileWriter)

    logger.debug(s"createOntology: md=$md")

    // TODO remove these special entries in OntologyVersion
    val map = ontUtil.extractSomeProps(md)
    val ontologyTypeOpt = map.get("ontologyType")
    val resourceTypeOpt = map.get("resourceType")

    val ontVersion = OntologyVersion(name, userName, ontFileWriter.format, new DateTime(date),
                                     version_status, contact_name,
                                     metadata = ontUtil.toOntMdList(md),
                                     ontologyType = ontologyTypeOpt,
                                     resourceType = resourceTypeOpt)

    val ont = Ontology(uri, Some(orgName), versions = Map(version -> ontVersion))

    Try(ontDAO.insert(ont, WriteConcern.Safe)) match {
      case Success(_) =>
        OntologyRegistrationResult(uri, version = Some(version), registered = Some(ontVersion.date))

      case Failure(exc) => throw CannotInsertOntology(uri, exc.getMessage)
          // perhaps duplicate key in concurrent registration
    }
  }

  /**
   * Creates a version for an existing ontology.
   */
  def createOntologyVersion(uri:             String,
                            originalUriOpt:  Option[String],
                            nameOpt:         Option[String],
                            userName:        String,
                            version:         String,
                            version_status:  Option[String],
                            contact_name:    Option[String],
                            date:            String,
                            ontFileWriter:   OntFileWriter) = {

    val ont = ontDAO.findOneById(uri).getOrElse(throw NoSuchOntUri(uri))

    verifyOwner(userName, ont)

    val md = writeOntologyFile(uri, originalUriOpt, version, ontFileWriter)

    logger.debug(s"createOntologyVersion: md=$md")

    // TODO remove these special entries in OntologyVersion
    val map = ontUtil.extractSomeProps(md)
    val ontologyTypeOpt = map.get("ontologyType")
    val resourceTypeOpt = map.get("resourceType")

    var ontVersion = OntologyVersion("", userName, ontFileWriter.format, new DateTime(date),
                                     version_status, contact_name,
                                     metadata = ontUtil.toOntMdList(md),
                                     ontologyType = ontologyTypeOpt,
                                     resourceType = resourceTypeOpt)

    nameOpt foreach (name => ontVersion = ontVersion.copy(name = name))

    var update = ont
    update = update.copy(versions = ont.versions ++ Map(version -> ontVersion))

    logger.info(s"update: $update")

    Try(ontDAO.update(MongoDBObject("_id" -> uri), update, false, false, WriteConcern.Safe)) match {
      case Success(result) =>
        OntologyRegistrationResult(uri, version = Some(version), updated = Some(ontVersion.date))

      case Failure(exc)  => throw CannotInsertOntologyVersion(uri, version, exc.getMessage)
    }
  }

  /**
   * Updates a particular version.
   */
  def updateOntologyVersion(uri:            String,
                            originalUriOpt: Option[String],
                            version:        String,
                            name:           String,
                            userName:       String) = {

    val ont = ontDAO.findOneById(uri).getOrElse(throw NoSuchOntUri(uri))
    verifyOwner(userName, ont)

    var ontVersion = ont.versions.getOrElse(version, throw NoSuchOntVersion(uri, version))

    ontVersion = ontVersion.copy(name = name)

    val newVersions = ont.versions.updated(version, ontVersion)
    val update = ont.copy(versions = newVersions)
    //logger.info(s"update: $update")

    Try(ontDAO.update(MongoDBObject("_id" -> uri), update, false, false, WriteConcern.Safe)) match {
      case Success(result) =>
        OntologyRegistrationResult(uri, version = Some(version), updated = Some(ontVersion.date))

      case Failure(exc)  => throw CannotUpdateOntologyVersion(uri, version, exc.getMessage)
    }
  }

  /**
   * Deletes a particular version.
   */
  def deleteOntologyVersion(uri: String, version: String, userName: String) = {
    val ont = ontDAO.findOneById(uri).getOrElse(throw NoSuchOntUri(uri))
    verifyOwner(userName, ont)
    ont.versions.getOrElse(version, throw NoSuchOntVersion(uri, version))

    val update = ont.copy(versions = ont.versions - version)
    //logger.info(s"update: $update")

    Try(ontDAO.update(MongoDBObject("_id" -> uri), update, false, false, WriteConcern.Safe)) match {
      case Success(result) =>
        OntologyRegistrationResult(uri, version = Some(version), removed = Some(DateTime.now())) //TODO

      case Failure(exc)  => throw CannotDeleteOntologyVersion(uri, version, exc.getMessage)
    }
  }

  /**
   * Deletes a whole ontology entry.
   */
  def deleteOntology(uri: String, userName: String) = {
    val ont = ontDAO.findOneById(uri).getOrElse(throw NoSuchOntUri(uri))
    verifyOwner(userName, ont)

    Try(ontDAO.remove(ont, WriteConcern.Safe)) match {
      case Success(result) =>
        OntologyRegistrationResult(uri, removed = Some(DateTime.now())) //TODO

      case Failure(exc)  => throw CannotDeleteOntology(uri, exc.getMessage)
    }
  }

  /**
   * Deletes the whole ontologies collection
   */
  def deleteAll() = ontDAO.remove(MongoDBObject())

  ///////////////////////////////////////////////////////////////////////////

  /**
   * Verifies the user can make changes or removals wrt to the given ont.
   */
  private def verifyOwner(userName: String, ont: Ontology): Unit = {
    ont.orgName match {
      case Some(orgName) => verifyOrgAndUser(orgName, userName)

      case None => // TODO handle no-organization case
        throw Bug(s"currently I expect registered ont to have org associated")
    }
  }

  /**
   * Verifies the given organization and the userName against that organization.
   */
  private def verifyOrgAndUser(orgName: String, userName: String): Unit = {
    orgsDAO.findOneById(orgName) match {
      case Some(org) =>
        if (!org.members.contains(userName)) throw NotAMember(userName, orgName)

      case None => throw NoSuchOrg(orgName)
    }
  }

  private def validateUri(uri: String) {
    try new URI(uri)
    catch {
      case e: URISyntaxException => throw InvalidUri(uri, e.getMessage)
    }
    if (uri.contains("|")) throw InvalidUri(uri, "cannot contain the '|' character")
  }

  def saveUploadedOntologyFile(userName: String, ontFileWriter: OntFileWriter)
  : UploadedFileInfo = {

    val userDir = new File(uploadsDir, userName)
    if (!userDir.isDirectory && !userDir.mkdirs()) {
      throw CannotCreateDirectory(userDir.getAbsolutePath)
    }
    val now = System.currentTimeMillis()
    val filename = s"$now.${ontFileWriter.format}"
    val destFile = new File(userDir, filename)

    logger.debug(s"saving uploaded file to $destFile")
    ontFileWriter.write(destFile)

    logger.debug(s"loading model from $destFile")
    val ontModelLoadedResult = ontFileLoader.loadOntModel(destFile, ontFileWriter.format)
    val actualFile = ontModelLoadedResult.file
    val format = ontModelLoadedResult.format
    val ontModel = ontModelLoadedResult.ontModel

    UploadedFileInfo(userName, actualFile.getName, format,
      ontFileLoader.getPossibleOntologyUris(ontModel, actualFile))
  }

  def getUploadedFile(userName: String, filename: String): File = {
    val userDir = new File(uploadsDir, userName)
    val file = new File(userDir, filename)
    logger.debug(s"getUploadedFile: file=$file")
    file
  }

  private def getVersionDirectory(uri: String, version: String): File = {
    require(!uri.contains("|"))
    val uriEnc = uri.replace('/', '|')
    val uriDir = new File(ontsDir, uriEnc)
    val versionDir = new File(uriDir, version)
    if (!versionDir.isDirectory && !versionDir.mkdirs()) {
      throw CannotCreateDirectory(versionDir.getAbsolutePath)
    }
    versionDir
  }

  private def writeOntologyFile(uri:            String,
                                originalUriOpt: Option[String],
                                version:        String,
                                ontFileWriter:  OntFileWriter
                               ): Map[String,List[String]] = {

    val versionDir = getVersionDirectory(uri, version)
    val destFile = new File(versionDir, s"file.${ontFileWriter.format}")

    originalUriOpt match {
      case None =>
        // it's a "re-hosted" registration.
        ontFileWriter.write(destFile)

      case Some(originalUri) =>
        // it's a "fully-hosted" registration.

        // first, save the original file
        val origDest = new File(versionDir, s"file_orig.${ontFileWriter.format}")
        ontFileWriter.write(origDest)

        // now, load model and "move" the namespace from `originalUri` to `uri`
        val ontModel = ontUtil.loadOntModel(originalUri, origDest, ontFileWriter.format)
        ontUtil.replaceNamespace(ontModel, originalUri, uri)
        ontUtil.writeModel(uri, ontModel, ontFileWriter.format, destFile)
    }

    ontUtil.getPropsFromOntMetadata(uri, destFile, ontFileWriter.format)
  }

  private val baseDir = setup.filesConfig.getString("baseDirectory")
  private val uploadsDir = new File(baseDir, "uploads")
  private val ontsDir = new File(baseDir, "onts")

}
