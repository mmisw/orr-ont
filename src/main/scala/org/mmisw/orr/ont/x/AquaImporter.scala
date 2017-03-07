package org.mmisw.orr.ont.x

import java.io.{File, PrintWriter}
import java.util.ServiceConfigurationError

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.{StrictLogging => Logging}
import org.joda.time.DateTime
import org.mmisw.orr.ont.{Cfg, Setup}
import org.mmisw.orr.ont.db.{OntVisibility, Organization}
import org.mmisw.orr.ont.service.{OntFileWriter, OntService, OrgService, UserService}
import org.mmisw.orr.ont.swld.ontUtil
import org.mmisw.orr.ont.util.Emailer

import scala.collection.immutable.TreeMap
import scala.io.Source
import scala.xml.{Node, NodeSeq, XML}


/**
 * Imports data from previous database.
 */
object AquaImporter extends App with Logging {
  private val TESTING_AUTHORITIES_REGEX = "mmitest|test(ing)?(_.*)?|.*_test(ing)?"

  val config = {
    val configFilename = "/etc/orront.conf"
    logger.info(s"Loading configuration from $configFilename")
    val configFile = new File(configFilename)
    if (!configFile.canRead) {
      throw new ServiceConfigurationError("Could not read configuration file " + configFile)
    }
    ConfigFactory.parseFile(configFile).resolve()
  }
  val cfg = Cfg(config)

  implicit val setup = new Setup(cfg, emailer = new Emailer(cfg.email))

  val userService = new UserService
  val orgService = new OrgService
  val ontService = new OntService

  userService.deleteAll()
  orgService.deleteAll()
  ontService.deleteAll()

  val importConfig = config.getConfig("import")
  val users    = AquaUser.loadEntities(importConfig.getString("aquaUsers"))
  val onts     = VAquaOntology.loadEntities(importConfig.getString("aquaOnts"))
  val ontFiles = AquaOntologyFile.loadEntities(importConfig.getString("aquaOntFiles"))

  val aquaUploadsDirOpt = if (importConfig.hasPath("aquaUploadsDir"))
    Some(importConfig.getString("aquaUploadsDir")) else None

  val aquaOntOpt = if (importConfig.hasPath("aquaOnt"))
    Some(importConfig.getString("aquaOnt")) else None

  require(aquaUploadsDirOpt.isDefined != aquaOntOpt.isDefined)

  // capture the oldest submission by this user_id:
  val userIdFirstSubmissions = users map { case (user_id, _) ⇒
    (user_id, scala.collection.mutable.HashSet[DateTime]())
  }

  val byUri = onts.groupBy(_._2.uri)
  val orgNames = getOrgNames(byUri.keys)
  val orgFirstSubmissions = Map(orgNames.map(o => (o, scala.collection.mutable.HashSet[DateTime]())).toArray: _*)

  println(s"Loaded: users=${users.size} onts=${onts.size}")
  println(s"Extracted ${orgNames.size} orgNames")

  var usersCreated: Int = 0
  var orgsCreated: Int = 0
  var ontsCreated: Int = 0

  processUsers(users)
  processOrgs(orgNames)
  processOnts(byUri)
  postProcessOrgs()
  postProcessUsers()

  setup.destroy()

  byUri.keys.toList.sorted foreach { uri ⇒ println(uri) }
  println(s"""
           |  ${onts.size} total ontology versions processed
           |  $ontsCreated ontologies created
           |  $usersCreated users created
           |  $orgsCreated orgs created
         """.stripMargin)

  ///////////////////////////////////////////////////////////////////////////

  /** creates/updates the given users */
  private def processUsers(users: Map[String,AquaUser]) = {
    println("USERS:")
    users.values foreach { u =>
      if (u.username != "admin") {
        println(f"\t${u.username}%-20s - ${u.email}")
        if (userService.existsUser(u.username)) {
          userService.updateUser(u.username, map = u.map)  // don't set any 'updated'
        }
        else {
          val aquaDateCreated = DateTime.parse(u.date_created)

          userService.createUser(
            u.username, u.email, Some(u.phone), u.firstname, u.lastname,
            Right(u.password), None,
            registered = aquaDateCreated,  // subject to be changed in postProcessUsers
            updated = Some(aquaDateCreated)
          )
          usersCreated += 1
        }
      }
    }
  }

  private def orgNameFromUri(uri: String): Option[String] = {
    val re = """https?://mmisw\.org/ont/([^/]+)/.*""".r
    uri match {
      case re(orgName) => Some(orgName)
      case _           => None
    }
  }

  private def getOrgNames(uris: Iterable[String]): Seq[String] =
    uris.flatMap(orgNameFromUri).toSeq.sorted

