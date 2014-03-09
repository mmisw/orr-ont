package org.mmisw.orr.ont

import org.json4s._
import org.json4s.native.JsonMethods._
import org.scalatra.test.specs2._


class AuthorityControllerSpec extends MutableScalatraSpec {
  implicit val formats = org.json4s.DefaultFormats
  com.mongodb.casbah.commons.conversions.scala.RegisterJodaTimeConversionHelpers()
  import org.json4s.JsonDSL._

  implicit val setup = new Setup("/etc/orront.conf", testing = true)
  addServlet(new AuthorityController, "/*")

  val authName = s"random.${java.util.UUID.randomUUID().toString}"
  val map =
    ("authName"   -> authName) ~
    ("name"       -> "some authority") ~
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

  "POST new authority" should {
    "return status 200" in {

      post("/", body = pretty(render(map)),
           headers = Map("content-type" -> "application/json")) {

        status must_== 200
        val res = parse(body).extract[AuthorityResult]
        res.authName must_== authName
      }
    }
  }

  "PUT update authority" should {
    "return status 200" in {
      put("/", body = pretty(render(("authName" -> authName) ~ ("ontUri" -> "updated.ontUri"))),
        headers = Map("content-type" -> "application/json")) {
        status must_== 200
        val res = parse(body).extract[AuthorityResult]
        res.authName must_== authName
      }
    }
  }

  "DELETE authority" should {
    "return status 200" in {
      delete("/", Map("authName" -> authName)) {
        status must_== 200
        val res = parse(body).extract[AuthorityResult]
        res.authName must_== authName
      }
    }
  }

}
