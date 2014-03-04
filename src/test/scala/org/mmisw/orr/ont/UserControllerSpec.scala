package org.mmisw.orr.ont

import org.json4s._
import org.json4s.native.JsonMethods._
import org.scalatra.test.specs2._


class UserControllerSpec extends MutableScalatraSpec {
  implicit val formats = org.json4s.DefaultFormats
  import org.json4s.JsonDSL._

  implicit val setup = new Setup("/etc/orront.conf", testing = true)
  addServlet(new UserController, "/*")

  val userName = s"random:${java.util.UUID.randomUUID().toString}"
  val password = "mypassword"
  val map = Map(
    "userName"  -> userName,
    "firstName" -> "myFirstName",
    "lastName"  -> "myLastName",
    "password"  -> password)

  "GET /" should {
    "return status 200" in {
      get("/") {
        status must_== 200
      }
    }
  }

  sequential

  "POST new user" should {
    "return status 200" in {

      post("/", body = pretty(render(map)),
           headers=Map("content-type" -> "application/json")) {

        status must_== 200

        val json = parse(body)
        val map = json.extract[Map[String, String]]
        map.get("userName") must_== Some(userName)
      }
    }
  }

  "POST check correct password" should {
    "return status 200" in {
      post("/chkpw", body = pretty(render(Map("userName" -> userName, "password" -> password))),
        headers=Map("content-type" -> "application/json")) {
        status must_== 200
      }
    }
  }

  "POST check wrong password" should {
    "return status 401" in {
      post("/chkpw", body = pretty(render(Map("userName" -> userName, "password" -> "wrong"))),
        headers=Map("content-type" -> "application/json")) {
        status must_== 401
      }
    }
  }

  "PUT update user" should {
    "return status 200" in {
      put("/", body = pretty(render(Map("userName" -> userName, "firstName" -> "updated.firstName"))),
        headers=Map("content-type" -> "application/json")) {
        status must_== 200
      }
    }
  }

  "DELETE user" should {
    "return status 200" in {
      delete("/", Map("userName" -> userName)) {
        status must_== 200
      }
    }
  }

}
