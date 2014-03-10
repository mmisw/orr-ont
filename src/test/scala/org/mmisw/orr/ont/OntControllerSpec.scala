package org.mmisw.orr.ont

import org.json4s._
import org.json4s.native.JsonMethods._
import org.scalatra.test.specs2._
import com.typesafe.scalalogging.slf4j.Logging
import java.io.File


class OntControllerSpec extends MutableScalatraSpec with Logging {
  implicit val formats = org.json4s.DefaultFormats
  com.mongodb.casbah.commons.conversions.scala.RegisterJodaTimeConversionHelpers()

  implicit val setup = new Setup("/etc/orront.conf", testing = true)
  addServlet(new OntController, "/*")

  val uri  = s"random.${java.util.UUID.randomUUID().toString}"
  val file = new File("src/test/resources/test.rdf")
  val format = "rdf"
  val map1 = Map("uri" -> uri,
    "name" -> "some.name",
    "orgName" -> "fakeOrg",
    "userName" -> "tester",
    "format" -> format
  )
  val map2 = map1 + ("name" -> "modified name")
  val body2 = pretty(render(Extraction.decompose(map2)))

  var registeredVersion: Option[String] = None

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
        val res = parse(body).extract[OntologyResult]
        res.uri must_== uri
        registeredVersion = res.version
      }
    }
  }

  "GET with only uri" should {
    "return the latest version" in {
      val map = Map("uri" -> uri, "format" -> "!md")
      logger.info(s"get: $map")
      get("/", map) {
        logger.info(s"get new entry reply: $body")
        status must_== 200
        val res = parse(body).extract[PendOntologyResult]
        res.uri must_== uri
        res.latestVersion must_== registeredVersion.get
      }
    }
  }

  "POST new version" should {
    "work" in {
      Thread.sleep(1500) // so the new version is diff.
      logger.info(s"post: $map2")
      post("/version", params = map2, files = Map("file" -> file)) {
        logger.info(s"post new version reply: $body")
        status must_== 200
        val res = parse(body).extract[OntologyResult]
        res.uri must_== uri
        registeredVersion = res.version
      }
    }
  }

  "GET with only uri" should {
    "return the latest version" in {
      val map = Map("uri" -> uri, "format" -> "!md")
      logger.info(s"get: $map")
      get("/", map) {
        logger.info(s"get new entry reply: $body")
        status must_== 200
        val res = parse(body).extract[PendOntologyResult]
        res.uri must_== uri
        res.latestVersion must_== registeredVersion.get
      }
    }
  }

  "PUT to update existing version" should {
    "work" in {
      val map3 = Map("uri" -> uri,
        "version" -> registeredVersion.get,
        "name" -> "modified name again",
        "userName" -> "tester"
      )
      logger.info(s"put: $map3")
      put("/version", map3) {
        logger.info(s"put reply: $body")
        status must_== 200
        val res = parse(body).extract[OntologyResult]
        res.uri must_== uri
        registeredVersion = res.version
      }
    }
  }

  "DELETE version" should {
    "work" in {
      val map = Map("uri" -> uri,
        "version" -> registeredVersion.get,
        "userName" -> "tester"
      )
      logger.info(s"delete version: $map")
      delete("/version", map) {
        status must_== 200
        val res = parse(body).extract[OntologyResult]
        res.uri must_== uri
        registeredVersion = res.version
      }
    }
  }

  "DELETE entry" should {
    "work" in {
      val map = Map("uri" -> uri,
        "userName" -> "tester"
      )
      logger.info(s"delete entry: $map")
      delete("/", map) {
        status must_== 200
        val res = parse(body).extract[OntologyResult]
        res.uri must_== uri
      }
    }
  }

}
