package org.mmisw.orr.ont

import org.json4s._
import org.json4s.native.JsonMethods._
import org.scalatra.test.specs2._


class AuthorityControllerSpec extends MutableScalatraSpec {
  implicit val formats = org.json4s.DefaultFormats
  import org.json4s.JsonDSL._

  implicit val setup = new Setup("/etc/orront.conf", testing = true)
  addServlet(new AuthorityController, "/*")

  val shortName = s"random.${java.util.UUID.randomUUID().toString}"
  val map = Map(
    "shortName"  -> shortName,
    "ontUri"     -> "ontUri")

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

        val json = parse(body)
        val map = json.extract[Map[String, String]]
        map.get("shortName") must_== Some(shortName)
      }
    }
  }

  "PUT update authority" should {
    "return status 200" in {
      put("/", body = pretty(render(Map("shortName" -> shortName, "ontUri" -> "updated.ontUri"))),
        headers = Map("content-type" -> "application/json")) {
        status must_== 200
      }
    }
  }

  "DELETE authority" should {
    "return status 200" in {
      delete("/", Map("shortName" -> shortName)) {
        status must_== 200
      }
    }
  }

}
