package org.mmisw.orr.ont

import com.mongodb.casbah.Imports._
import com.typesafe.scalalogging.slf4j.Logging
import org.mmisw.orr.ont.db.Authority
import scala.util.{Failure, Success, Try}
import com.novus.salat._
import com.novus.salat.global._
import org.joda.time.DateTime


class AuthorityController(implicit setup: Setup) extends OrrOntStack
    with SimpleMongoDbJsonConversion with Logging {

  val authoritiesDAO = setup.db.authoritiesDAO


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
    authoritiesDAO.findOneById(authName) match {
      case Some(authority) => getAuthorityJson(authority)
      case None => error(404, s"'$authName' is not registered")
    }
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
    authoritiesDAO.findOneById(authName) match {
      case None =>
        error(404, s"'$authName' is not registered")

      case Some(found) =>
        logger.info(s"found authority: $found")
        var update = found

        if (map.contains("name")) {
          update = update.copy(name = require(map, "name"))
        }
        if (map.contains("ontUri")) {
          update = update.copy(ontUri = Some(require(map, "ontUri")))
        }
        update = update.copy(updated = Some(DateTime.now()))
        logger.info(s"updating authority with: $update")
        Try(authoritiesDAO.update(MongoDBObject("_id" -> authName), update, false, false, WriteConcern.Safe)) match {
          case Success(result) => AuthorityResult(authName, updated = Some(DateTime.now())) //TODO
          case Failure(exc)    => error(500, s"update failure = $exc")
        }
    }
  }

  delete("/") {
    val authName = require(params, "authName")
    authoritiesDAO.findOneById(authName) match {
      case None => error(404, s"'$authName' is not registered")

      case Some(authority) =>
        Try(authoritiesDAO.remove(authority, WriteConcern.Safe)) match {
          case Success(result) => AuthorityResult(authName, removed = Some(DateTime.now())) //TODO

          case Failure(exc)  => error(500, s"update failure = $exc")
        }
    }
  }

  post("/!/deleteAll") {
    val map = body()
    val pw = require(map, "pw")
    val special = setup.mongoConfig.getString("pw_special")
    if (special == pw) authoritiesDAO.remove(MongoDBObject()) else halt(401)
  }
}
