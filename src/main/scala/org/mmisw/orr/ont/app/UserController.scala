package org.mmisw.orr.ont.app

import com.mongodb.casbah.Imports._
import com.novus.salat._
import com.novus.salat.global._
import com.typesafe.scalalogging.{StrictLogging => Logging}
import org.joda.time.DateTime
import org.mmisw.orr.ont._
import org.mmisw.orr.ont.service.UserService
import org.scalatra.Created

import scala.util.{Failure, Success, Try}


class UserController(implicit setup: Setup) extends BaseController
      with Logging {

  val userService = new UserService

  createAdminIfMissing()

  /*
   * Gets all users
   */
  get("/") {
    usersDAO.find(MongoDBObject()) map getUserJson
  }

  /*
   * Gets a user.
   */
  get("/:userName") {
    val userName = require(params, "userName")
    getUserJson(getUser(userName))
  }

  // username reminder
  put("/unr/") {
    val map = body()
    val email = require(map, "email")

    new Thread(new Runnable {
      def run() {
        userService.sendUsername(email)
      }
    }).start()

    UsernameReminderResult(email,
      message = Some("If you have an account with this address, you should receive an email in a few minutes.")
    )
  }

  // requests password to be reset
  // TODO mechanism to avoid/reduce DoS attacks
  put("/rpwr/:userName") {
    val userName = require(params, "userName")
    val user = getUser(userName)

    new Thread(new Runnable {
      def run() {
        val resetRoute = s"$getMyBaseUrl/api/v0/user/auth/reset/"
        userService.requestResetPassword(user, resetRoute)
      }
    }).start()

    PasswordResetResult(user.userName,
      email = Some(user.email),
      message = Some(
        s"""An email with password reset instructions is on its way to: ${user.email}.
           | If you don't receive it within a few minutes, please try again later.
         """.stripMargin)
    )
  }

  /*
   * Dispatches HTML form for the user to set a new password.
   */
  get("/auth/reset/:token") {
    val token = require(params, "token")

    contentType = formats("html")

    val pwr = setup.db.pwrDAO.findOneById(token).getOrElse(
      halt(404, s"invalid or expired token"))

//    if (pwr.expiration.compareTo(DateTime.now()) > 0)
//      error(400, "this link has expired.")

    val action = request.getRequestURL.toString

    <html>
      <body>
        <h3>
          Set your new password:
        </h3>
        <form action="$action" method="post">
          <div>
            <label for="username">Username:</label>
            <input type="text" id="username" name="username"/>
          </div>
          <div>
            <label for="mail">E-mail:</label>
            <input type="email" id="mail" name="email"/>
          </div>
          <div>
            <label for="password">Password:</label>
            <input type="password" id="password" name="password"/>
          </div>
          <div>
            <label for="password2">Password again:</label>
            <input type="password" id="password2" name="password2"/>
          </div>
          <div>
            <input type="hidden" value="$token" name="token"/>
          </div>
          <div class="button">
            <button type="submit">Submit</button>
          </div>
        </form>
      </body>
    </html>.toString()
      .replace("$pwr", pwr.toString)
      .replace("$action", action)
      .replace("$token", token)
  }

  /*
   * Form action for setting new password.
   */
  post("/auth/reset/:token") {
    val token = require(params, "token")
    val userName = require(params, "username")
    val email = require(params, "email")
    val password = require(params, "password")
    val password2 = require(params, "password2")

    contentType = formats("html")

    val pwr = setup.db.pwrDAO.findOneById(token).getOrElse(
      halt(404, s"invalid token"))

//    if (pwr.expiration.compareTo(DateTime.now()) > 0)
//      error(400, "sorry, this link has expired.")

    val user = getUser(pwr.userName)

    if (email != user.email)
      error(400, "invalid information; click Back in your browser and try again.")

    // TODO real password validation!
    if (password.length < 8)
      error(400, "password is too short")
    if (password != password2)
      error(400, "passwords don't match")

    // all ok, update password:
    val update = user.copy(password = userAuth.encryptPassword(password))
    Try(usersDAO.update(MongoDBObject("_id" -> userName), update, false, false, WriteConcern.Safe)) match {
      case Success(result) =>
      case Failure(exc)    => error(500, s"update failure = $exc")
    }

    // remove token
    Try(setup.db.pwrDAO.remove(pwr, WriteConcern.Safe)) match {
      case Success(result) =>
      case Failure(exc)    => error(500, s"update failure = $exc")
    }

    // TODO: update firebase.

    userService.notifyPasswordHasBeenReset(user)

    <html>
      <body>
        <h3>
          Your password has been reset.
        </h3>
        Continue to <a href="$orrLink">$orrLink</a>
        and sign in with your new password.
      </body>
    </html>.toString()
      .replace("$orrLink", getMyBaseUrl)
  }

  /*
   * Registers a new user.
   */
  post("/") {
    // TODO: re-captcha to verify this is a request from a human
    // TODO: email with account confirmation link

    val map = body()

    val userName  = require(map, "userName")
    val email     = require(map, "email")
    val firstName = require(map, "firstName")
    val lastName  = require(map, "lastName")
    val password  = require(map, "password")
    val phone     = getString(map, "phone")
    val ontUri    = getString(map, "ontUri")

    Created(createUser(userName, email, firstName, lastName, password, phone, ontUri))
  }

  post("/auth") {
    val map = body()
    val userName  = require(map, "userName")
    val password  = require(map, "password")
    val user = getUser(userName)

    if (userAuth.checkPassword(password, user)) UserResult(userName,
      role = if (extra.contains(userName)) Some("extra") else None)
    else error(401, "bad password")
  }

//  ////////////////////////////////////////////////////////////////////////
//  /*
//   * Creates a user session.
//   * userName and password can be given as parameters:
//   *    http post :8081/api/v0/user/session\?userName=uuu\&password=ppp
//   * or via basic authentication:
//   *    http -a uuu:ppp post :8081/api/v0/user/session
//   * ...
//   *    Set-Cookie: JSESSIONID=1e8r7k3thzddy1sc28m1gdub09;...
//   */
//  post("/session") {
//    val userNameOpt = params.get("userName")
//    val passwordOpt = params.get("password")
//    val userName = createSession(userNameOpt, passwordOpt)
//    val role = if (extra.contains(userName)) Some("extra") else None
//    UserResult(userName, role = role)
//  }
//
//  /*
//   * route for testing the authentication verification:
//   *   http post :8081/api/v0/user/chksession/uuu 'Cookie:JSESSIONID=1e8r7k3thzddy1sc28m1gdub09'
//   */
//  post("/chksession/:userName") {
//    val userName = require(params, "userName")
//    verifySession(userName)
//    UserResult(userName)
//  }
//
  ////////////////////////////////////////////////////////////////////////

  /*
   * Updates a user account.
   * Only the same user or "admin" can do this.
   */
  put("/:userName") {
    val userName = require(params, "userName")
    logger.debug(s"PUT userName=$userName")
    val map = body()
    verifyIsAuthenticatedUser(userName, "admin")

    userService.updateUser(userName, toStringMap(map), Some(DateTime.now()))
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

  def getUserJson(dbUser: db.User) = {
    var res = UserResult(
      userName   = dbUser.userName,
      firstName  = Some(dbUser.firstName),
      lastName   = Some(dbUser.lastName),
      ontUri     = dbUser.ontUri
    )
    if (checkIsUserOrAdminOrExtra(dbUser.userName)) {
      res = res.copy(
        email      = Some(dbUser.email),
        phone      = dbUser.phone,
        registered = Some(dbUser.registered),
        updated    = dbUser.updated
      )
    }
    grater[UserResult].toCompactJSON(res)
  }

  def createUser(userName: String,
                 email: String,
                 firstName: String, lastName: String,
                 password: String,
                 phone: Option[String] = None,
                 ontUri: Option[String] = None
                ) = {

    usersDAO.findOneById(userName) match {
      case None =>
        val encPassword = userAuth.encryptPassword(password)
        val obj = db.User(userName, firstName, lastName, encPassword, email, ontUri, phone)

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
