package org.mmisw.orr.ont

import org.json4s._
import org.json4s.native.JsonMethods._
import org.scalatra.test.specs2._
import com.typesafe.scalalogging.slf4j.Logging
import java.io.File


class OntControllerSpec extends MutableScalatraSpec with Logging {
  implicit val formats = org.json4s.DefaultFormats

  implicit val setup = new Setup("/etc/orront.conf", testing = true)
  addServlet(new OntController, "/*")

  val uri  = s"random.${java.util.UUID.randomUUID().toString}"
  val file = new File("src/test/resources/test.rdf")
  val format = "rdf"
  val map1 = Map("uri" -> uri,
    "name" -> "some.name",
    "authority" -> "fakeAuthority",
    "userName" -> "tester",
    "format" -> format
  )
  val map2 = map1 + ("name" -> "modified name")
  val body2 = pretty(render(Extraction.decompose(map2)))

  var version: Option[String] = None

  "GET /" should {
    "return status 200" in {
      get("/") {
        status must_== 200
      }
    }
  }

  sequential

  "POST new entry" should {
    "work" in {
      logger.info(s"post: $map1")
      post("/", map1, Map("file" -> file)) {
        logger.info(s"post new entry reply: $body")
        status must_== 200

        val json = parse(body)
        val map = json.extract[Map[String, String]]
        map.get("uri") must_== Some(uri)
      }
    }
  }

  "POST new version" should {
    "work" in {
      Thread.sleep(1500) // so the new version is diff.
      logger.info(s"post: $map2")
      post("/", params = map2, files = Map("file" -> file)) {
        logger.info(s"post new version reply: $body")
        status must_== 200

        val json = parse(body)
        val map = json.extract[Map[String, String]]
        map.get("uri") must_== Some(uri)
        version = map.get("version")
      }
    }
  }

  "PUT to update existing version" should {
    "work" in {
      val map3 = map2 + ("version" -> version.get, "name" -> "modified name again")
      logger.info(s"put: $map3")
      put("/version", map3) {
        logger.info(s"put reply: $body")
        status must_== 200
      }
    }
  }

  "DELETE version" should {
    "work" in {
      val map3 = map2 + ("version" -> version.get)
      logger.info(s"delete version: $map3")
      delete("/version", map3) {
        status must_== 200
      }
    }
  }

  "DELETE entry" should {
    "work" in {
      logger.info(s"delete entry: $map2")
      delete("/", map2) {
        status must_== 200
      }
    }
  }

}
