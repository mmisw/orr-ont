package org.mmisw.orr.ont.service

import com.mongodb.casbah.Imports._
import com.typesafe.scalalogging.{StrictLogging => Logging}
import org.joda.time.DateTime
import org.mmisw.orr.ont.db.User
import org.mmisw.orr.ont.{db, UserResult, PendUserResult, Setup}

import scala.util.{Failure, Success, Try}

/**
 * User service
 */
class UserService(implicit setup: Setup) extends BaseService(setup) with Logging {

  createAdminIfMissing()

  /**
   * Gets the users satisfying the given query.
   * @param query  Query
   * @return       iterator
   */
  def getUsers(query: MongoDBObject): Iterator[PendUserResult] = {
    usersDAO.find(query) map { user =>
        PendUserResult(user.userName, user.ontUri, Some(user.registered))
    }
  }

  def existsUser(userName: String): Boolean = usersDAO.findOneById(userName).isDefined

  /**
   * Creates a new user.
   */
  def createUser(userName: String, email: String, phoneOpt: Option[String],
                 firstName: String, lastName: String, password: Either[String,String],
                 ontUri: Option[String], registered: DateTime = DateTime.now()) = {

    usersDAO.findOneById(userName) match {
      case None =>
        validateUserName(userName)
        validateEmail(email)
        validatePhone(phoneOpt)

        val encPassword = password.fold(
          clearPass => userAuth.encryptPassword(clearPass),
          encPass   => encPass
        )
        val user = User(userName, firstName, lastName, encPassword, email, ontUri, phoneOpt, registered)

        Try(usersDAO.insert(user, WriteConcern.Safe)) match {
          case Success(_) =>
            UserResult(userName, registered = Some(user.registered))

          case Failure(exc) => throw CannotInsertUser(userName, exc.getMessage)
              // perhaps duplicate key in concurrent registration
        }

      case Some(_) => throw UserAlreadyRegistered(userName)
    }
  }

  /**
   * Updates a user.
   */
  def updateUser(userName: String, map: Map[String,String], updated: Option[DateTime] = None) = {

    var update = getUser(userName)

    if (map.contains("email")) {
      update = update.copy(email = map.get("email").get)
    }
    if (map.contains("phone")) {
      update = update.copy(phone = map.get("phone"))
    }
    if (map.contains("firstName")) {
      update = update.copy(firstName = map.get("firstName").get)
    }
    if (map.contains("lastName")) {
      update = update.copy(lastName = map.get("lastName").get)
    }

    if (map.contains("password")) {
      val encPassword = userAuth.encryptPassword(map.get("password").get)
      update = update.copy(password = encPassword)
    }
    else if (map.contains("encPassword")) {
      update = update.copy(password = map.get("encPassword").get)
    }

    if (map.contains("ontUri")) {
      update = update.copy(ontUri = map.get("ontUri"))
    }

    updated foreach {u => update = update.copy(updated = Some(u))}
    //logger.debug(s"updating user with: $update")

    Try(usersDAO.update(MongoDBObject("_id" -> userName), update, false, false, WriteConcern.Safe)) match {
      case Success(result) =>
        UserResult(userName, updated = update.updated)

      case Failure(exc)  => throw CannotUpdateUser(userName, exc.getMessage)
    }
  }

  /**
   * Deletes a whole user entry.
   */
  def deleteUser(userName: String) = {
    val user = getUser(userName)

    Try(usersDAO.remove(user, WriteConcern.Safe)) match {
      case Success(result) =>
        UserResult(userName, removed = Some(DateTime.now())) //TODO

      case Failure(exc)  => throw CannotDeleteUser(userName, exc.getMessage)
    }
  }

  /**
   * Deletes the whole users collection
   */
  def deleteAll() = usersDAO.remove(MongoDBObject())

  ///////////////////////////////////////////////////////////////////////////

  private def getUser(userName: String): User = usersDAO.findOneById(userName).getOrElse(throw NoSuchUser(userName))

  /*
   * TODO validate userName
   *  For migration from previous database note that there are userNames
   *  with spaces, with periods, and even some emails.
   * Actually, email would be a desirable ID in general: review this.
   **/
  private def validateUserName(userName: String) {
  }

  // TODO validate email
  private def validateEmail(email: String) {
  }

  // TODO validate phone
  private def validatePhone(phoneOpt: Option[String]) {
  }

  /**
   * Creates the "admin" user if not already in the database.
   */
  def createAdminIfMissing() {
    val admin = "admin"
    usersDAO.findOneById(admin) match {
      case None =>
        logger.debug("creating 'admin' user")
        val password    = setup.config.getString("admin.password")
        val encPassword = userAuth.encryptPassword(password)
        val email       = setup.config.getString("admin.email")
        val obj = db.User(admin, "Adm", "In", encPassword, email)

        Try(usersDAO.insert(obj, WriteConcern.Safe)) match {
          case Success(r) =>
            logger.info(s"'admin' user created: ${obj.registered}")

          case Failure(exc)  =>
            logger.error(s"error creating 'admin' user", exc)
        }

      case Some(_) => // nothing
    }
  }

}
