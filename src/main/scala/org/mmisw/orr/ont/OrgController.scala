package org.mmisw.orr.ont

import com.mongodb.casbah.Imports._
import com.typesafe.scalalogging.slf4j.Logging
import org.mmisw.orr.ont.db.Organization
import scala.util.{Failure, Success, Try}
import com.novus.salat._
import com.novus.salat.global._
import org.joda.time.DateTime


class OrgController(implicit setup: Setup) extends BaseController
    with Logging {

  /*
   * Gets all organizations
   */
  get("/") {
    orgsDAO.find(MongoDBObject()) map getOrgJson
  }

  /*
   * Gets an organization
   */
  get("/:orgName") {
    val orgName = require(params, "orgName")
    getOrgJson(getOrg(orgName))
  }

  /*
   * Registers a new organization.
   * Only "admin" can do this.
   */
  post("/") {
    verifyAuthenticatedUser("admin")
    val map = body()

    logger.info(s"POST body = $map")
    val orgName = require(map, "orgName")
    val name = require(map, "name")
    val ontUri = getString(map, "ontUri")
    val members = getSeq(map, "members")

    createOrg(orgName, name, members, ontUri)
  }

  /*
   * Updates an organization.
   * Only members and "admin" can do this.
   */
  put("/:orgName") {
    val orgName = require(params, "orgName")
    val org = getOrg(orgName)

    verifyAuthenticatedUser(org.members :+ "admin": _*)

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

  /*
   * Deletes an organization.
   * Only "admin" can do this.
   */
  delete("/:orgName") {
    verifyAuthenticatedUser("admin")
    val orgName = require(params, "orgName")
    val org = getOrg(orgName)
    Try(orgsDAO.remove(org, WriteConcern.Safe)) match {
      case Success(result) => OrgResult(orgName, removed = Some(DateTime.now())) //TODO

      case Failure(exc)  => error(500, s"update failure = $exc")
    }
  }

  delete("/!/all") {
    verifyAuthenticatedUser("admin")
    orgsDAO.remove(MongoDBObject())
  }

  def createOrg(orgName: String, name: String, members: List[String], ontUri: Option[String] = None) = {
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

  def getOrgJson(org: Organization) = {
    // TODO what exactly to report?
    val res = PendOrgResult(org.orgName, org.name, org.ontUri,
      registered = Some(org.registered))
    grater[PendOrgResult].toCompactJSON(res)
  }
}
