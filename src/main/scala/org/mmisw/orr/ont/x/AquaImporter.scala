package org.mmisw.orr.ont.x

import java.io.{PrintWriter, File}

import org.joda.time.DateTime
import org.mmisw.orr.ont.Setup
import org.mmisw.orr.ont.service.{OrgService, OntFileWriter, OntService, UserService}
import org.mmisw.orr.ont.swld.ontUtil

import scala.collection.immutable.TreeMap
import scala.io.Source
import scala.xml.{NodeSeq, Node, XML}


/**
 * Imports data from previous database.
 */
object AquaImporter extends App {

  implicit val setup = new Setup("/etc/orront.conf")
  val userService = new UserService
  val orgService = new OrgService
  val ontService = new OntService

  userService.deleteAll()
  orgService.deleteAll()
  ontService.deleteAll()

  val importConfig = setup.config.getConfig("import")
  val users    = AquaUser.loadEntities(importConfig.getString("aquaUsers"))
  val onts     = VAquaOntology.loadEntities(importConfig.getString("aquaOnts"))
  val ontFiles = AquaOntologyFile.loadEntities(importConfig.getString("aquaOntFiles"))
  val aquaUploadsDirOpt = Option(if (importConfig.hasPath("aquaUploadsDir")) importConfig.getString("aquaUploadsDir") else null)
  val aquaOnt = importConfig.getString("aquaOnt")

  val byUri = onts.groupBy(_._2.uri)
  val orgNames = getOrgNames(byUri.keys)
  val orgFirstSubmissions = Map(orgNames.map(o => (o, scala.collection.mutable.HashSet[DateTime]())).toArray: _*)

  println(s"Loaded: users=${users.size} onts=${onts.size}")
  println(s"Extracted ${orgNames.size} orgNames")

  processUsers(users)
  processOrgs(orgNames)
  processOnts(byUri)
  postProcessOrgs()

  setup.destroy()

  ///////////////////////////////////////////////////////////////////////////

  /** creates/updates the given users */
  private def processUsers(users: Map[String,AquaUser]) = {
    println("USERS:")
    users foreach { case (id,u) =>
      if (u.username != "admin") {
        println(f"\t${u.username}%-20s - ${u.email}")
        if (userService.existsUser(u.username)) {
          userService.updateUser(u.username, u.map)  // don't set any 'updated'
        }
        else {
          userService.createUser(
            u.username, u.email, Some(u.phone), u.firstname, u.lastname,
            Right(u.password), None, DateTime.parse(u.date_created))
        }
      }
    }
  }

  private def getOrgNameFromUri(uri: String): String = {
    val re = """http://mmisw\.org/ont/([^/]+)/.*""".r
    uri match {
      case re(orgName) => orgName
      case _ => "-"
    }
  }

  private def getOrgNames(uris: Iterable[String]): Seq[String] = {
    val re = """http://mmisw\.org/ont/([^/]+)/.*""".r
    uris.foldLeft(Set[String]()) { case (a, u) => a + getOrgNameFromUri(u) }.toSeq.sorted
  }

  private def processOrgs(orgNames: Seq[String]) {
    println("ORGS:")
    orgNames foreach { orgName =>
      if (!orgService.existsOrg(orgName)) {
        println(s"\t$orgName")
        orgService.createOrg(orgName, orgName)
      }
    }
  }

  /**
   * Resets the registration date of each org to reflect the earliest
   * ont submission against that org.
   */
  private def postProcessOrgs(): Unit = {
    orgFirstSubmissions foreach {
      case (orgName, firstSubmissions) if firstSubmissions.nonEmpty =>
        val earliest = firstSubmissions.minBy(dt => dt.getMillis)
        orgService.updateOrg(orgName, registered = Some(earliest))
    }
  }

  private def addOrgMembers(orgName: String, userNames: Set[String]) {
    orgService.getOrgOpt(orgName) match {
      case Some(org) => orgService.updateOrg(orgName, Some(org.members.toSet ++ userNames))
      case None => println(s"WARNING: '$orgName': organization not found")
    }
  }

  private def processOnts(byUri: Map[String, Map[String,VAquaOntology]]): Unit = {
    println("ONTS:")
    byUri.keys.toSeq.sorted foreach {uri =>
      val uriOnts = byUri(uri)
      val orgName = getOrgNameFromUri(uri)
      val members = uriOnts.values.map {o:VAquaOntology => users.get(o.user_id).get.username}.toSet
      addOrgMembers(orgName, members)

      println(s"\t$uri")
      processOntUri(uri, orgName, uriOnts) foreach (firstSubmission => orgFirstSubmissions(orgName) += firstSubmission)
    }
  }

