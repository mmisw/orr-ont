package org.mmisw.orr.ont

import org.json4s._
import org.json4s.native.JsonMethods._
import org.scalatra.test.specs2._


class OntServletSpec extends MutableScalatraSpec {
  implicit val formats = org.json4s.DefaultFormats
  import org.json4s.JsonDSL._

  implicit val setup = new Setup("/etc/orront.conf")
  addServlet(new OntServlet, "/*")

  "GET /" should {
    "return status 200" in {
      get("/") {
        status must_== 200
      }
    }
  }

  "POST/PUT/DELETE" should {
    "work" in {

      val uri  = s"random:${java.util.UUID.randomUUID().toString}"
      val name = "some.name"

      // post new artifact
      post("/", body=pretty(render(Map("uri" -> uri, "name" -> name))),
           headers=Map("content-type" -> "application/json")) {

        status must_== 200

        val json = parse(body)
        val map = json.extract[Map[String, String]]
        map.get("uri") must_== Some(uri)
      }

      // update:
      put("/", body=pretty(render(Map("uri" -> uri, "name" -> "updated.name"))),
          headers=Map("content-type" -> "application/json")) {
        status must_== 200
      }

      // delete:
      submit("DELETE", s"/?uri=$uri") {
        status must_== 200
      }
    }
  }

}
