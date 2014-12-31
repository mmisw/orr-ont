package org.mmisw.orr.ont

import org.json4s._
import org.json4s.native.JsonMethods._
import org.scalatra.test.specs2._


class UserControllerSpec extends MutableScalatraSpec with BaseSpec {
  import org.json4s.JsonDSL._

  addServlet(new UserController, "/*")

  "GET /admin" should {
    "return status 200" in {
      get("/admin") {
        status must_== 200
      }
    }
  }

  val userName = s"random:${java.util.UUID.randomUUID().toString}"
  val password = "mypassword"
  val credentials = basicCredentials(userName, password)

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

  "POST /!/testAuth" should {
    "return status 401 with no credentials" in {
      post("/!/testAuth") {
        status must_== 401
      }
    }
    "return status 401 with non-admin credentials" in {
      post("/!/testAuth", body = "", headers = Map("Authorization" -> credentials)) {
        status must_== 401
      }
    }
    "return status 200 with admin credentials" in {
      post("/!/testAuth", body = "", headers = Map("Authorization" -> adminCredentials)) {
        status must_== 200
      }
    }
  }

  sequential

  "POST new user" should {
    "return status 200" in {

      post("/", body = pretty(render(map)),
           headers=Map("content-type" -> "application/json")) {

        println(s"body = $body")
        status must_== 200
        val res = parse(body).extract[UserResult]
        res.userName must_== userName
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
        val res = parse(body).extract[UserResult]
        res.userName must_== userName
      }
    }
  }

  "DELETE user" should {
    "return status 200" in {
      delete("/", Map("userName" -> userName)) {
        status must_== 200
        val res = parse(body).extract[UserResult]
        res.userName must_== userName
      }
    }
  }

  "DELETE admin (in tests)" should {
    "return status 200" in {
      delete("/", Map("userName" -> "admin")) {
        status must_== 200
        val res = parse(body).extract[UserResult]
        res.userName must_== "admin"
      }
    }
  }

}
