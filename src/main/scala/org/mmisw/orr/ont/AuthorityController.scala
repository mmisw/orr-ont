package org.mmisw.orr.ont

import com.mongodb.casbah.Imports._
import com.typesafe.scalalogging.slf4j.Logging


class AuthorityController(implicit setup: Setup) extends OrrOntStack
    with SimpleMongoDbJsonConversion with Logging {

  val authorities = setup.db.authoritiesColl

  get("/") {
    authorities.find()
  }

  post("/") {
    val map = body()

    logger.info(s"POST body = $map")
    val shortName  = require(map, "shortName")
    val name       = require(map, "name")
    val ontUri     = getString(map, "ontUri")
    val members    = getSeq(map, "members")

    val now = new java.util.Date()
    val date = dateFormatter.format(now)

    val q = MongoDBObject("shortName" -> shortName)
    authorities.findOne(q) match {
      case None =>
        val obj = MongoDBObject(
          "shortName"    -> shortName,
          "name"         -> name,
          "ontUri"       -> ontUri,
          "members"      -> members,
          "registered"   -> date
        )
        authorities += obj
        Authority(shortName, ontUri, registered = Some(date))

      case Some(ont) => error(400, s"'$shortName' already registered")
    }
  }

  put("/") {
    val map = body()

    val shortName = require(map, "shortName")

    val obj = MongoDBObject("shortName" -> shortName)
    authorities.findOne(obj) match {
      case None =>
        error(404, s"'$shortName' is not registered")

      case Some(found) =>
        logger.info(s"found authority: $found")
        val update = found
        List("ontUri") foreach { k =>
          if (map.contains(k)) {
            update.put(k, map.get(k).head)
          }
        }
        logger.info(s"updating authority with: $update")
        val result = authorities.update(obj, update)
        AuthorityResult(shortName, s"updated (${result.getN})")
    }
  }

  delete("/") {
    val shortName: String = params.getOrElse("shortName", missing("shortName"))
    val obj = MongoDBObject("shortName" -> shortName)
    val result = authorities.remove(obj)
    AuthorityResult(shortName, s"removed (${result.getN})")
  }

  post("/!/deleteAll") {
    val map = body()
    val pw = require(map, "pw")
    val special = setup.mongoConfig.getString("pw_special")
    if (special == pw) authorities.remove(MongoDBObject()) else halt(401)
  }
}
