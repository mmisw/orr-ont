package org.mmisw.orr.ont.app

import java.io.File

import com.typesafe.scalalogging.{StrictLogging => Logging}
import org.json4s._
import org.json4s.native.JsonMethods._
import org.mmisw.orr.ont._
import org.mmisw.orr.ont.auth.authUtil
import org.mmisw.orr.ont.service.{TripleStoreService, OntService, UserService}
import org.mmisw.orr.ont.swld.ontUtil
import org.scalatra.test.specs2._
import org.specs2.mock.Mockito


/**
 * A general sequence involving users, orgs, and onts.
 */
class SequenceSpec extends MutableScalatraSpec with BaseSpec with Mockito with Logging {
  import org.json4s.JsonDSL._

  implicit val ontService = new OntService
  implicit val tsService = mock[TripleStoreService]

  addServlet(new UserController, "/user/*")
  addServlet(new OrgController,  "/org/*")
  addServlet(new OntController,  "/ont/*")
  addServlet(new TripleStoreController,  "/ts/*")
  addServlet(new SelfHostedOntController, "/*")

  sequential

  //////////
  // users
  //////////

  "Get all users (GET /user)" should {
    "succeed and contain the 'admin' user" in {
      get("/user") {
        status must_== 200
        val res = parse(body).extract[List[UserResult]]
        res.exists(r => r.userName == "admin") must beTrue
      }
    }
  }

  "Get admin user (GET /user/admin)" should {
    "succeed" in {
      get("/user/admin") {
        status must_== 200
        val res = parse(body).extract[UserResult]
        res.userName must_== "admin"
      }
    }
  }

  "Get non-existent user (GET /ont)" should {
    "fail with not found status" in {
      get(s"/user/${newUserName()}") {
        status must_== 404
      }
    }
  }

  val adminHeaders = Map("Authorization" -> adminCredentials)

  val userName = newUserName()
  val password = "pass"

  val map = Map(
    "userName"  -> userName,
    "email"     -> "foo@example.net",
    "firstName" -> "myFirstName",
    "lastName"  -> "myLastName",
    "password"  -> password)

  val userName2 = newUserName() // to test DELETE and other opers
  val password2 = "pass2"
  val user2Headers = Map("Authorization" -> authUtil.basicCredentials(userName2, password2))

