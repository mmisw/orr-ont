package org.mmisw.orr.ont

import org.json4s._
import org.json4s.native.JsonMethods._
import org.scalatra.test.specs2._


class OrgControllerSpec extends MutableScalatraSpec with BaseSpec {
  import org.json4s.JsonDSL._

  addServlet(new OrgController, "/*")

  val orgName = s"org-${java.util.UUID.randomUUID().toString}"
  val map =
    ("orgName"    -> orgName) ~
    ("name"       -> "some organization") ~
    ("ontUri"     -> "ontUri") ~
    ("members"    -> Seq("member1", "member2")
  )

  "GET /" should {
    "return status 200" in {
      get("/") {
        status must_== 200
      }
    }
  }

  sequential

  "POST new organization" should {
    "return status 401 with no credentials" in {
      val headers = Map("content-type" -> "application/json")
      post("/", body = pretty(render(map)), headers = headers) {
        status must_== 401
      }
    }
    "return status 200 with admin credentials" in {
      val headers = Map("content-type" -> "application/json", "Authorization" -> adminCredentials)
      post("/", body = pretty(render(map)), headers = headers) {
        status must_== 200
        val res = parse(body).extract[OrgResult]
        res.orgName must_== orgName
      }
    }
  }

  "PUT update organization" should {
    "return status 401 with no credentials" in {
      val headers = Map("content-type" -> "application/json")
      put(s"/$orgName", body = pretty(render("ontUri" -> "updated.ontUri")),
        headers = headers) {
        status must_== 401
      }
    }
    "return status 200 with admin credentials" in {
      val headers = Map("content-type" -> "application/json", "Authorization" -> adminCredentials)
      put(s"/$orgName", body = pretty(render("ontUri" -> "updated.ontUri")),
        headers = headers) {
        status must_== 200
        val res = parse(body).extract[OrgResult]
        res.orgName must_== orgName
      }
    }
  }

  "DELETE organization" should {
    "return status 401 with no credentials" in {
      delete(s"/$orgName", headers = Map.empty) {
        status must_== 401
      }
    }
    "return status 200 with admin credentials" in {
      val headers = Map("Authorization" -> adminCredentials)
      delete(s"/$orgName", headers = headers) {
        status must_== 200
        val res = parse(body).extract[OrgResult]
        res.orgName must_== orgName
      }
    }
  }

}
