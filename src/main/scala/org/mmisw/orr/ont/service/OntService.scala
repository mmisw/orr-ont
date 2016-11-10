package org.mmisw.orr.ont.service

import java.io.File
import java.net.{URI, URISyntaxException}

import com.mongodb.casbah.Imports._
import com.typesafe.scalalogging.{StrictLogging => Logging}
import org.joda.time.DateTime
import org.mmisw.orr.ont.db.{Ontology, OntologyVersion}
import org.mmisw.orr.ont.swld.{PossibleOntologyInfo, ontFileLoader, ontUtil}
import org.mmisw.orr.ont._

import scala.util.{Failure, Success, Try}


trait OntFileWriter {
  val format: String
  def write(destFile: File): Unit
}

case class UploadedFileInfo(userName: String,
                            filename: String,
                            format: String,
                            possibleOntologyUris: Map[String, PossibleOntologyInfo])


sealed abstract class OntOwner
case class OrgOntOwner(orgName: String) extends OntOwner
case class UserOntOwner(userName: String) extends OntOwner

object OntOwner {
  val ownerNamePattern = "(~?)(.*)".r

  def apply(ownerName: String): OntOwner = {
    ownerName match {
      case ownerNamePattern("", orgName)   => OrgOntOwner(orgName)
      case ownerNamePattern("~", userName) => UserOntOwner(userName)
    }
  }

  def unapply(arg: OntOwner): String = arg match {
    case OrgOntOwner(orgName)   => orgName
    case UserOntOwner(userName) => "~" + userName
  }
}


class OntService(implicit setup: Setup) extends BaseService(setup) with Logging {

  /**
    * Try to resolve an ontology, possibly with http scheme (HTTP-HTTPS) change.
    *
    * @param uri               Requested ontology URI
    * @param httpSchemeChange  Try http scheme change? True by default.
    * @return                  The found ontology is any
    */
  def resolveOntology(uri: String,
                      httpSchemeChange: Boolean = true
                     ): Option[Ontology] = {

    ontDAO.findOneById(uri) orElse {
      if (httpSchemeChange) {
        replaceHttpScheme(uri) flatMap { uri2 =>
          logger.debug(s"resolving '$uri2' (after http scheme change)")
          ontDAO.findOneById(uri2)
        }
      }
      else None
    }
  }

  /**
   * Returns ontology elements for a given URI and optional version.
   *
   * @param uri         ontology URI
   * @param versionOpt  optional version. If None, latest version is returned
   * @return            (ont, ontVersion, version)
   */
  def resolveOntologyVersion(uri: String, versionOpt: Option[String] = None): (Ontology, OntologyVersion, String) = {

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

  def resolveOntologyVersion(ont: Ontology, versionOpt: Option[String]): (OntologyVersion, String) = {
    versionOpt match {
      case Some(version) =>
        val ontVersion = ont.versions.getOrElse(version, throw NoSuchOntVersion(ont.uri, version))
        (ontVersion, version)

      case None =>
        val (ontVersion, version) = getLatestVersion(ont).getOrElse(throw Bug(s"'${ont.uri}', no versions registered"))
        (ontVersion, version)
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
          getOntologySummaryResult(ont, ontVersion, version, privileged, includeMetadata = false)

        case None =>
          // This will be case when all versions have been deleted.
          // TODO perhaps allow ontology entry with no versions?
          logger.warn(s"bug: '${ont.uri}', no versions registered")
          OntologySummaryResult(ont.uri, "version?", "name?")
      }
    }
  }

