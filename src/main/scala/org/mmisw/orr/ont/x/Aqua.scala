package org.mmisw.orr.ont.x

import org.joda.time.DateTime
import org.mmisw.orr.ont.Setup
import org.mmisw.orr.ont.service.UserService

/**
 * Preliminary code to import data from previous database.
 */
object Aqua extends App {

  def getUsers: Seq[AquaUser] = {
    val xmlIn = scala.xml.XML.loadFile("src/main/resources/select_from_ncbo_user.html")
    val headerCols = (xmlIn \\ "tr" \\ "th") map(_.text.trim)
    println(s"headerCols=$headerCols")
    assert(headerCols.length == 8)
    (xmlIn \\ "tr").drop(1) map { row =>
      val rowCols = (row \ "td") map(_.text.trim)
      assert(rowCols.length == 8)
      AquaUser(Map(headerCols zip rowCols: _*))
    }
  }

  implicit val setup = new Setup("/etc/orront.conf")
  val userService = new UserService

  val users = getUsers

  users foreach { u =>
    println(s"creating ${u.username} - ${u.email} - ${u.firstName} ${u.firstName}")
    userService.createUser(u.username, Some(u.email), u.phone, u.firstName, u.lastName, u.password, None, u.date_created)
  }
  println(s"${users.length} users created.")

  setup.destroy()
}


case class AquaUser(username:  String,
                    password:  String,
                    email:     String,
                    firstName: String,
                    lastName:  String,
                    phone:     Option[String],
                    date_created: DateTime)

object AquaUser {
  def apply(map: Map[String,String]): AquaUser = {

    // Is it UTC? if so, append a Z.
    val date_created = DateTime.parse(map.get("date_created").get.replaceAll(" ", "T"))
    AquaUser(
      map.get("username").get,
      map.get("password").get,
      map.get("email").get,
      map.get("firstname").get,
      map.get("lastname").get,
      map.get("phone"),
      date_created
    )
  }
}
