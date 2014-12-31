package org.mmisw.orr.ont

import java.io.File

import com.typesafe.scalalogging.slf4j.Logging
import org.json4s._
import org.json4s.native.JsonMethods._
import org.scalatra.test.specs2._


/**
 * A general sequence involving users, orgs, and onts.
 */
class SequenceSpec extends MutableScalatraSpec with BaseSpec with Logging {
  import org.json4s.JsonDSL._

  addServlet(new UserController, "/user/*")
  addServlet(new OrgController,  "/org/*")
  addServlet(new OntController,  "/ont/*")

  sequential

  //////////
  // users
  //////////

  "GET all users" should {
    "work and contain admin" in {
      get("/user") {
        status must_== 200
        val res = parse(body).extract[List[PendUserResult]]
        res.exists(r => r.userName == "admin") must beTrue
      }
    }
  }

  "GET admin user" should {
    "work" in {
      get("/user/admin") {
        status must_== 200
        logger.debug(s"body=$body")
        val res = parse(body).extract[PendUserResult]
        res.userName must_== "admin"
      }
    }
  }

  val adminHeaders = Map("Authorization" -> adminCredentials)

  val userName = newUserName()
  val password = "pass"

  val map = Map(
    "userName"  -> userName,
    "firstName" -> "myFirstName",
    "lastName"  -> "myLastName",
    "password"  -> password)

  val userName2 = newUserName() // to test DELETE and other opers
  val password2 = "pass2"
  val user2Headers = Map("Authorization" -> basicCredentials(userName2, password2))

  "POST new users" should {
    "fail with no credentials" in {
      post("/user", body = pretty(render(map))) {
        println(s"body = $body")
        status must_== 401
      }
    }

    val adminHeaders = Map("content-type" -> "application/json", "Authorization" -> adminCredentials)

    "work with admin credentials" in {
      post("/user", body = pretty(render(map)), headers = adminHeaders) {
        println(s"body = $body")
        status must_== 200
        val res = parse(body).extract[UserResult]
        res.userName must_== userName
      }
    }

    val map2 = Map(
      "userName"  -> userName2,
      "firstName" -> "myFirstName",
      "lastName"  -> "myLastName",
      "password"  -> password2)

    "fail with regular user credentials" in {
      // first userName (already added) trying to create a new user
      post("/user", body = pretty(render(map2)), headers = userHeaders) {
        println(s"body = $body")
        status must_== 403
      }
    }

    "work (user2)" in {
      post("/user", body = pretty(render(map2)), headers = adminHeaders) {
        println(s"body = $body")
        status must_== 200
        val res = parse(body).extract[UserResult]
        res.userName must_== userName2
      }
    }
  }

  "GET a user" should {
    "work" in {
      get(s"/user/$userName") { status must_== 200 }
    }
  }

  "POST check password" should {
    "return 200 with correct password" in {
      post("/user/chkpw", body = pretty(render(Map("userName" -> userName, "password" -> password))),
        headers = Map("content-type" -> "application/json")) {
        status must_== 200
      }
    }
    "return 401 with bad password" in {
      post("/user/chkpw", body = pretty(render(Map("userName" -> userName, "password" -> "wrong"))),
        headers = Map("content-type" -> "application/json")) {
        status must_== 401
      }
    }
  }

  val userCredentials = basicCredentials(userName, password)
  val userHeaders = Map("Authorization" -> userCredentials)

  "PUT to update a user" should {
    val body = pretty(render(Map("userName" -> userName, "firstName" -> "updated.firstName")))

    "fail with no credentials" in {
      val headers = Map("content-type" -> "application/json")
      put("/user", body = body, headers = headers) {
        status must_== 401
      }
    }
    "work with user credentials" in {
      val headers = Map("content-type" -> "application/json", "Authorization" -> userCredentials)
      put("/user", body = body, headers = headers) {
        status must_== 200
        val res = parse(body).extract[UserResult]
        res.userName must_== userName
      }
    }
    "work with admin credentials" in {
      val headers = Map("content-type" -> "application/json", "Authorization" -> adminCredentials)
      put("/user", body = body, headers = headers) {
        status must_== 200
        val res = parse(body).extract[UserResult]
        res.userName must_== userName
      }
    }
  }

