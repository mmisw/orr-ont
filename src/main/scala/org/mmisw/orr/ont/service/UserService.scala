package org.mmisw.orr.ont.service

import com.mongodb.casbah.Imports._
import com.typesafe.scalalogging.{StrictLogging => Logging}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.mmisw.orr.ont.db.{PwReset, User}
import org.mmisw.orr.ont._
import org.mmisw.orr.ont.util.IEmailer

import scala.util.{Failure, Success, Try}

/**
 * User service
 */
class UserService(implicit setup: Setup, emailer: IEmailer) extends BaseService(setup) with Logging {

  createAdminIfMissing()

  private val pwrDAO = setup.db.pwrDAO

  /**
   * Gets the users satisfying the given query.
    *
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
   * Sends email with reminder of username(s).
   * TODO username-email relationship to be decided
   */
  def sendUsername(email: String): Unit = {
    logger.debug(s"sendUsername: email=$email")

    val dtFormatter = DateTimeFormat.forPattern("YYYY-MM-dd")
    def getEmailText(users: Seq[db.User]): String = {

      val fmt = "%s - %-12s - %s"
      val header = fmt.format("Registered", "Username", "Full name")
      def user2info(u: db.User): String =
        fmt.format(dtFormatter.print(u.registered), u.userName, u.firstName+ " " + u.lastName)

      val be = if (users.size > 1) "s are" else " is"
      s"""
        |Hi $email,
        |
        |A request has been received to send a reminder of account information
        |associated with your email address.
        |
        |The following account$be associated:
        |    $header
        |    ${users.map(user2info).mkString("\n    ")}
        |
        |The orr-ont team
        """.stripMargin
    }

    val query = MongoDBObject("email" -> email)
    val users = usersDAO.find(query).toSeq.sortBy(_.registered.toDate)

    if (users.nonEmpty) {
      val emailText = getEmailText(users)
      println(s"sendUsername: email=$email: emailText:\n$emailText")
      try {
        emailer.sendEmail(email,
          s"Your orr-ont username${if (users.size > 1) "s" else ""}",
          emailText)
      }
      catch {
        case exc:Exception => exc.printStackTrace()
      }
    }
    else println(s"sendUsername: email=$email: no associated usernames")
  }

  /**
   * Generates email so the user can reset her/his password.
   */
  def requestResetPassword(user: db.User, resetRoute: String): Unit = {
    logger.debug(s"request password reset for userName=${user.userName} (resetRoute=$resetRoute)")

    def getEmailText(resetLink: String): String = {
      s"""
        | Hi ${user.firstName} ${user.lastName},
        |
        | You have requested to reset your password at the orr-ont.
        |
        | Please visit this link to reset it:
        |   $resetLink
        |
        | Your account:
        |    username: ${user.userName}
        |    email:    ${user.email}
        |
        | If you did not make this request, please disregard this email.
        |
        | The orr-ont team
        """.stripMargin
    }

    val token = java.util.UUID.randomUUID().toString

    val expiration = DateTime.now().plusHours(24)
    val obj = PwReset(token, user.userName, expiration)

    Try(pwrDAO.insert(obj, WriteConcern.Safe)) match {
      case Success(r) =>
        val emailText = getEmailText(s"$resetRoute$token")
        println(s"resetPassword: PwReset: $obj emailText:\n$emailText")
        try {
          emailer.sendEmail(user.email,
            "Reset your orr-ont password",
            emailText)
        }
        catch {
          case exc:Exception => exc.printStackTrace()
        }

      case Failure(exc) => exc.printStackTrace()
    }
  }

  def notifyPasswordHasBeenReset(user: db.User): Unit = {
    val emailText =
      s"""
         |Your orr-ont password has been changed.
         |
         | Your account:
         |    username: ${user.userName}
         |    email:    ${user.email}
         |
         | The orr-ont team
       """.stripMargin
    logger.debug(s"notifyPasswordReset:\n$emailText")

    try {
      emailer.sendEmail(user.email,
        "orr-ont password change confirmation",
        emailText
      )
    }
    catch {
      case exc:Exception => exc.printStackTrace()
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