  private def processOrgs(orgNames: Seq[String]) {
    println("ORGS:")
    orgNames foreach { orgName =>
      if (!orgService.existsOrg(orgName)) {
        println(s"\t$orgName")
        orgService.createOrg(Organization(orgName, orgName))
        orgsCreated += 1
      }
    }
  }

  /**
   * Resets the registration date of each org to reflect the earliest
   * ont submission against that org.
   */
  private def postProcessOrgs(): Unit = {
    orgFirstSubmissions foreach { case (orgName, firstSubmissions) ⇒
      if (firstSubmissions.nonEmpty) {
        val earliest = firstSubmissions.minBy(dt => dt.getMillis)
        orgService.updateOrg(orgName, registered = Some(earliest))
      }
    }
  }

  /**
    * Resets the registration date of each user to reflect the earliest ont submission
    * in case the captured user's date_created is after that ont submission.
    */
  private def postProcessUsers(): Unit = {
    userIdFirstSubmissions foreach { case (user_id, firstSubmissions) ⇒
      if (firstSubmissions.nonEmpty) {
        val aquaUser = users(user_id)
        val username = aquaUser.username
        val earliestSubmission = firstSubmissions.minBy(dt => dt.getMillis)
        val userDateCreated = DateTime.parse(aquaUser.date_created)
        if (userDateCreated.isAfter(earliestSubmission)) {
          userService.updateUser(username, registered = Some(earliestSubmission))
        }
      }
    }
  }

  private def addOrgMembers(orgName: String, userNames: Set[String]) {
    orgService.getOrgOpt(orgName) match {
      case Some(org) => orgService.updateOrg(orgName, membersOpt = Some(org.members ++ userNames))
      case None => println(s"WARNING: '$orgName': organization not found")
    }
  }

  private def processOnts(byUri: Map[String, Map[String,VAquaOntology]]): Unit = {
    println("ONTS:")
    byUri.keys.toSeq.sorted foreach {uri =>
      val uriOnts = byUri(uri)

      val orgNameOpt = orgNameFromUri(uri)

      orgNameOpt foreach { orgName ⇒
        val members: Set[String] = uriOnts.values.map {o:VAquaOntology => users(o.user_id).username}.toSet
        addOrgMembers(orgName, members)
      }

      println(s"\t$uri")
      val firstSubmissionOpt = processOntUri(uri, orgNameOpt, uriOnts)

      for { orgName         ← orgNameOpt
            firstSubmission ← firstSubmissionOpt
      }
        orgFirstSubmissions(orgName) += firstSubmission
    }
  }

  private case class MyOntFileWriter(format: String, source: Source, version: String,
                                     orgNameOpt: Option[String]) extends OntFileWriter {
    override def write(destFile: File) {
      println(f"\t\twriting contents to ${destFile.getAbsolutePath}")
      val out = new PrintWriter(destFile)
      source.getLines foreach { line =>
        orgNameOpt match {
          case Some(orgName) ⇒
            // trick to convert contents from "versioned" to "unversioned": remove the
            // version piece from fragments that look like the versioned URI of the ontology:
            val stripped = line.replaceAll(s"/ont/$orgName/$version/", s"/ont/$orgName/")
            out.println(stripped)

          case None ⇒
            out.println(line)
        }
      }
      out.close()
      source.close()
    }
  }

  private def getOntFileWriter(uri: String, version: String, orgNameOpt: Option[String], ont: VAquaOntology): Option[OntFileWriter] = {
    ontFiles.values.find(_.ontology_version_id == ont.id) match {
      case Some(entity) =>
        val filename = entity.filename
        val format = ontUtil.storedFormat(filename.substring(filename.lastIndexOf(".") + 1))

        val source: Source = aquaUploadsDirOpt match {
          case Some(aquaUploadsDir) =>
            val fullPath = s"$aquaUploadsDir${ont.file_path}/$filename"
            println(f"\t\tLoading $fullPath")
            io.Source.fromFile(fullPath)

          case None =>
            val aquaOnt = aquaOntOpt.get
            println(f"\t\tLoading $uri version $version")
            io.Source.fromURL(s"$aquaOnt?uri=$uri&version=$version&form=$format")
        }
        Some(MyOntFileWriter(format, source, version, orgNameOpt))

      case None => None
    }
  }

