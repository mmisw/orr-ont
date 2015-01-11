package org.mmisw.orr.ont.x

import java.io.File

import org.joda.time.DateTime
import org.mmisw.orr.ont.Setup
import org.mmisw.orr.ont.service.{OrgService, OntContents, OntService, UserService}

import scala.collection.immutable.TreeMap
import scala.xml.{NodeSeq, Node, XML}


/**
 * Preliminary code to import data from previous database.
 */
object AquaImporter extends App {

  implicit val setup = new Setup("/etc/orront.conf")
  val userService = new UserService
  val orgService = new OrgService
  val ontService = new OntService

  val users = AquaUser.loadEntities("src/main/resources/ncbo_user.html")
  //processUsers(users)

  val onts = VAquaOntology.loadEntities("src/main/resources/v_ncbo_ontology.html")

  println(s"Loaded ${onts.size} ontologies")

  val userOnts = TreeMap(onts.groupBy(_._2.user_id).toArray: _*)

  println("USER ONTOLOGIES:")
//  userOnts foreach { case (user_id, elems) =>
//    println(f"    user $user_id - ${users(user_id).username}%-20s - ${elems.size}%3d submissions")
//  }

/*
  println("ALL ONTOLOGIES/VERSIONS:")
  onts.values foreach(o => println(o))
*/

  val byUri = onts.groupBy(_._2.uri)

  val uriVersionCounter = byUri.toArray.map{case (uri, versions) => (uri, versions.size)}.sortBy(-_._2)

  println(s"${uriVersionCounter.size} ONTOLOGY URIs:")
  uriVersionCounter foreach { case (uri, versionCounter) =>
    println(f"    $uri%-60s - $versionCounter%3d versions")
  }

  val orgName = "mmitest"
  val uri = "http://mmisw.org/ont/mmitest/parenthesis"
  val uriOnts = byUri(uri)
  if (!orgService.existsOrg(orgName)) {
    orgService.createOrg(orgName, orgName, List("carueda"))
  }

  processOntUri(uri, orgName, uriOnts)

  setup.destroy()

  ///////////////////////////////////////////////////////////////////////////

  /** creates/updates the given users */
  private def processUsers(users: Map[String,AquaUser]) = {
    var (created, updated) = (0, 0)
    users foreach { case (id,u) =>
      if (u.username != "admin") {
        if (userService.existsUser(u.username)) {
          println(f"updating ${u.id}%-5s - ${u.username}%-20s - ${u.email}")
          userService.updateUser(u.username, u.map)  // don't set any 'updated'
          updated += 1
        }
        else {
          println(f"creating ${u.id}%-5s - ${u.username}%-20s - ${u.email}")
          userService.createUser(
            u.username, u.email, Some(u.phone), u.firstname, u.lastname,
            Right(u.password), None, DateTime.parse(u.date_created))
          created += 1
        }
      }
    }
    println(s"==> ${users.size} users processed: $created created, $updated updated")
  }

  /** creates/updates the submissions of a given ont URI */
  private def processOntUri(uri: String, orgName: String, onts: Map[String,VAquaOntology]) = {

    val contents: OntContents = new OntContents { override def write(destFile: File) { println(s"(fake contents write to ${destFile.getAbsolutePath}")} }
    val format = "TODO-format"

    val byVersion = Map(onts.map{case(_,o) => (o.version_number, o)}.toArray: _*)
    val sortedVersions = byVersion.keys.toSeq.sorted
    println(s"Processing ${byVersion.size} submissions of URI=$uri.  versions=$sortedVersions")

    // register entry (first submission)
    sortedVersions.headOption foreach { version =>
      val o = byVersion(version)
      println(f"registering ontology version=${o.version_number} - '${o.display_label}'")
      ontService.createOntology(
        o.uri, o.display_label, o.version_number, o.date_created,
        users.get(o.user_id).get.username, orgName,
        contents, format)
    }

    // register the other submissions
    sortedVersions.drop(1) foreach { version =>
      val o = byVersion(version)
      println(f"registering ontology version=$version - '${o.display_label}'")
      val date = o.date_created

      ontService.createOntologyVersion(
        o.uri, Some(o.display_label), users.get(o.user_id).get.username,
        o.version_number, o.date_created, contents, format
        )
    }
    println(s"==> ${byVersion.size} submissions processed")
  }

}

trait EntityLoader {
  type EntityType <: AquaEntity
  val allFieldNames: List[String]
  def apply(row: Node): EntityType

  def getXml(p: String) = {
    val source = scala.io.Source.fromFile(new File(p))
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
                         version_status:    String,
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
                         contact_name:      String,

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
      if (version_status == "null") "" else version_status,
      file_path,
      status_id,
      date_created,
      display_label,
      contact_name,
      uri,

      // map for ontService
      Map(
        "uri" -> uri // more TODO
      )
    )
  }

  override def getXml(p: String) = {
    val source = scala.io.Source.fromFile(new File(p))
    val xml = source.getLines().map(_.replaceAll(" & ", " &amp; ")).mkString
    source.close()
    xml
  }
}


