package org.mmisw.orr.ont

import org.json4s._
import org.json4s.native.JsonMethods._
import org.scalatra.test.specs2._


class OrgControllerSpec extends MutableScalatraSpec {
  implicit val formats = org.json4s.DefaultFormats
  com.mongodb.casbah.commons.conversions.scala.RegisterJodaTimeConversionHelpers()
  import org.json4s.JsonDSL._

  implicit val setup = new Setup("/etc/orront.conf", testing = true)
  addServlet(new OrgController, "/*")

  val orgName = s"random.${java.util.UUID.randomUUID().toString}"
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
    "return status 200" in {

      post("/", body = pretty(render(map)),
           headers = Map("content-type" -> "application/json")) {

        status must_== 200
        val res = parse(body).extract[OrgResult]
        res.orgName must_== orgName
      }
    }
  }

  "PUT update organization" should {
    "return status 200" in {
      put(s"/$orgName", body = pretty(render("ontUri" -> "updated.ontUri")),
        headers = Map("content-type" -> "application/json")) {
        status must_== 200
        val res = parse(body).extract[OrgResult]
        res.orgName must_== orgName
      }
    }
  }

  "DELETE organization" should {
    "return status 200" in {
      delete(s"/$orgName", Map.empty) {
        status must_== 200
        val res = parse(body).extract[OrgResult]
        res.orgName must_== orgName
      }
    }
  }

}