  /**
    * Creates all submissions of a given ont URI.
    * Returns the time of the earliest submission
    */
  private def processOntUri(uri: String, orgNameOpt: Option[String], onts: Map[String,VAquaOntology])
  : Option[DateTime] = {

    if (onts.isEmpty) return None

    val byVersion = Map(onts.map{case(_,o) => (o.version_number, o)}.toArray: _*)
    val sortedVersions = byVersion.keys.toSeq.sorted

    val firstVersion = sortedVersions.head
    val firstOnt     = byVersion(firstVersion)
    val lastOnt      = byVersion(sortedVersions.last)
    val lastUserName = users(lastOnt.user_id).username

    userIdFirstSubmissions(lastOnt.user_id) += DateTime.parse(firstOnt.date_created)

    val ownerName = orgNameOpt.getOrElse(s"~$lastUserName")

    var firstSubmission: Option[DateTime] = None

    // status to propagate to newer versions not having an explicit version_status themselves
    var version_status: Option[String] = None

    // register entry (first submission)
    for(ontFileWriter <- getOntFileWriter(uri, firstVersion, orgNameOpt, firstOnt)) {
      val o = firstOnt
      val dateCreated = DateTime.parse(o.date_created)
      firstSubmission = Some(dateCreated)
      val versionVisibility = Some(getVersionVisibility(orgNameOpt, o.version_status))
      ontService.createOntology(
        o.uri, None, o.display_label, o.version_number,
        logOpt = None,
        versionVisibility = versionVisibility,
        versionStatus = o.version_status,
        o.date_created,
        userName = users(o.user_id).username,
        ownerName = ownerName,
        ontFileWriter,
        contact_name = o.contact_name
      )
      ontsCreated += 1

      version_status  = o.version_status
    }

    // register the other submissions
    sortedVersions.drop(1) foreach { version =>
      val o = byVersion(version)
      for (ontFileWriter <- getOntFileWriter(uri, version, orgNameOpt, o)) {
        if (o.version_status.isDefined) {
          version_status = o.version_status  // update to use here and propagate
        }
        val versionVisibility = Some(getVersionVisibility(orgNameOpt, version_status))
        val userName = users(o.user_id).username
        ontService.createOntologyVersion(
          o.uri, None, Some(o.display_label),
          userName = userName,
          o.version_number,
          logOpt = None,
          versionVisibility = versionVisibility,
          versionStatus = version_status,
          o.date_created, ontFileWriter,
          contact_name = o.contact_name)
      }
    }

    firstSubmission
  }

  private def getVersionVisibility(orgNameOpt: Option[String], version_status: Option[String]): String = {
    import OntVisibility._
    val statusOpt = version_status.map(_.toLowerCase.trim)
    statusOpt match {
      case Some("stable")  ⇒ public
      case Some("testing") ⇒ owner
      case _ ⇒
        orgNameOpt match {
          case Some("mmiorr-internal") ⇒ owner

          case Some(orgName) ⇒
            // traditional logic based on authority abbreviation
            if (orgName.matches(TESTING_AUTHORITIES_REGEX)) owner else public

          case None ⇒
            // no status, no authority abbreviation:
            public
        }
    }
  }
}

trait EntityLoader {
  type EntityType <: AquaEntity
  val allFieldNames: List[String]
  def apply(row: Node): EntityType

  def getXml(p: String): String = {
    println(s"getXml: p=$p")
    val source = scala.io.Source.fromURL(p, "ISO-8859-1")
    val xml = source.mkString
    source.close()
    xml
  }

  def loadEntities(p: String): Map[String, EntityType] = {
    val xml = getXml(p)
    val xmlIn = XML.loadString(xml)

    val gotHeaderCols = (xmlIn \\ "tr" \\ "th") map(_.text.trim)
    assert(gotHeaderCols == allFieldNames)

    val valueRows: NodeSeq = (xmlIn \\ "tr").drop(1)  // drop the header of course
    TreeMap(valueRows map apply map (u => (u.id, u)):_*)
  }

  /** returns the values but without the ones corresponding to dropFieldNames */
  def dropFields(header: Seq[String], values: Seq[String], dropFieldNames: Seq[String]): Seq[String] = {
    val z: Seq[(String,String)] = header zip values
    z.filterNot { case (h, _) => dropFieldNames.contains(h) } map (_._2)
  }

  /** fix the given dates so they can get parsed to DateTime */
  def fixDates(header: Seq[String], values: Seq[String], dates: Seq[String]): Seq[String] = {
    val z: Seq[(String,String)] = header zip values
    z.map { case (h, value) => if (dates.contains(h)) value.replaceAll(" ", "T") + "Z" else value }
  }
}

sealed abstract class AquaEntity {
  val id: String
}

case class AquaUser(id:           String,
                    username:     String,
                    password:     String,
                    email:        String,
                    firstname:    String,
                    lastname:     String,
                    phone:        String,
                    date_created: String,

                    map:          Map[String,String])  extends AquaEntity

object AquaUser extends EntityLoader {
  type EntityType = AquaUser

  val allFieldNames = List("id", "username", "password", "email", "firstname", "lastname", "phone", "date_created")
  val fieldNames: List[String] = allFieldNames