  //////////
  // orgs
  //////////

  "GET all orgs" should {
    "work" in {
      get("/org") {
        status must_== 200
        val res = parse(body).extract[List[PendOrgResult]]
        res.length must be >= 0
      }
    }
  }

  val orgName = newOrgName()
  val orgMembers = Seq(userName)
  val orgMap =
      ("orgName"    -> orgName) ~
      ("name"       -> "some organization") ~
      ("ontUri"     -> "org.ontUri") ~
      ("members"    -> orgMembers)

  val orgName2 = newOrgName()  // to test DELETE

  "POST new orgs" should {
    val body = pretty(render(orgMap))

    "fail with no credentials" in {
      val headers = Map("content-type" -> "application/json")
      post("/org", body = body, headers = headers) {
        status must_== 401
      }
    }

    "fail with regular user credentials" in {
      val headers = Map("content-type" -> "application/json", "Authorization" -> userCredentials)
      post("/org", body = body, headers = headers) {
        status must_== 403
      }
    }

    val adminHeaders = Map("content-type" -> "application/json", "Authorization" -> adminCredentials)

    "work with admin credentials" in {
      post("/org", body = body, headers = adminHeaders) {
        status must_== 200
        val res = parse(body).extract[OrgResult]
        res.orgName must_== orgName
      }
    }

    "work with admin credentials (org2)" in {
      val orgMap =
        ("orgName"    -> orgName2) ~
          ("name"       -> "some organization2") ~
          ("ontUri"     -> "org.ontUri") ~
          ("members"    -> orgMembers)
      val body = pretty(render(orgMap))

      post("/org", body = body, headers = adminHeaders) {
        status must_== 200
        val res = parse(body).extract[OrgResult]
        res.orgName must_== orgName2
      }
    }
  }

  "PUT to update an org" should {
    "fail with no credentials" in {
      val headers = Map("content-type" -> "application/json")
      put(s"/org/$orgName", body = pretty(render("ontUri" -> "updated.ontUri")),
        headers = headers) {
        status must_== 401
      }
    }

    "work with member credentials" in {
      val headers = Map("content-type" -> "application/json", "Authorization" -> userCredentials)
      put(s"/org/$orgName", body = pretty(render("ontUri" -> "updated.ontUri")),
        headers = headers) {
        status must_== 200
        val res = parse(body).extract[OrgResult]
        res.orgName must_== orgName
      }
    }

    "fail with non-member credentials" in {
      val userCredentials = basicCredentials(userName2, password2)
      val headers = Map("content-type" -> "application/json", "Authorization" -> userCredentials)
      put(s"/org/$orgName", body = pretty(render("ontUri" -> "updated.ontUri")),
        headers = headers) {
        status must_== 403
      }
    }

    "work with admin credentials" in {
      val headers = Map("content-type" -> "application/json", "Authorization" -> adminCredentials)
      put(s"/org/$orgName", body = pretty(render("ontUri" -> "updated.ontUri")),
        headers = headers) {
        status must_== 200
        val res = parse(body).extract[OrgResult]
        res.orgName must_== orgName
      }
    }
  }

  "DELETE an org" should {
    "fail with no credentials" in {
      delete(s"/org/$orgName2", headers = Map.empty) {
        status must_== 401
      }
    }
    "fail with regular user credentials" in {
      delete(s"/org/$orgName2", headers = userHeaders) {
        status must_== 403
      }
    }
    "work with admin credentials" in {
      delete(s"/org/$orgName2", headers = adminHeaders) {
        status must_== 200
        val res = parse(body).extract[OrgResult]
        res.orgName must_== orgName2
      }
    }
  }

  //////////
  // onts
  //////////

  "GET all onts" should {
    "work" in {
      get("/ont") {
        status must_== 200
        val res = parse(body).extract[List[PendOntologyResult]]
        res.length must be >= 0
      }
    }
  }

  val uri  = newOntUri()
  val file = new File("src/test/resources/test.rdf")
  val format = "rdf"
  val map1 = Map("uri" -> uri,
    "name" -> "some ont name",
    "orgName" -> orgName,
    "userName" -> userName,
    "format" -> format
  )
  var registeredVersion: Option[String] = None

