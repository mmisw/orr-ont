package org.mmisw.orr.ont

import com.mongodb.casbah.Imports._
import com.typesafe.scalalogging.slf4j.Logging
import org.mmisw.orr.ont.db.Organization
import scala.util.{Failure, Success, Try}
import com.novus.salat._
import com.novus.salat.global._
import org.joda.time.DateTime
import org.mmisw.orr.ont.auth.AuthenticationSupport


class OrgController(implicit setup: Setup) extends BaseController
    with AuthenticationSupport with Logging {

  get("/") {
    orgsDAO.find(MongoDBObject()) map getOrgJson
  }

  get("/:orgName") {
    val orgName = require(params, "orgName")
    getOrgJson(getOrg(orgName))
  }

  // http post localhost:8080/org orgName=mmi name="mmi project" ontUri=http://mmisw.org/ont/mmi members:='["carueda"]'
  // basic authentication: http -a username:password post ...
  post("/") {
    if (!setup.testing) basicAuth
    val map = body()

    logger.info(s"POST body = $map")
    val orgName   = require(map, "orgName")
    val name       = require(map, "name")
    val ontUri     = getString(map, "ontUri")
    val members    = getSeq(map, "members")

    orgsDAO.findOneById(orgName) match {
      case None =>
        members foreach verifyUser
        val obj = Organization(orgName, name, ontUri, members)

        Try(orgsDAO.insert(obj, WriteConcern.Safe)) match {
          case Success(r) => OrgResult(orgName, registered = Some(obj.registered))

          case Failure(exc)  => error(500, s"insert failure = $exc")
          // TODO note that it might be a duplicate key in concurrent registration
        }

      case Some(ont) => error(400, s"'$orgName' organization already registered")
    }
  }

  put("/:orgName") {
    val orgName = require(params, "orgName")
    val org = getOrg(orgName)
    val map = body()
    var update = org

    if (map.contains("name")) {
      update = update.copy(name = require(map, "name"))
    }
    if (map.contains("ontUri")) {
      update = update.copy(ontUri = Some(require(map, "ontUri")))
    }
    if (map.contains("members")) {
      val members = getSeq(map, "members")
      members foreach verifyUser
      update = update.copy(members = members)
    }
    val updated = Some(DateTime.now())
    update = update.copy(updated = updated)
    logger.info(s"updating organization with: $update")
    Try(orgsDAO.update(MongoDBObject("_id" -> orgName), update, false, false, WriteConcern.Safe)) match {
      case Success(result) => OrgResult(orgName, updated = update.updated)
      case Failure(exc)    => error(500, s"update failure = $exc")
    }
  }

  delete("/:orgName") {
    val orgName = require(params, "orgName")
    val org = getOrg(orgName)
    Try(orgsDAO.remove(org, WriteConcern.Safe)) match {
      case Success(result) => OrgResult(orgName, removed = Some(DateTime.now())) //TODO

      case Failure(exc)  => error(500, s"update failure = $exc")
    }
  }

  post("/!/deleteAll") {
    val map = body()
    val pw = require(map, "pw")
    val special = setup.mongoConfig.getString("pw_special")
    if (special == pw) orgsDAO.remove(MongoDBObject()) else halt(401)
  }

  def getOrgJson(org: Organization) = {
    // TODO what exactly to report?
    val res = PendOrgResult(org.orgName, org.name, org.ontUri,
      registered = Some(org.registered))
    grater[PendOrgResult].toCompactJSON(res)
  }
}
