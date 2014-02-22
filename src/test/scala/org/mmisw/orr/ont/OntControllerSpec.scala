package org.mmisw.orr.ont

import org.json4s._
import org.json4s.native.JsonMethods._
import org.scalatra.test.specs2._
import com.typesafe.scalalogging.slf4j.Logging


class OntControllerSpec extends MutableScalatraSpec with Logging {
  implicit val formats = org.json4s.DefaultFormats
  import org.json4s.JsonDSL._

  implicit val setup = new Setup("/etc/orront.conf", testing = true)
  addServlet(new OntController, "/*")

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
      val map = Map("uri" -> uri, "name" -> "some.name", "userName" -> "tester")

      // post new entry
      logger.info(s"post: $map")
      post("/", body = pretty(render(map)),
           headers = Map("content-type" -> "application/json")) {

        status must_== 200

        logger.info(s"post reply: $body")
        val json = parse(body)
        val map = json.extract[Map[String, String]]
        map.get("uri") must_== Some(uri)
      }

      // post new version:
      val map2 = map + ("name" -> "modified name", "userName" -> "tester2")
      logger.info(s"post: $map2")
      var version: Option[String] = None
      post("/", body = pretty(render(map2)),
        headers = Map("content-type" -> "application/json")) {

        status must_== 200

        logger.info(s"post reply: $body")
        val json = parse(body)
        val map = json.extract[Map[String, String]]
        map.get("uri") must_== Some(uri)
        version = map.get("version")
      }

      val map3 = map2 + ("version" -> version.get, "name" -> "modified name AGAIN")

      // update existing version
      logger.info(s"put: $map")
      put("/version", body = pretty(render(map3)),
        headers = Map("content-type" -> "application/json")) {
        logger.info(s"put reply: $body")
        status must_== 200
      }

      // delete:
      submit("DELETE", s"/?uri=$uri") {
        status must_== 200
      }
    }
  }

}