  private case class MyOntFileWriter(format: String, source: Source, version: String, orgName: String) extends OntFileWriter {
    override def write(destFile: File) {
      println(f"\t\twriting contents to ${destFile.getAbsolutePath}")
      val out = new PrintWriter(destFile)
      source.getLines foreach { line =>
        // trick to convert contents from "versioned" to "unversioned": remove the
        // version piece from fragments that look like the versioned URI of the ontology:
        val stripped = line.replaceAll(s"/ont/$orgName/$version/", s"/ont/$orgName/")
        out.println(stripped)
      }
      out.close()
      source.close()
    }
  }

  private def getOntFileWriter(uri: String, version: String, orgName: String, ont: VAquaOntology): Option[OntFileWriter] = {
    ontFiles.values.find(_.ontology_version_id == ont.id).headOption match {
      case Some(entity) =>
        val filename = entity.filename
        val format = ontUtil.storedFormat(filename.substring(filename.lastIndexOf(".") + 1))

        val source: Source = aquaUploadsDirOpt match {
          case Some(aquaUploadsDir) =>
            val fullPath = s"$aquaUploadsDir${ont.file_path}/$filename"
            println(f"\t\tLoading $fullPath")
            io.Source.fromFile(fullPath)

          case None =>
            println(f"\t\tLoading $uri version $version")
            io.Source.fromURL(s"$aquaOnt?uri=$uri&version=$version&form=$format")
        }
        Some(MyOntFileWriter(format, source, version, orgName))

      case None => None
    }
  }

  /**
   * Creates all submissions of a given ont URI.
   * Returns the time of the earliest submission
   */
  private def processOntUri(uri: String, orgName: String, onts: Map[String,VAquaOntology]): Option[DateTime] = {

    val byVersion = Map(onts.map{case(_,o) => (o.version_number, o)}.toArray: _*)
    val sortedVersions = byVersion.keys.toSeq.sorted

    var firstSubmission: Option[DateTime] = None

    // register entry (first submission)
    sortedVersions.headOption foreach { version =>
      val o = byVersion(version)
      for(ontFileWriter <- getOntFileWriter(uri, version, orgName, o)) {
        val dateCreated = DateTime.parse(o.date_created)
        firstSubmission = Some(dateCreated)
        ontService.createOntology(
          o.uri, o.display_label, o.version_number, o.version_status,
          o.contact_name,
          o.date_created, users.get(o.user_id).get.username, orgName,
          ontFileWriter)
      }
    }

    // register the other submissions
    sortedVersions.drop(1) foreach { version =>
      val o = byVersion(version)
      for (ontFileWriter <- getOntFileWriter(uri, version, orgName, o)) {
        ontService.createOntologyVersion(
          o.uri, Some(o.display_label), users.get(o.user_id).get.username,
          o.version_number, o.version_status, o.contact_name,
          o.date_created, ontFileWriter)
      }
    }

    firstSubmission
  }
}

trait EntityLoader {
  type EntityType <: AquaEntity
  val allFieldNames: List[String]
  def apply(row: Node): EntityType

  def getXml(p: String) = {
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
    z.filterNot { case (h, value) => dropFieldNames.contains(h) } map (_._2)
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
  val fieldNames = allFieldNames

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
    ) = fieldNames.map(map.get(_).get)

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

  val fieldNames = allFieldNames.filterNot(dropFieldNames.contains)

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
    ) = fieldNames.map(map.get(_).get)

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
        "uri" -> uri // more TODO
      )
    )
  }

  override def getXml(p: String) = {
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
  val fieldNames = allFieldNames

  def apply(row: Node): AquaOntologyFile = {
    val map: Map[String,String] = {
      val gotRowCols: Seq[String] = (row \ "td") map(_.text.trim)
      assert(gotRowCols.length == fieldNames.length)
      val values = fixDates(fieldNames, gotRowCols, Seq("date_created"))
      Map(fieldNames zip values: _*)
    }

    val List(id, ontology_version_id, filename) = fieldNames.map(map.get(_).get)

    AquaOntologyFile(id, ontology_version_id, filename)
  }
}



