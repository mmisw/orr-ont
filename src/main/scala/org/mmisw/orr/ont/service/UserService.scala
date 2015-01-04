package org.mmisw.orr.ont.service

import com.mongodb.casbah.Imports._
import com.typesafe.scalalogging.slf4j.Logging
import org.joda.time.DateTime
import org.mmisw.orr.ont.db.User
import org.mmisw.orr.ont.{UserResult, PendUserResult, Setup}

import scala.util.{Failure, Success, Try}


/**
 * User service
 */
class UserService(implicit setup: Setup) extends BaseService(setup) with Logging {

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

  /**
   * Creates a new user.
   */
  def createUser(userName: String, emailOpt: Option[String], phoneOpt: Option[String],
                 firstName: String, lastName: String, password: String,
                 ontUri: Option[String]) = {

    usersDAO.findOneById(userName) match {
      case None =>
        validateUserName(userName)
        validateEmail(emailOpt)
        validatePhone(phoneOpt)

        val user = User(userName, firstName, lastName, password, emailOpt, ontUri, phoneOpt)

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
  def updateUserVersion(userName: String, map: Map[String,String]) = {
    var update = getUser(userName)

    if (map.contains("email")) {
      update = update.copy(email = map.get("email"))
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
      val password = map.get("password").get
      val encPassword = userAuth.encryptPassword(password)
      update = update.copy(password = encPassword)
    }
    if (map.contains("ontUri")) {
      update = update.copy(ontUri = map.get("ontUri"))
    }
    val updated = Some(DateTime.now())
    update = update.copy(updated = updated)
    logger.info(s"updating user with: $update")

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

  private def validateUserName(userName: String) {
    if (userName.length == 0
      || !userName.forall(Character.isJavaIdentifierPart)
      || !Character.isJavaIdentifierStart(userName(0))) {
      throw InvalidUserName(userName)
    }
  }

  // TODO validate email
  private def validateEmail(emailOpt: Option[String]) {
  }

  // TODO validate phone
  private def validatePhone(phoneOpt: Option[String]) {
  }

}