  def getOntologySummaryResult(ont: Ontology,
                               ontVersion: OntologyVersion,
                               version: String,
                               privileged: Boolean,
                               includeMetadata: Boolean = false,
                               versionsOpt: Option[List[String]] = None
  ): OntologySummaryResult = {

    val resourceTypeOpt = ontVersion.resourceType map ontUtil.simplifyResourceType
    OntologySummaryResult(
      uri          = ont.uri,
      version      = version,
      name         = ontVersion.name,
      submitter    = if (privileged) Some(ontVersion.userName) else None,
      ownerName    = Some(ont.ownerName),
      author       = ontVersion.author,
      status       = ontVersion.status,
      metadata     = if (includeMetadata) Some(ontUtil.toOntMdMap(ontVersion.metadata)) else None,
      ontologyType = ontVersion.ontologyType,
      resourceType = resourceTypeOpt,
      versions     = versionsOpt,
      format       = Option(ontVersion.format),
      visibility   = ontVersion.visibility
    )
  }

  def getOntologySubjects(ont: Ontology,
                          ontVersion: OntologyVersion,
                          version: String,
                          includeMetadata: Boolean = false
  ): OntologySubjectsResult = {

    val (file, actualFormat) = getOntologyFile(ont.uri, version, ontVersion.format)
    val ontModel = ontUtil.loadOntModel(ont.uri, file, actualFormat)
    val subjects = ontUtil.getOntologySubjects(ontModel, excludeUri = ont.uri)
    val metadata = if (includeMetadata) Some(ontUtil.toOntMdMap(ontVersion.metadata)) else None

    OntologySubjectsResult(
      uri          = ont.uri,
      version      = version,
      name         = ontVersion.name,
      subjects     = subjects,
      metadata     = metadata
    )
  }

