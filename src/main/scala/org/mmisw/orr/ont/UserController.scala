package org.mmisw.orr.ont

import com.mongodb.casbah.Imports._
import com.typesafe.scalalogging.slf4j.Logging

import org.jasypt.util.password.StrongPasswordEncryptor



class UserController(implicit setup: Setup) extends OrrOntStack
with SimpleMongoDbJsonConversion with Logging {

  val users = setup.db.usersColl

  val passwordEnc = new StrongPasswordEncryptor

  get("/") {
    users.find()
  }

  post("/") {
    val map = body()

    val userName  = require(map, "userName")
    val firstName = require(map, "firstName")
    val lastName  = require(map, "lastName")
    val password  = require(map, "password")

    val encPassword = passwordEnc.encryptPassword(password)

    val now = new java.util.Date()
    val date    = dateFormatter.format(now)

    val q = MongoDBObject("userName" -> userName)
    users.findOne(q) match {
      case None =>
        val obj = MongoDBObject(
          "userName"    -> userName,
          "firstName"   -> firstName,
          "lastName"    -> lastName,
          "password"    -> encPassword,
          "registered"  -> date
        )
        users += obj
        User(userName, firstName, lastName, registered = Some(date))

      case Some(ont) => error(400, s"'$userName' already registered")
    }
  }

  post("/chkpw") {
    val map = body()

    val userName  = require(map, "userName")
    val password  = require(map, "password")

    val q = MongoDBObject("userName" -> userName)
    users.findOne(q) match {
      case None =>
        error(404, s"'$userName' not registered")

      case Some(ont) =>
        val encPassword = ont.as[String]("password")
        if (!passwordEnc.checkPassword(password, encPassword))
          error(401, "bad password")

        UserResult(userName, "password ok")
    }
  }

  put("/") {
    val map = body()

    val userName = require(map, "userName")

    val obj = MongoDBObject("userName" -> userName)
    users.findOne(obj) match {
      case None =>
        error(404, s"'$userName' is not registered")

      case Some(found) =>
        logger.info(s"found user: $found")
        val update = found
        List("firstName", "lastName") foreach { k =>
          if (map.contains(k)) {
            update.put(k, map.get(k).head)
          }
        }
        if (map.contains("password")) {
          val password = require(map, "password")
          val encPassword = passwordEnc.encryptPassword(password)
          update.put("password", encPassword)
        }
        logger.info(s"updating user with: $update")
        val result = users.update(obj, update)
        UserResult(userName, s"updated (${result.getN})")
    }
  }

  delete("/") {
    val userName: String = params.getOrElse("userName", missing("userName"))
    val obj = MongoDBObject("userName" -> userName)
    val result = users.remove(obj)
    UserResult(userName, s"removed (${result.getN})")
  }

  post("/!/deleteAll") {
    val map = body()
    val pw = require(map, "pw")
    val special = setup.mongoConfig.getString("pw_special")
    if (special == pw) users.remove(MongoDBObject()) else halt(401)
  }

}
