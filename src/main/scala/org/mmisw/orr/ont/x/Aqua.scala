package org.mmisw.orr.ont.x

import java.io.File

import org.joda.time.DateTime
import org.mmisw.orr.ont.Setup
import org.mmisw.orr.ont.service.UserService

import scala.collection.immutable.TreeMap
import scala.xml.{NodeSeq, Node, XML}


/**
 * Preliminary code to import data from previous database.
 */
object Aqua extends App {

  implicit val setup = new Setup("/etc/orront.conf")
  val userService = new UserService

//  val users = NcboUser.getUsers
  val users = NcboUser.loadEntities("src/main/resources/ncbo_user.html")
  //processUsers(users)

  val onts = VNcboOntology.loadEntities("src/main/resources/v_ncbo_ontology.html")

  println(s"Loaded ${onts.size} ontologies")

  val userOnts = TreeMap(onts.groupBy(_._2.user_id).toArray: _*)

  userOnts foreach { case (user_id, elems) =>
    println(f"    user $user_id - ${users(user_id).username}%-20s - ${elems.size}%3d submissions")
  }

  setup.destroy()

  ///////////////////////////////////////////////////////////////////////////

  /** creates/updates the given users */
  private def processUsers(users: Map[String,NcboUser]) = {
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

  trait EntityLoader {
    type EntityType <: NcboEntity
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
  }

  sealed abstract class NcboEntity {
    val id: String
  }
  case class NcboUser(id:           String,
                      username:     String,
                      password:     String,
                      email:        String,
                      firstname:    String,
                      lastname:     String,
                      phone:        String,
                      date_created: String,

                      map:          Map[String,String])  extends NcboEntity

  object NcboUser extends EntityLoader {
    type EntityType = NcboUser

    val allFieldNames = List("id", "username", "password", "email", "firstname", "lastname", "phone", "date_created")
    val fieldNames = allFieldNames

    def apply(row: Node): NcboUser = {
      val map: Map[String,String] = {
        val gotRowCols: Seq[String] = (row \ "td") map(_.text.trim)
        assert(gotRowCols.length == fieldNames.length)
        val values = fixDates(fieldNames, gotRowCols, "date_created")
        Map(fieldNames zip values: _*)
      }

      val List(id, username, password, email, firstname, lastname, phone, date_created) = fieldNames.map(map.get(_).get)

      NcboUser(
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

    def getUsers: Map[String,NcboUser] = {

      val xmlIn = XML.loadFile("src/main/resources/ncbo_user.html")

      val gotHeaderCols = (xmlIn \\ "tr" \\ "th") map(_.text.trim)
      assert(gotHeaderCols == fieldNames)

      val valueRows: NodeSeq = (xmlIn \\ "tr").drop(1)  // drop the header of course
      TreeMap(valueRows map NcboUser.apply map (u => (u.id, u)):_*)
    }
  }

  case class VNcboOntology(id:                String,
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

                           urn:               String,

                           // coding_scheme
                           //is_foundry

                           map:               Map[String,String]) extends NcboEntity

  object VNcboOntology extends EntityLoader {
    type EntityType = VNcboOntology

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

    val ignoreFieldNames = List(
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

    val fieldNames = allFieldNames.filterNot(ignoreFieldNames.contains(_))

    def apply(row: Node): VNcboOntology = {
      val map: Map[String,String] = {
        val gotRowCols: Seq[String] = (row \ "td") map(_.text.trim)
        assert(gotRowCols.length == allFieldNames.length)
        val values = ignore(allFieldNames, gotRowCols, ignoreFieldNames:_*)
        val valuesFixed = fixDates(allFieldNames, values, "date_created")
        Map(fieldNames zip valuesFixed: _*)
      }

      val List(
        id,
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

      VNcboOntology(
        id,
        ontology_id,
        user_id,
        version_number,
        version_status,
        file_path,
        status_id,
        date_created,
        display_label,
        contact_name,
        urn,

        // map for ontService
        Map(
          "uri" -> urn // more TODO
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

  /** returns the vals but without the values corresponding to ignoreCols */
  private def ignore(header: Seq[String], vals: Seq[String], ignoreCols: String*): Seq[String] = {
    val z: Seq[(String,String)] = header zip vals
    z.filterNot { case (h, value) => ignoreCols.contains(h) } map (_._2)
  }

  /** fix the given dates so they can get parsed to DateTime */
  private def fixDates(header: Seq[String], vals: Seq[String], dates: String*): Seq[String] = {
    val z: Seq[(String,String)] = header zip vals
    z.map { case (h, value) => if (dates.contains(h)) value.replaceAll(" ", "T") + "Z" else value }
  }

}