  "POST a new ont" should {
    "fail with no credentials" in {
      post("/ont", map1, Map("file" -> file)) {
        status must_== 401
      }
    }

    "fail with user not member of org" in {
      post("/ont", map1, Map("file" -> file), headers = user2Headers) {
        status must_== 403
      }
    }

    "work with org member credentials" in {
      post("/ont", map1, Map("file" -> file), headers = userHeaders) {
        status must_== 200
        val res = parse(body).extract[OntologyResult]
        res.uri must_== uri
        registeredVersion = res.version
        logger.debug(s"registeredVersion=$registeredVersion")
      }
    }

    "fail with duplicate uri if resubmitted as new" in {
      post("/ont", map1, Map("file" -> file), headers = userHeaders) {
        status must_== 409
      }
    }

    "work with admin credentials" in {
      // need a diff uri
      val map2 = Map("uri" -> newOntUri(),
        "name" -> "some ont name",
        "orgName" -> orgName,
        "userName" -> userName,
        "format" -> format
      )
      post("/ont", map2, Map("file" -> file), headers = adminHeaders) {
        status must_== 200
        val res = parse(body).extract[OntologyResult]
        res.uri must_== map2("uri")
      }
    }

    // TODO "work with no explicit org"?
  }

  "GET an ont with given uri" should {
    "return the latest version" in {
      val map = Map("uri" -> uri, "format" -> "!md")
      logger.info(s"get: $map")
      get("/ont", map) {
        logger.info(s"get new entry reply: $body")
        status must_== 200
        val res = parse(body).extract[PendOntologyResult]
        res.uri must_== uri
        val latestVersion = res.versions.sorted(Ordering[String].reverse).head
        latestVersion must_== registeredVersion.get
      }
    }
  }

  "PUT to register a new ont version" should {
    val map2 = map1 + ("name" -> "modified name")
    //val body2 = pretty(render(Extraction.decompose(map2)))

    "fail with no credentials" in {
      put("/ont", params = map2, files = Map("file" -> file)) {
        status must_== 401
      }
    }

    "fail with non-member of corresponding org" in {
      put("/ont", params = map2, files = Map("file" -> file), headers = user2Headers) {
        status must_== 403
      }
    }

    "work with member of corresponding org" in {
      Thread.sleep(1500) // so automatically assigned new version is diff.
      put("/ont", params = map2, files = Map("file" -> file), headers = userHeaders) {
        status must_== 200
        val res = parse(body).extract[OntologyResult]
        res.uri must_== uri
        registeredVersion = res.version
        logger.debug(s"registeredVersion=$registeredVersion")
      }
    }

    "work with admin credentials" in {
      Thread.sleep(1500) // so automatically assigned new version is diff.
      put("/ont", params = map2, files = Map("file" -> file), headers = adminHeaders) {
        status must_== 200
        val res = parse(body).extract[OntologyResult]
        res.uri must_== uri
        registeredVersion = res.version
        logger.debug(s"registeredVersion=$registeredVersion")
      }
    }
  }

  "GET an ont with given uri" should {
    "return the new latest version" in {
      val map = Map("uri" -> uri, "format" -> "!md")
      logger.info(s"get: $map")
      get("/ont", map) {
        logger.info(s"get new entry reply: $body")
        status must_== 200
        val res = parse(body).extract[PendOntologyResult]
        res.uri must_== uri
        val latestVersion = res.versions.sorted(Ordering[String].reverse).head
        latestVersion must_== registeredVersion.get
      }
    }
  }