  def getExternalOntologySubjects(uri: String): Try[ExternalOntologySubjectsResult] = {
    ontUtil.loadExternalModel(uri) map { ontModel =>
      ExternalOntologySubjectsResult(
        uri       = uri,
        subjects  = ontUtil.getOntologySubjects(ontModel, excludeUri = uri)
      )
    }
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
      case Some(version) => Some((ont.versions(version), version))
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
                     versionVisibility: Option[String],
                     versionStatus:  Option[String],
                     date:           String,
                     userName:       String,
                     ownerName:      String,
                     ontFileWriter:  OntFileWriter,
                     contact_name:   Option[String] = None, // for AquaImporter
                     ownerAsAuthorName: Option[String] = None
                    ) = {

    if (ontDAO.findOneById(uri).isDefined) throw OntologyAlreadyRegistered(uri)

    validateUri(uri)

    val md = writeOntologyFile(uri, originalUriOpt, version, ontFileWriter)

    val authorOpt: Option[String] = contact_name orElse ontUtil.extractAuthor(md) orElse ownerAsAuthorName

    logger.debug(s"createOntology: md=$md  authorOpt=$authorOpt")

    // TODO remove these special entries in OntologyVersion
    val map = ontUtil.extractSomeProps(md)
    val ontologyTypeOpt = map.get("ontologyType")
    val resourceTypeOpt = map.get("resourceType")

    val ontVersion = OntologyVersion(name, userName, ontFileWriter.format, new DateTime(date),
                                     visibility = versionVisibility,
                                     status = versionStatus,
                                     author = authorOpt,
                                     metadata = ontUtil.toOntMdList(md),
                                     ontologyType = ontologyTypeOpt,
                                     resourceType = resourceTypeOpt)

    val ont = Ontology(uri, ownerName, versions = Map(version -> ontVersion))

    Try(ontDAO.insert(ont, WriteConcern.Safe)) match {
      case Success(_) =>
        sendNotificationEmail("New ontology registered",
          s"""
            |The following ontology has been registered:
            |
            | URI: $uri
            | ${getResolveWith(uri)}
            | Version: $version
            | Registered: ${ontVersion.date}
            | Owner: $ownerName
            | Submitter: $userName
          """.stripMargin
        )
        OntologyRegistrationResult(uri,
          version = Some(version),
          visibility = ontVersion.visibility,
          status = ontVersion.status,
          registered = Some(ontVersion.date)
        )

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
                            versionVisibility: Option[String],
                            versionStatus:   Option[String],
                            date:            String,
                            ontFileWriter:   OntFileWriter,
                            doVerifyOwner:   Boolean = true,
                            contact_name:    Option[String] = None // for AquaImporter
                           ) = {

    val ont = ontDAO.findOneById(uri).getOrElse(throw NoSuchOntUri(uri))

    if (doVerifyOwner) verifyOwner(userName, ont)

    val md = writeOntologyFile(uri, originalUriOpt, version, ontFileWriter)

    val authorOpt: Option[String] = contact_name orElse ontUtil.extractAuthor(md) orElse
      ont.latestVersion.flatMap(_.author)

    val name = nameOpt.getOrElse(ont.latestVersion.map(_.name).getOrElse(""))
    logger.debug(s"createOntologyVersion: md=$md  authorOpt=$authorOpt  name=$name")

    // TODO remove these special entries in OntologyVersion
    val map = ontUtil.extractSomeProps(md)
    val ontologyTypeOpt = map.get("ontologyType")
    val resourceTypeOpt = map.get("resourceType")

    val ontVersion = OntologyVersion(name, userName, ontFileWriter.format, new DateTime(date),
                                     visibility = versionVisibility,
                                     status = versionStatus,
                                     author = authorOpt,
                                     metadata = ontUtil.toOntMdList(md),
                                     ontologyType = ontologyTypeOpt,
                                     resourceType = resourceTypeOpt)

    val update = ont.copy(versions = ont.versions ++ Map(version -> ontVersion))

    logger.info(s"update: $update")

    Try(ontDAO.update(MongoDBObject("_id" -> uri), update, upsert = false, multi = false, WriteConcern.Safe)) match {
      case Success(result) =>
        sendNotificationEmail("New ontology version registered",
          s"""
             |A new ontology version has been registered:
             |
             | URI: $uri
             | ${getResolveWith(uri)}
             | Version: $version
             | Submitter: $userName
             | Updated: ${ontVersion.date}
          """.stripMargin
        )
        OntologyRegistrationResult(uri,
          visibility = ontVersion.visibility,
          status = ontVersion.status,
          version = Some(version),
          updated = Some(ontVersion.date)
        )

      case Failure(exc)  => throw CannotInsertOntologyVersion(uri, version, exc.getMessage)
    }
  }

  /**
   * Updates a particular version.
   */
  def updateOntologyVersion(uri:            String,
                            originalUriOpt: Option[String],
                            version:        String,
                            versionVisibilityOpt: Option[String],
                            versionStatusOpt:     Option[String],
                            nameOpt:        Option[String],
                            userName:       String,
                            doVerifyOwner:  Boolean = true) = {

    val ont = ontDAO.findOneById(uri).getOrElse(throw NoSuchOntUri(uri))

    if (doVerifyOwner) verifyOwner(userName, ont)

    var ontVersion = ont.versions.getOrElse(version, throw NoSuchOntVersion(uri, version))

    versionVisibilityOpt foreach { _ =>
      ontVersion = ontVersion.copy(visibility = versionVisibilityOpt)
    }

    versionStatusOpt foreach { _ =>
      ontVersion = ontVersion.copy(status = versionStatusOpt)
    }

    nameOpt foreach { name =>
      ontVersion = ontVersion.copy(name = name)
    }

    val newVersions = ont.versions.updated(version, ontVersion)
    val update = ont.copy(versions = newVersions)
    //logger.info(s"update: $update")

    Try(ontDAO.update(MongoDBObject("_id" -> uri), update, upsert = false, multi = false, WriteConcern.Safe)) match {
      case Success(result) =>
        OntologyRegistrationResult(uri,
          version = Some(version),
          visibility = ontVersion.visibility,
          status = ontVersion.status,
          updated = Some(ontVersion.date)
        )

      case Failure(exc)  => throw CannotUpdateOntologyVersion(uri, version, exc.getMessage)
    }
  }

  /**
   * Deletes a particular version.
   */
  def deleteOntologyVersion(uri:            String,
                            version:        String,
                            userName:       String,
                            doVerifyOwner:  Boolean = true) = {

    val ont = ontDAO.findOneById(uri).getOrElse(throw NoSuchOntUri(uri))

    if (doVerifyOwner) verifyOwner(userName, ont)

    ont.versions.getOrElse(version, throw NoSuchOntVersion(uri, version))

    val update = ont.copy(versions = ont.versions - version)
    //logger.info(s"update: $update")

    if (update.versions.isEmpty) {
      doDeleteOntology(ont)
    }
    else {
      logger.debug(s"deleteOntologyVersion: uri=$uri version=$version")
      Try(ontDAO.update(MongoDBObject("_id" -> uri), update, upsert = false, multi = false, WriteConcern.Safe)) match {
        case Success(result) =>
          logger.debug(s"deleteOntologyVersion: success: result=$result")
          OntologyRegistrationResult(uri, version = Some(version), removed = Some(DateTime.now())) //TODO

        case Failure(exc)  => throw CannotDeleteOntologyVersion(uri, version, exc.getMessage)
      }
    }
  }

  /**
   * Deletes a whole ontology entry.
   */
  def deleteOntology(uri:            String,
                     userName:       String,
                     doVerifyOwner:  Boolean = true) = {

    val ont = ontDAO.findOneById(uri).getOrElse(throw NoSuchOntUri(uri))

    if (doVerifyOwner) verifyOwner(userName, ont)

    doDeleteOntology(ont)
  }

  /**
   * Deletes the whole ontologies collection
   */
  def deleteAll() = ontDAO.remove(MongoDBObject())

  ///////////////////////////////////////////////////////////////////////////

  /**
    * If uri starts with "http:" or "https:", returns a Some
    * with the same uri but with the scheme replaced for the other.
    * Otherwise, None.
    */
  // #31 "https == http for purposes of IRI identification"
  private def replaceHttpScheme(uri: String): Option[String] = {
    if      (uri.startsWith("http:"))  Some("https:" + uri.substring("http:".length))
    else if (uri.startsWith("https:")) Some("http:" +  uri.substring("https:".length))
    else None
  }

  /**
    * @return Empty string if uri is self-resolvable; otherwise: "Resolve with: &lt;url?uri=uri>",
    */
  private def getResolveWith(uri: String): String = {
    val myUrl = setup.cfg.deployment.url + "/"
    if (uri.startsWith(myUrl)) "" else s"Resolve with: $myUrl?uri=$uri"
  }

  private def doDeleteOntology(ont: Ontology) = {
    logger.debug(s"doDeleteOntology: ont.uri=${ont.uri}")
    Try(ontDAO.removeById(ont.uri, WriteConcern.Safe)) match {
      case Success(result) =>
        logger.debug(s"doDeleteOntology: success: result=$result")
        OntologyRegistrationResult(ont.uri, removed = Some(DateTime.now())) //TODO

      case Failure(exc)  => throw CannotDeleteOntology(ont.uri, exc.getMessage)
    }
  }

  /**
   * Verifies the user can make changes or removals wrt to the given ont.
   */
  private def verifyOwner(userName: String, ont: Ontology): Unit = {
    OntOwner(ont.ownerName) match {
      case OrgOntOwner(orgName)        => verifyOrgAndUser(orgName, userName)
      case UserOntOwner(ownerUserName) => verifyUserIsOwner(ownerUserName, userName)
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

  private def verifyUserIsOwner(ownerUserName: String, userName: String): Unit = {
    if (ownerUserName != userName) throw NotOntOwner(userName)
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

  private val baseDir = setup.baseDir
  private val uploadsDir = new File(baseDir, "uploads")
  private val ontsDir = new File(baseDir, "onts")

}
