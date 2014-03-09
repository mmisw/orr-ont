package org.mmisw.orr.ont

import com.mongodb.casbah.Imports._
import com.typesafe.scalalogging.slf4j.Logging
import org.mmisw.orr.ont.db.Authority
import scala.util.{Failure, Success, Try}
import com.novus.salat._
import com.novus.salat.global._



class AuthorityController(implicit setup: Setup) extends OrrOntStack
    with SimpleMongoDbJsonConversion with Logging {

  val authoritiesDAO = setup.db.authoritiesDAO

  get("/") {
    authoritiesDAO.find(MongoDBObject()) map grater[Authority].toCompactJSON
  }

  // http post localhost:8080/authority shortName=mmi name="mmi project" ontUri=http://mmisw.org/ont/mmi members:='["carueda"]'
  post("/") {
    val map = body()

    logger.info(s"POST body = $map")
    val shortName  = require(map, "shortName")
    val name       = require(map, "name")
    val ontUri     = getString(map, "ontUri")
    val members    = getSeq(map, "members")

    authoritiesDAO.findOneById(shortName) match {
      case None =>
        val obj = Authority(shortName, name, ontUri, members)

        Try(authoritiesDAO.insert(obj, WriteConcern.Safe)) match {
          case Success(r) =>
            logger.debug(s"insert result = '$r'")
            AuthorityResult("authority registered", shortName)

          case Failure(exc)  => error(500, s"insert failure = $exc")
          // TODO note that it might be a duplicate key in concurrent registration
        }

      case Some(ont) => error(400, s"'$shortName' already registered")
    }
  }

  put("/") {
    val map = body()
    val shortName = require(map, "shortName")
    authoritiesDAO.findOneById(shortName) match {
      case None =>
        error(404, s"'$shortName' is not registered")

      case Some(found) =>
        logger.info(s"found authority: $found")
        var update = found

        if (map.contains("name")) {
          update = update.copy(name = require(map, "name"))
        }
        if (map.contains("ontUri")) {
          update = update.copy(ontUri = Some(require(map, "ontUri")))
        }
        logger.info(s"updating authority with: $update")
        Try(authoritiesDAO.update(MongoDBObject("shortName" -> shortName), update, false, false, WriteConcern.Safe)) match {
          case Success(result) => AuthorityResult(shortName, s"updated (${result.getN})")
          case Failure(exc)    => error(500, s"update failure = $exc")
        }
    }
  }

  delete("/") {
    val shortName = require(params, "shortName")
    authoritiesDAO.findOneById(shortName) match {
      case None => error(404, s"'$shortName' is not registered")

      case Some(authority) =>
        Try(authoritiesDAO.remove(authority, WriteConcern.Safe)) match {
          case Success(result) => AuthorityResult(shortName, s"removed (${result.getN})")

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
