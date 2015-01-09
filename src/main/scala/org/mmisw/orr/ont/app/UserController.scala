package org.mmisw.orr.ont.app

import com.mongodb.casbah.Imports._
import com.novus.salat._
import com.novus.salat.global._
import com.typesafe.scalalogging.slf4j.Logging
import org.joda.time.DateTime
import org.mmisw.orr.ont.db
import org.mmisw.orr.ont.{PendUserResult, Setup, UserResult}
import org.scalatra.Created

import scala.util.{Failure, Success, Try}


class UserController(implicit setup: Setup) extends BaseController
      with Logging {

  createAdminIfMissing()

  /*
   * Gets all users
   */
  get("/") {
    usersDAO.find(MongoDBObject()) map getUserJson
  }

  /*
   * Gets a user
   */
  get("/:userName") {
    val userName = require(params, "userName")
    getUserJson(getUser(userName))
  }

  /*
   * Registers a new user.
   * Only "admin" can do this.
   */
  post("/") {
    verifyAuthenticatedUser("admin")
    val map = body()

    val userName  = require(map, "userName")
    val firstName = require(map, "firstName")
    val lastName  = require(map, "lastName")
    val password  = require(map, "password")
    val email     = require(map, "email")
    val ontUri    = getString(map, "ontUri")

    Created(createUser(userName, firstName, lastName, password, email, ontUri))
  }

  post("/chkpw") {
    val map = body()
    val userName  = require(map, "userName")
    val password  = require(map, "password")
    val user = getUser(userName)

    if (userAuth.checkPassword(password, user)) UserResult(userName)
    else error(401, "bad password")
  }

  /*
   * Updates a user account.
   * Only the same user or "admin" can do this.
   */
  put("/:userName") {
    val userName = require(params, "userName")
    logger.debug(s"PUT userName=$userName")
    val map = body()
    verifyAuthenticatedUser(userName, "admin")

    var update = getUser(userName)

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

  /*
   * Removes a user account.
   * Only "admin" can do this.
   */
  delete("/:userName") {
    verifyAuthenticatedUser("admin")
    val userName = require(params, "userName")
    val user = getUser(userName)
    deleteUser(user)
  }

  // for initial testing of authentication from unit tests
  post("/!/testAuth") {
    verifyAuthenticatedUser("admin")
    UserResult("admin")
  }

  delete("/!/all") {
    verifyAuthenticatedUser("admin")
    usersDAO.remove(MongoDBObject())
  }

  ///////////////////////////////////////////////////////////////////////////

  def getUserJson(user: db.User) = {
    // TODO what exactly to report?
    val res = PendUserResult(user.userName, user.ontUri, registered = Some(user.registered))
    grater[PendUserResult].toCompactJSON(res)
  }

  def createUser(userName: String, firstName: String, lastName: String, password: String, email: String,
                 ontUri: Option[String] = None) = {

    val encPassword = userAuth.encryptPassword(password)

    usersDAO.findOneById(userName) match {
      case None =>
        val obj = db.User(userName, firstName, lastName, encPassword, email, ontUri)

        Try(usersDAO.insert(obj, WriteConcern.Safe)) match {
          case Success(r) => UserResult(userName, registered = Some(obj.registered))

          case Failure(exc)  => error(500, s"insert failure = $exc")
          // TODO note that it might be a duplicate key in concurrent registration
        }

      case Some(_) => error(400, s"'$userName' already registered")
    }
  }

  /**
   * Creates the "admin" user if not already in the database.
   * This is called on initialization of this controller.
   */
  def createAdminIfMissing() {
    val admin = "admin"
    usersDAO.findOneById(admin) match {
      case None =>
        logger.debug("creating 'admin' user")
        val password = setup.config.getString("admin.password")
        val encPassword = userAuth.encryptPassword(password)
        val obj = db.User(admin, "Adm", "In", encPassword, setup.config.getString("admin.email"))

        Try(usersDAO.insert(obj, WriteConcern.Safe)) match {
          case Success(r) =>
            logger.info(s"'admin' user created: ${obj.registered}")

          case Failure(exc)  =>
            logger.error(s"error creating 'admin' user", exc)
        }

      case Some(_) => // nothing
    }
  }

  def deleteUser(user: db.User) = {
    Try(usersDAO.remove(user, WriteConcern.Safe)) match {
      case Success(result) => UserResult(user.userName, removed = Some(DateTime.now()))
      case Failure(exc)    => error(500, s"update failure = $exc")
    }
  }

}
