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

  "Get all users (GET /user)" should {
    "succeed and contain the 'admin' user" in {
      get("/user") {
        status must_== 200
        val res = parse(body).extract[List[PendUserResult]]
        res.exists(r => r.userName == "admin") must beTrue
      }
    }
  }

  "Get admin user (GET /user/admin)" should {
    "succeed" in {
      get("/user/admin") {
        status must_== 200
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

  "Create new users (POST /user)" should {
    "fail with no credentials" in {
      post("/user", body = pretty(render(map))) {
        status must_== 401
      }
    }

    val adminHeaders = Map("content-type" -> "application/json", "Authorization" -> adminCredentials)

    "succeed with admin credentials" in {
      post("/user", body = pretty(render(map)), headers = adminHeaders) {
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
        status must_== 403
      }
    }

    "succeed (user2)" in {
      post("/user", body = pretty(render(map2)), headers = adminHeaders) {
        status must_== 200
        val res = parse(body).extract[UserResult]
        res.userName must_== userName2
      }
    }
  }

  "Get a user (GET /user/:userName)" should {
    "succeed" in {
      get(s"/user/$userName") { status must_== 200 }
    }
  }

  "Check password (POST /user/chkpw)" should {
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

  "Update a user (PUT /user/:userName)" should {
    // don't name this 'body' as it would take precedence over the replied body below
    val reqBody = pretty(render(Map("firstName" -> "updated.firstName")))

    "fail with no credentials" in {
      val headers = Map("content-type" -> "application/json")
      put(s"/user/$userName", body = reqBody, headers = headers) {
        status must_== 401
      }
    }
    "succeed with user credentials" in {
      val headers = Map("content-type" -> "application/json", "Authorization" -> userCredentials)
      put(s"/user/$userName", body = reqBody, headers = headers) {
        status must_== 200
        logger.debug(s"PUT user reply body=$body")
        val res = parse(body).extract[UserResult]
        res.userName must_== userName
      }
    }
    "succeed with admin credentials" in {
      val headers = Map("content-type" -> "application/json", "Authorization" -> adminCredentials)
      put(s"/user/$userName", body = reqBody, headers = headers) {
        status must_== 200
        val res = parse(body).extract[UserResult]
        res.userName must_== userName
      }
    }
  }

  //////////
  // orgs
  //////////

  "Get all orgs (GET /org)" should {
    "succeed" in {
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

  "Create new orgs (POST /org)" should {
    val reqBody = pretty(render(orgMap))

    "fail with no credentials" in {
      val headers = Map("content-type" -> "application/json")
      post("/org", body = reqBody, headers = headers) {
        status must_== 401
      }
    }

    "fail with regular user credentials" in {
      val headers = Map("content-type" -> "application/json", "Authorization" -> userCredentials)
      post("/org", body = reqBody, headers = headers) {
        status must_== 403
      }
    }

    val adminHeaders = Map("content-type" -> "application/json", "Authorization" -> adminCredentials)

    "succeed with admin credentials" in {
      post("/org", body = reqBody, headers = adminHeaders) {
        status must_== 200
        val res = parse(body).extract[OrgResult]
        res.orgName must_== orgName
      }
    }

    "succeed with admin credentials (org2)" in {
      val orgMap =
        ("orgName"    -> orgName2) ~
          ("name"       -> "some organization2") ~
          ("ontUri"     -> "org.ontUri") ~
          ("members"    -> orgMembers)
      val reqBody = pretty(render(orgMap))

      post("/org", body = reqBody, headers = adminHeaders) {
        status must_== 200
        val res = parse(body).extract[OrgResult]
        res.orgName must_== orgName2
      }
    }
  }

  "Get members of an org (GET /org/:orgName/members)" should {
    "succeed" in {
      get(s"/org/$orgName/members") {
        status must_== 200
      }
    }
  }

  "Update an org (PUT /org/:orgName)" should {
    "fail with no credentials" in {
      val headers = Map("content-type" -> "application/json")
      put(s"/org/$orgName", body = pretty(render("ontUri" -> "updated.ontUri")),
        headers = headers) {
        status must_== 401
      }
    }

    "succeed with member credentials" in {
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

    "succeed with admin credentials" in {
      val headers = Map("content-type" -> "application/json", "Authorization" -> adminCredentials)
      put(s"/org/$orgName", body = pretty(render("ontUri" -> "updated.ontUri")),
        headers = headers) {
        status must_== 200
        val res = parse(body).extract[OrgResult]
        res.orgName must_== orgName
      }
    }
  }

  "Delete an org (DELETE /org/:orgName)" should {
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
    "succeed with admin credentials" in {
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

  "Get all onts (GET /ont)" should {
    "succeed" in {
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

  "Register a new ont (POST /ont)" should {
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

    "succeed with org member credentials" in {
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

    "succeed with admin credentials" in {
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

    // TODO "succeed with no explicit org"?
  }

  "Get an ont with given uri (GET /ont)" should {
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

  "Get onts with some filter parameters (GET /ont?orgName=nn)" should {
    "return list containing submission above" in {
      val map = Map("orgName" -> orgName)
      logger.info(s"get: $map")
      get("/ont", map) {
        status must_== 200
        val res = parse(body).extract[List[PendOntologyResult]]
        res.exists(_.orgName == Some(orgName)) must beTrue
      }
    }
  }

  "Register a new ont version (PUT /ont)" should {
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

    "succeed with member of corresponding org" in {
      Thread.sleep(1500) // so automatically assigned new version is diff.
      put("/ont", params = map2, files = Map("file" -> file), headers = userHeaders) {
        status must_== 200
        val res = parse(body).extract[OntologyResult]
        res.uri must_== uri
        registeredVersion = res.version
        logger.debug(s"registeredVersion=$registeredVersion")
      }
    }

    "succeed with admin credentials" in {
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

  "Get an ont with given uri (GET /ont)" should {
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

  "Update a specific ont version (PUT /ont)" should {
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

    "succeed with member of corresponding org" in {
      Thread.sleep(1500) // so automatically assigned new version is diff.
      put("/ont", params = map2 + ("version" -> registeredVersion.get), files = Map("file" -> file), headers = userHeaders) {
        status must_== 200
        val res = parse(body).extract[OntologyResult]
        res.uri must_== uri
      }
    }

    "succeed with admin credentials" in {
      Thread.sleep(1500) // so automatically assigned new version is diff.
      put("/ont", params = map2, files = Map("file" -> file), headers = adminHeaders) {
        status must_== 200
        val res = parse(body).extract[OntologyResult]
        res.uri must_== uri
      }
    }
  }

  "Delete an ont version (DELETE /ont)" should {
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

    "succeed with member credentials" in {
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

  "Delete a whole ont entry (DELETE /org/)" should {
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

    "succeed with member credentials" in {
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

  "Delete a user (DELETE /user/:userName)" should {
    "fail with no credentials" in {
      delete(s"/user/$userName2", headers = Map.empty) {
        status must_== 401
      }
    }

    "fail with regular user credentials" in {
      delete(s"/user/$userName2", headers = userHeaders) {
        status must_== 403
      }
    }

    "succeed with admin credentials" in {
      delete(s"/user/$userName2", headers = adminHeaders) {
        status must_== 200
        val res = parse(body).extract[UserResult]
        res.userName must_== userName2
      }
    }
  }

  /////////////////////
  // final cleanup
  /////////////////////

  // don't do it if the nocleanup env var is defined. Allows to inspect the
  // final contents of the test collections.
  if (!sys.env.isDefinedAt("nocleanup")) "cleanup" should {

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

    "succeed for /ont with admin credentials" in {
      delete("/ont/!/all", headers = adminHeaders) { status must_== 200 }
    }
    "succeed for /org with admin credentials" in {
      delete("/org/!/all", headers = adminHeaders) { status must_== 200 }
    }
    // users at the end to allow admin authentication for all of the above as well
    "succeed for /user with admin credentials" in {
      delete("/user/!/all", headers = adminHeaders) { status must_== 200 }
    }
  }

}
