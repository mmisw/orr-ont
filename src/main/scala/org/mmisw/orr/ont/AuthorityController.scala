package org.mmisw.orr.ont

import com.mongodb.casbah.Imports._
import com.typesafe.scalalogging.slf4j.Logging
import org.mmisw.orr.ont.db.Authority
import scala.util.{Failure, Success, Try}
import com.novus.salat._
import com.novus.salat.global._
import org.joda.time.DateTime


class AuthorityController(implicit setup: Setup) extends BaseController
    with Logging {

  def getAuthorityJson(authority: Authority) = {
    // TODO what exactly to report?
    val res = PendAuthorityResult(authority.authName, authority.ontUri,
      registered = Some(authority.registered))
    grater[PendAuthorityResult].toCompactJSON(res)
  }

  get("/") {
    authoritiesDAO.find(MongoDBObject()) map getAuthorityJson
  }

  get("/:authName") {
    val authName = require(params, "authName")
    getAuthorityJson(getAuthority(authName))
  }

  // http post localhost:8080/authority authName=mmi name="mmi project" ontUri=http://mmisw.org/ont/mmi members:='["carueda"]'
  post("/") {
    val map = body()

    logger.info(s"POST body = $map")
    val authName   = require(map, "authName")
    val name       = require(map, "name")
    val ontUri     = getString(map, "ontUri")
    val members    = getSeq(map, "members")

    authoritiesDAO.findOneById(authName) match {
      case None =>
        members foreach verifyUser
        val obj = Authority(authName, name, ontUri, members)

        Try(authoritiesDAO.insert(obj, WriteConcern.Safe)) match {
          case Success(r) => AuthorityResult(authName, registered = Some(obj.registered))

          case Failure(exc)  => error(500, s"insert failure = $exc")
          // TODO note that it might be a duplicate key in concurrent registration
        }

      case Some(ont) => error(400, s"'$authName' already registered")
    }
  }

  put("/") {
    val map = body()
    val authName = require(map, "authName")
    val authority = getAuthority(authName)
    var update = authority

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
    logger.info(s"updating authority with: $update")
    Try(authoritiesDAO.update(MongoDBObject("_id" -> authName), update, false, false, WriteConcern.Safe)) match {
      case Success(result) => AuthorityResult(authName, updated = update.updated)
      case Failure(exc)    => error(500, s"update failure = $exc")
    }
  }

  delete("/") {
    val authName = require(params, "authName")
    val authority = getAuthority(authName)
    Try(authoritiesDAO.remove(authority, WriteConcern.Safe)) match {
      case Success(result) => AuthorityResult(authName, removed = Some(DateTime.now())) //TODO

      case Failure(exc)  => error(500, s"update failure = $exc")
    }
  }

  post("/!/deleteAll") {
    val map = body()
    val pw = require(map, "pw")
    val special = setup.mongoConfig.getString("pw_special")
    if (special == pw) authoritiesDAO.remove(MongoDBObject()) else halt(401)
  }
}
