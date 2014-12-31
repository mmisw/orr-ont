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

  val userName = s"user-${java.util.UUID.randomUUID().toString}"
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

  sequential

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

  "POST new user" should {
    "return status 200" in {

      val headers = Map("content-type" -> "application/json", "Authorization" -> adminCredentials)

      post("/", body = pretty(render(map)), headers = headers) {
        println(s"body = $body")
        status must_== 200
        val res = parse(body).extract[UserResult]
        res.userName must_== userName
      }
    }
  }

  "POST check password" should {
    "return status 200 with correct password" in {
      post("/chkpw", body = pretty(render(Map("userName" -> userName, "password" -> password))),
        headers=Map("content-type" -> "application/json")) {
        status must_== 200
      }
    }
    "return status 401 with bad password" in {
      post("/chkpw", body = pretty(render(Map("userName" -> userName, "password" -> "wrong"))),
        headers=Map("content-type" -> "application/json")) {
        status must_== 401
      }
    }
  }

  "PUT update user" should {
    val body = pretty(render(Map("userName" -> userName, "firstName" -> "updated.firstName")))

    "return status 401 with no credentials" in {
      val headers = Map("content-type" -> "application/json")
      put("/", body = body, headers = headers) {
        status must_== 401
      }
    }
    "return status 200 with user credentials" in {
      val headers = Map("content-type" -> "application/json", "Authorization" -> credentials)
      put("/", body = body, headers = headers) {
        status must_== 200
        val res = parse(body).extract[UserResult]
        res.userName must_== userName
      }
    }
    "return status 200 with admin credentials" in {
      val headers = Map("content-type" -> "application/json", "Authorization" -> adminCredentials)
      put("/", body = body, headers = headers) {
        status must_== 200
        val res = parse(body).extract[UserResult]
        res.userName must_== userName
      }
    }
  }

  "DELETE user" should {
    "return status 200" in {
      val headers = Map("Authorization" -> adminCredentials)
      delete("/", Map("userName" -> userName), headers) {
        status must_== 200
        val res = parse(body).extract[UserResult]
        res.userName must_== userName
      }
    }
  }

  "DELETE admin" should {
    "return status 401 with no credentials" in {
      delete("/", Map("userName" -> "admin")) {
        status must_== 401
      }
    }
    "return status 401 with non-admin credentials" in {
      val headers = Map("Authorization" -> credentials)
      delete("/", Map("userName" -> "admin"), headers) {
        status must_== 401
      }
    }
    "return status 200" in {
      val headers = Map("Authorization" -> adminCredentials)
      delete("/", Map("userName" -> "admin"), headers) {
        status must_== 200
        val res = parse(body).extract[UserResult]
        res.userName must_== "admin"
      }
    }
  }

}