  "PUT to update a specific ont version" should {
    val map2 = map1 + ("name" -> "modified name on version")

    // pass the specific version (registeredVersion).
    // note: this is done within each check below such that registeredVersion
    // is actually defined from previous checks.

    "fail with no credentials" in {
      put("/ont", params = map2 + ("version" -> registeredVersion.get), files = Map("file" -> file)) {
        status must_== 401
      }
    }

    "fail with non-member of corresponding org" in {
      put("/ont", params = map2 + ("version" -> registeredVersion.get), files = Map("file" -> file), headers = user2Headers) {
        status must_== 403
      }
    }

    "work with member of corresponding org" in {
      Thread.sleep(1500) // so automatically assigned new version is diff.
      put("/ont", params = map2 + ("version" -> registeredVersion.get), files = Map("file" -> file), headers = userHeaders) {
        status must_== 200
        val res = parse(body).extract[OntologyResult]
        res.uri must_== uri
      }
    }

    "work with admin credentials" in {
      Thread.sleep(1500) // so automatically assigned new version is diff.
      put("/ont", params = map2, files = Map("file" -> file), headers = adminHeaders) {
        status must_== 200
        val res = parse(body).extract[OntologyResult]
        res.uri must_== uri
      }
    }
  }

  "DELETE an ont version" should {
    "fail with no credentials" in {
      val map = Map("uri" -> uri,
        "version" -> registeredVersion.get,
        "userName" -> userName
      )
      delete("/ont", map) {
        status must_== 401
      }
    }

    "fail with non-member of corresponding org" in {
      val map = Map("uri" -> uri,
        "version" -> registeredVersion.get,
        "userName" -> userName2
      )
      delete("/ont", map, headers = user2Headers) {
        status must_== 403
      }
    }

    "work with member credentials" in {
      val map = Map("uri" -> uri,
        "version" -> registeredVersion.get,
        "userName" -> userName
      )
      delete("/ont", map, headers = userHeaders) {
        status must_== 200
        val res = parse(body).extract[OntologyResult]
        res.uri must_== uri
      }
    }
  }

  "DELETE a whole ont entry" should {
    "fail with no credentials" in {
      val map = Map("uri" -> uri,
        "userName" -> userName
      )
      delete("/ont", map) {
        status must_== 401
      }
    }

    "fail with non-member of corresponding org" in {
      val map = Map("uri" -> uri,
        "userName" -> userName2
      )
      delete("/ont", map, headers = user2Headers) {
        status must_== 403
      }
    }

    "work with member credentials" in {
      val map = Map("uri" -> uri,
        "userName" -> userName
      )
      delete("/ont", map, headers = userHeaders) {
        status must_== 200
        val res = parse(body).extract[OntologyResult]
        res.uri must_== uri
      }
    }

  }

  //////////////////////////////////////////
  // Misc operations on supporting entities
  //////////////////////////////////////////

  "DELETE a user" should {
    "fail with no credentials" in {
      delete("/user", Map("userName" -> userName2)) {
        status must_== 401
      }
    }

    "fail with regular user credentials" in {
      delete("/user", Map("userName" -> userName2), headers = userHeaders) {
        status must_== 403
      }
    }

    "work with admin credentials" in {
      delete("/user", Map("userName" -> userName2), adminHeaders) {
        status must_== 200
        val res = parse(body).extract[UserResult]
        res.userName must_== userName2
      }
    }
  }

  /////////////////////
  // final cleanup
  /////////////////////

  "cleanup" should {
    "fail for /ont with no admin credentials" in {
      delete("/ont/!/all", headers = Map.empty) { status must_== 401 }
    }
    "fail for /org with no admin credentials" in {
      delete("/org/!/all", headers = Map.empty) { status must_== 401 }
    }
    "fail for /user with no admin credentials" in {
      delete("/user/!/all", headers = Map.empty) { status must_== 401 }
    }

    "fail for /ont with regular user credentials" in {
      delete("/ont/!/all", headers = userHeaders) { status must_== 403 }
    }
    "fail for /org with regular user credentials" in {
      delete("/org/!/all", headers = userHeaders) { status must_== 403 }
    }
    "fail for /user with regular user credentials" in {
      delete("/user/!/all", headers = userHeaders) { status must_== 403 }
    }

    "work for /ont with admin credentials" in {
      delete("/ont/!/all", headers = adminHeaders) { status must_== 200 }
    }
    "work for /ont with admin credentials" in {
      delete("/org/!/all", headers = adminHeaders) { status must_== 200 }
    }
    // users at the end to allow admin authentication for all of the above as well
    "work for /ont with admin credentials" in {
      delete("/user/!/all", headers = adminHeaders) { status must_== 200 }
    }
  }

}