  def apply(row: Node): AquaUser = {
    val map: Map[String,String] = {
      val gotRowCols: Seq[String] = (row \ "td") map(_.text.trim)
      assert(gotRowCols.length == fieldNames.length)
      val values = fixDates(fieldNames, gotRowCols, Seq("date_created"))
      Map(fieldNames zip values: _*)
    }

    val List(id,
             username,
             password,
             email,
             firstname,
             lastname,
             phone,
             date_created
    ) = fieldNames.map(map(_))

    AquaUser(
      id,
      username,
      password,
      email,
      firstname,
      lastname,
      phone,
      date_created,

      // map for userService
      Map(
        "userName" -> username,
        "encPassword" -> password,
        "email" -> email,
        "firstName" -> firstname,
        "lastName" -> lastname,
        "phone" -> phone,
        "date_created" -> date_created
      )
    )
  }
}

case class VAquaOntology(id:                String,
                         ontology_id:       String,
                         user_id:           String,

                         //internal_version_number

                         version_number:    String,
                         version_status:    Option[String],
                         file_path:         String,

                         // is_remote
                         // is_reviewed

                         status_id:         String,
                         date_created:      String,

                         // date_released:     String,

                         // obo_foundry_id
                         // is_manual

                         display_label:     String,

                         // format
                         contact_name:      Option[String],

                         // contact_email
                         // homepage
                         // documentation
                         // publication

                         uri:               String,

                         // coding_scheme
                         //is_foundry

                         map:               Map[String,String]) extends AquaEntity

object VAquaOntology extends EntityLoader {
  type EntityType = VAquaOntology

  val allFieldNames = List(
    "id",
    "ontology_id",
    "user_id",
    "internal_version_number",
    "version_number",
    "version_status",
    "file_path",
    "is_remote",
    "is_reviewed",
    "status_id",
    "date_created",
    "date_released",
    "obo_foundry_id",
    "is_manual",
    "display_label",
    "format",
    "contact_name",
    "contact_email",
    "homepage",
    "documentation",
    "publication",
    "urn",
    "coding_scheme",
    "is_foundry"
  )

  val dropFieldNames = List(
    "internal_version_number",
    "is_remote",
    "is_reviewed",
    "date_released",
    "obo_foundry_id",
    "is_manual",
    "format",
    "contact_email",
    "homepage",
    "documentation",
    "publication",
    "coding_scheme",
    "is_foundry"
  )

  val fieldNames: List[String] = allFieldNames.filterNot(dropFieldNames.contains)

  def apply(row: Node): VAquaOntology = {
    val map: Map[String,String] = {
      val gotRowCols: Seq[String] = (row \ "td") map(_.text.trim)
      assert(gotRowCols.length == allFieldNames.length)
      val values = dropFields(allFieldNames, gotRowCols, dropFieldNames)
      val valuesFixed = fixDates(fieldNames, values, Seq("date_created"))
      Map(fieldNames zip valuesFixed: _*)
    }

    val List(id,
             ontology_id,
             user_id,
             version_number,
             version_status,
             file_path,
             status_id,
             date_created,
             display_label,
             contact_name,
             urn
    ) = fieldNames.map(map(_))

    // remove version_number for the uri:
    val uri = urn.replace(s"/$version_number/", "/")

    VAquaOntology(
      id,
      ontology_id,
      user_id,
      version_number,
      if (version_status == "null") None else Some(version_status),
      file_path,
      status_id,
      date_created,
      display_label,
      if (contact_name.trim == "") None else Some(contact_name),
      uri,

      // map for ontService
      Map(
        "uri" -> uri
      )
    )
  }

  override def getXml(p: String): String = {
    println(s"getXml: p=$p")
    val source = scala.io.Source.fromURL(p, "ISO-8859-1")
    val xml = source.getLines().map(_.replaceAll(" & ", " &amp; ")).mkString
    source.close()
    xml
  }
}

case class AquaOntologyFile(id:                   String,
                            ontology_version_id:  String,
                            filename:             String)  extends AquaEntity

object AquaOntologyFile extends EntityLoader {
  type EntityType = AquaOntologyFile

  val allFieldNames = List("id", "ontology_version_id", "filename")
  val fieldNames: List[String] = allFieldNames

  def apply(row: Node): AquaOntologyFile = {
    val map: Map[String,String] = {
      val gotRowCols: Seq[String] = (row \ "td") map(_.text.trim)
      assert(gotRowCols.length == fieldNames.length)
      val values = fixDates(fieldNames, gotRowCols, Seq("date_created"))
      Map(fieldNames zip values: _*)
    }

    val List(id, ontology_version_id, filename) = fieldNames.map(map(_))

    AquaOntologyFile(id, ontology_version_id, filename)
  }
}