  "Create new users (POST /user)" should {

    "succeed with no credentials" in {
      post("/user", body = pretty(render(map))) {
        status must_== 201
      }
    }

    val map2 = Map(
      "userName"  -> userName2,
      "email"     -> "foo@example.net",
      "firstName" -> "myFirstName",
      "lastName"  -> "myLastName",
      "password"  -> password2)

    "succeed (user2)" in {
      post("/user", body = pretty(render(map2)), headers = adminHeaders) {
        status must_== 201
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

  "Check password (POST /user/auth)" should {
    "return 200 with correct password" in {
      post("/user/auth", body = pretty(render(Map("userName" -> userName, "password" -> password))),
        headers = Map("content-type" -> "application/json")) {
        status must_== 200
      }
    }
    "return 401 with bad password" in {
      post("/user/auth", body = pretty(render(Map("userName" -> userName, "password" -> "wrong"))),
        headers = Map("content-type" -> "application/json")) {
        status must_== 401
      }
    }
  }

  val userCredentials = authUtil.basicCredentials(userName, password)
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

    val jwt = jwtUtil.createToken(userName, Map())
    "succeed with JWT" in {
      val headers = Map("content-type" -> "application/json")
      val reqBody2 = pretty(render(Map("jwt" -> jwt)))
      put(s"/user/$userName", body = reqBody2, headers = headers) {
        status must_== 200
        logger.debug(s"PUT user reply body=$body")
        val res = parse(body).extract[UserResult]
        res.userName must_== userName
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

  "notifyPasswordHasBeenReset" should {
    "email user" in {
      val userService = new UserService
      val user = db.User(userName = "un", firstName = "fn", lastName = "ln", password = "pw", email = "e@m.x")
      userService.notifyPasswordHasBeenReset(user)
      1===1
    }
  }

  "username reminder" should {
    "send email with username" in {
      val reqBody = pretty(render(Map("email" -> map("email"))))
      put(s"/user/unr/", body = reqBody) {
        status must_== 200
        logger.debug(s"PUT username reminder reply body=$body")
        val res = parse(body).extract[UsernameReminderResult]
        res.email must_== map("email")
        res.message must beSome
      }
    }
  }

  "password reset" should {
    "email user" in {
      put(s"/user/rpwr/$userName") {
        status must_== 200
        logger.debug(s"PUT username password reset reply body=$body")
        val res = parse(body).extract[PasswordResetResult]
        res.userName must_== userName
        res.email must_== map.get("email")
        res.message must beSome
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
        val res = parse(body).extract[List[OrgResult]]
        res.length must be >= 0
      }
    }
  }

  "Get non-existent org (GET /ont)" should {
    "fail with not found status" in {
      get(s"/org/${newOrgName()}") {
        status must_== 404
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
        status must_== 201
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
        status must_== 201
        val res = parse(body).extract[OrgResult]
        res.orgName must_== orgName2
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
      val userCredentials = authUtil.basicCredentials(userName2, password2)
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
        val res = parse(body).extract[List[OntologySummaryResult]]
        res.length must be >= 0
      }
    }
  }

  "Get non-existent ont (GET /ont)" should {
    "fail with not found status" in {
      val map = Map("uri" -> newOntUri())
      get("/ont", map) {
        status must_== 404
      }
    }
  }

  val ont1Uri  = "http://example.org/ont1"
  val ont1File = new File("src/test/resources/ont1.rdf")
  val format = "rdf"
  val map1 = Map("uri" -> ont1Uri,
    "name" -> "some ont name",
    "orgName" -> orgName,
    "userName" -> userName,
    "format" -> format
  )
  var registeredVersion: Option[String] = None

  "Register a new ont (POST /ont)" should {
    "fail with no credentials" in {
      post("/ont", map1, Map("file" -> ont1File)) {
        status must_== 401
      }
    }

    "fail with user not member of org" in {
      post("/ont", map1, Map("file" -> ont1File), headers = user2Headers) {
        status must_== 403
      }
    }

    "succeed with org member credentials" in {
      post("/ont", map1, Map("file" -> ont1File), headers = userHeaders) {
        status must_== 201
        val res = parse(body).extract[OntologyResult]
        registeredVersion = res.version
        logger.debug(s"registeredVersion=$registeredVersion")
        res.uri must_== ont1Uri
      }
    }

    "fail with duplicate uri if resubmitted as new" in {
      post("/ont", map1, Map("file" -> ont1File), headers = userHeaders) {
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
      post("/ont", map2, Map("file" -> ont1File), headers = adminHeaders) {
        status must_== 201
        val res = parse(body).extract[OntologyResult]
        res.uri must_== map2("uri")
      }
    }

    // TODO "succeed with no explicit org"?
  }

  "Get an ont with given uri (GET /ont)" should {
    "return the latest version" in {
      val map = Map("uri" -> ont1Uri, "format" -> "!md")
      logger.info(s"get: $map")
      get("/ont", map) {
        logger.info(s"get new entry reply: $body")
        status must_== 200
        val res = parse(body).extract[OntologySummaryResult]
        res.uri must_== ont1Uri
        res.versions.isDefined must beTrue
        val latestVersion = res.versions.head.sorted(Ordering[String].reverse).head
        latestVersion must_== registeredVersion.get
      }
    }

    // <FORMATS>
    def doGoodFormat(reqFormat: String, expectedMimeOption: Option[String] = None) = {
      val map = Map("uri" -> ont1Uri, "format" -> reqFormat)
      get("/ont", map) {
        val contentType = response.getContentType()
        if (status != 200) {
          println(s"doGoodFormat: reqFormat=$reqFormat -> status=$status contentType=$contentType  reason=${response.getReason()}")
        }
        status must_== 200
        val expectedMime = expectedMimeOption.getOrElse(ontUtil.mimeMappings(reqFormat))
        contentType must contain(expectedMime)
      }
    }
    "return expected file for rdf reqFormat"    in { doGoodFormat("rdf") }
    "return expected file for jsonld reqFormat" in { doGoodFormat("jsonld") }
    "return expected file for n3 reqFormat"     in { doGoodFormat("n3") }
    // TODO: note that for ttl, currently used Jena version returns mime type for n3:
    "return expected file for ttl reqFormat"    in { doGoodFormat("ttl", Some(ontUtil.mimeMappings("n3"))) }
    "return expected file for nt reqFormat"     in { doGoodFormat("nt") }
    "return expected file for rj reqFormat"     in { doGoodFormat("rj") }

    def doBadFormat(reqFormat: String) = {
      val map = Map("uri" -> ont1Uri, "format" -> reqFormat)
      get("/ont", map) {
        val respBody = body
        //println(s"doBadFormat: reqFormat=$reqFormat  response body=\n  " + respBody.replace("\n", "\n  "))
        status must_== 406
        val res = parse(respBody).extract[Map[String,String]]
        res.get("format") must beSome(reqFormat)
      }
    }
    // TODO move the following to doGoodFormat once jena actually supports these documented supported formats
    "return expected file for nq reqFormat"     in { doBadFormat("nq") }
    "return expected file for trig reqFormat"   in { doBadFormat("trig") }
    // </FORMATS>
  }

  "Get onts with some filter parameters (GET /ont?orgName=nn)" should {
    "return list containing submission above" in {
      val map = Map("orgName" -> orgName)
      logger.info(s"get: $map")
      get("/ont", map) {
        status must_== 200
        val res = parse(body).extract[List[OntologySummaryResult]]
        res.exists(_.orgName == Some(orgName)) must beTrue
      }
    }
  }

  "Register a new ont version (PUT /ont)" should {
    val map2 = map1 + ("name" -> "modified name")
    //val body2 = pretty(render(Extraction.decompose(map2)))

    "fail with no credentials" in {
      put("/ont", params = map2, files = Map("file" -> ont1File)) {
        status must_== 401
      }
    }

    "fail with non-member of corresponding org" in {
      put("/ont", params = map2, files = Map("file" -> ont1File), headers = user2Headers) {
        status must_== 403
      }
    }

    "succeed with member of corresponding org" in {
      Thread.sleep(1500) // so automatically assigned new version is diff.
      put("/ont", params = map2, files = Map("file" -> ont1File), headers = userHeaders) {
        status must_== 200
        val res = parse(body).extract[OntologyResult]
        registeredVersion = res.version
        logger.debug(s"registeredVersion=$registeredVersion")
        res.uri must_== ont1Uri
      }
    }

    "succeed with admin credentials" in {
      Thread.sleep(1500) // so automatically assigned new version is diff.
      put("/ont", params = map2, files = Map("file" -> ont1File), headers = adminHeaders) {
        status must_== 200
        val res = parse(body).extract[OntologyResult]
        registeredVersion = res.version
        logger.debug(s"registeredVersion=$registeredVersion")
        res.uri must_== ont1Uri
      }
    }
  }

  "Get an ont with given uri (GET /ont)" should {
    "return the new latest version" in {
      val map = Map("uri" -> ont1Uri, "format" -> "!md")
      logger.info(s"get: $map")
      get("/ont", map) {
        logger.info(s"get new entry reply: $body")
        status must_== 200
        val res = parse(body).extract[OntologySummaryResult]
        res.uri must_== ont1Uri
        res.versions.isDefined must beTrue
        val latestVersion = res.versions.head.sorted(Ordering[String].reverse).head
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
      put("/ont", params = map2 + ("version" -> registeredVersion.get), files = Map("file" -> ont1File)) {
        status must_== 401
      }
    }

    "fail with non-member of corresponding org" in {
      put("/ont", params = map2 + ("version" -> registeredVersion.get), files = Map("file" -> ont1File), headers = user2Headers) {
        status must_== 403
      }
    }

    "succeed with member of corresponding org" in {
      Thread.sleep(1500) // so automatically assigned new version is diff.
      put("/ont", params = map2 + ("version" -> registeredVersion.get), files = Map("file" -> ont1File), headers = userHeaders) {
        status must_== 200
        val res = parse(body).extract[OntologyResult]
        res.uri must_== ont1Uri
      }
    }

    "succeed with admin credentials" in {
      Thread.sleep(1500) // so automatically assigned new version is diff.
      put("/ont", params = map2, files = Map("file" -> ont1File), headers = adminHeaders) {
        status must_== 200
        val res = parse(body).extract[OntologyResult]
        res.uri must_== ont1Uri
      }
    }
  }

  /////////////////////
  // triple store
  /////////////////////

  "Get triple store size (GET /ts)" should {
    "call tsService.getSizeget once" in {
      get("/ts", headers = adminHeaders) {
        status must_== 200
        there was one(tsService).getSize(None)
      }
    }
  }

  "Load an ont in triple store (POST /ts)" should {
    "fail with no credentials" in {
      post("/ts", Map("uri" -> ont1Uri)) {
        status must_== 401
      }
    }

    "fail with regular user credentials" in {
      post("/ts", Map("uri" -> ont1Uri), headers = userHeaders) {
        status must_== 403
      }
    }

    "succeed with admin credentials" in {
      post("/ts", Map("uri" -> ont1Uri), headers = adminHeaders) {
        status must_== 200
        there was one(tsService).loadUri(ont1Uri)
      }
    }
  }

  "Reload an ont in triple store (PUT /ts)" should {
    "fail with no credentials" in {
      put("/ts", Map("uri" -> ont1Uri)) {
        status must_== 401
      }
    }

    "fail with regular user credentials" in {
      put("/ts", Map("uri" -> ont1Uri), headers = userHeaders) {
        status must_== 403
      }
    }

    "succeed with admin credentials" in {
      put("/ts", Map("uri" -> ont1Uri), headers = adminHeaders) {
        status must_== 200
        there was one(tsService).reloadUri(ont1Uri)
      }
    }
  }

  "Reload all onts in triple store (PUT /ts)" should {
    "fail with no credentials" in {
      put("/ts") {
        status must_== 401
      }
    }

    "fail with regular user credentials" in {
      put("/ts", Map(), headers = userHeaders) {
        status must_== 403
      }
    }

    "succeed with admin credentials" in {
      put("/ts", Map(), headers = adminHeaders) {
        status must_== 200
        there was one(tsService).reloadAll()
      }
    }
  }

  "Unload an ont from triple store (DELETE /ts)" should {
    "fail with no credentials" in {
      delete("/ts", Map("uri" -> ont1Uri)) {
        status must_== 401
      }
    }

    "fail with regular user credentials" in {
      delete("/ts", Map("uri" -> ont1Uri), headers = userHeaders) {
        status must_== 403
      }
    }

    "succeed with admin credentials" in {
      delete("/ts", Map("uri" -> ont1Uri), headers = adminHeaders) {
        status must_== 200
        there was one(tsService).unloadUri(ont1Uri)
      }
    }
  }

  "Unload all onts from triple store (DELETE /ts)" should {
    "fail with no credentials" in {
      delete("/ts") {
        status must_== 401
      }
    }

    "fail with regular user credentials" in {
      delete("/ts", Map(), headers = userHeaders) {
        status must_== 403
      }
    }

    "succeed with admin credentials" in {
      delete("/ts", Map(), headers = adminHeaders) {
        status must_== 200
        there was one(tsService).unloadAll()
      }
    }
  }

  ////////////////////////////
  // "self-hosted" requests
  ////////////////////////////
  // Except for very basic requests (eg. non-existing), this seems rather tricky to test
  "self-hosted: GET /non/existent)" should {
    "return not-found" in {
      get("/non/existent") {
        //println(s"GET /non/existent response body=$body")
        status must_== 404
      }
    }
  }

  /// continuing with onts specifically...

  "Delete an ont version (DELETE /ont)" should {
    "fail with no credentials" in {
      val map = Map("uri" -> ont1Uri,
        "version" -> registeredVersion.get,
        "userName" -> userName
      )
      delete("/ont", map) {
        status must_== 401
      }
    }

    "fail with non-member of corresponding org" in {
      val map = Map("uri" -> ont1Uri,
        "version" -> registeredVersion.get,
        "userName" -> userName2
      )
      delete("/ont", map, headers = user2Headers) {
        status must_== 403
      }
    }

    "succeed with member credentials" in {
      val map = Map("uri" -> ont1Uri,
        "version" -> registeredVersion.get,
        "userName" -> userName
      )
      delete("/ont", map, headers = userHeaders) {
        status must_== 200
        val res = parse(body).extract[OntologyResult]
        res.uri must_== ont1Uri
      }
    }
  }

  "Delete a whole ont entry (DELETE /org/)" should {
    "fail with no credentials" in {
      val map = Map("uri" -> ont1Uri,
        "userName" -> userName
      )
      delete("/ont", map) {
        status must_== 401
      }
    }

    "fail with non-member of corresponding org" in {
      val map = Map("uri" -> ont1Uri,
        "userName" -> userName2
      )
      delete("/ont", map, headers = user2Headers) {
        status must_== 403
      }
    }

    "succeed with member credentials" in {
      val map = Map("uri" -> ont1Uri,
        "userName" -> userName
      )
      delete("/ont", map, headers = userHeaders) {
        status must_== 200
        val res = parse(body).extract[OntologyResult]
        res.uri must_== ont1Uri
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
