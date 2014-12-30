package org.mmisw.orr.ont

import com.mongodb.casbah.Imports._
import com.typesafe.scalalogging.slf4j.Logging

import org.mmisw.orr.ont.db.User
import scala.util.{Failure, Success, Try}
import com.novus.salat._
import com.novus.salat.global._
import org.joda.time.DateTime


class UserController(implicit setup: Setup) extends BaseController
      with Logging {

  get("/") {
    usersDAO.find(MongoDBObject()) map getUserJson
  }

  get("/:userName") {
    val userName = require(params, "userName")
    getUserJson(getUser(userName))
  }

  post("/") {
    val map = body()

    val userName  = require(map, "userName")
    val firstName = require(map, "firstName")
    val lastName  = require(map, "lastName")
    val password  = require(map, "password")
    val ontUri    = getString(map, "ontUri")

    val encPassword = userAuth.encryptPassword(password)

    usersDAO.findOneById(userName) match {
      case None =>
        val obj = User(userName, firstName, lastName, encPassword, ontUri)

        Try(usersDAO.insert(obj, WriteConcern.Safe)) match {
          case Success(r) => UserResult(userName, registered = Some(obj.registered))

          case Failure(exc)  => error(500, s"insert failure = $exc")
          // TODO note that it might be a duplicate key in concurrent registration
        }

      case Some(ont) => error(400, s"'$userName' already registered")
    }
  }

  post("/chkpw") {
    val map = body()
    val userName  = require(map, "userName")
    val password  = require(map, "password")
    val user = getUser(userName)

    if (userAuth.checkPassword(password, user)) UserResult(userName)
    else error(401, "bad password")
  }

  put("/") {
    val map = body()

    val userName = require(map, "userName")
    val user = getUser(userName)
    var update = user

    if (map.contains("firstName")) {
      update = update.copy(firstName = require(map, "firstName"))
    }
    if (map.contains("lastName")) {
      update = update.copy(lastName = require(map, "lastName"))
    }
    if (map.contains("password")) {
      val password = require(map, "password")
      val encPassword = userAuth.encryptPassword(password)
      update = update.copy(password = encPassword)
    }
    if (map.contains("ontUri")) {
      update = update.copy(ontUri = Some(require(map, "ontUri")))
    }
    val updated = Some(DateTime.now())
    update = update.copy(updated = updated)
    logger.info(s"updating user with: $update")

    Try(usersDAO.update(MongoDBObject("_id" -> userName), update, false, false, WriteConcern.Safe)) match {
      case Success(result) => UserResult(userName, updated = update.updated)
      case Failure(exc)    => error(500, s"update failure = $exc")
    }
  }

  delete("/") {
    val userName = require(params, "userName")
    val user = getUser(userName)
    Try(usersDAO.remove(user, WriteConcern.Safe)) match {
      case Success(result) =>
        UserResult(userName, removed = Some(DateTime.now()))

      case Failure(exc)  => error(500, s"update failure = $exc")
    }
  }

  post("/!/deleteAll") {
    val map = body()
    val pw = require(map, "pw")
    val special = setup.mongoConfig.getString("pw_special")
    if (special == pw) usersDAO.remove(MongoDBObject()) else halt(401)
  }

  def getUserJson(user: User) = {
    // TODO what exactly to report?
    val res = PendUserResult(user.userName, user.ontUri, registered = Some(user.registered))
    grater[PendUserResult].toCompactJSON(res)
  }
}
