package org.mmisw.orr.ont.auth

import org.mmisw.orr.ont.db
import org.jasypt.util.password.StrongPasswordEncryptor
import org.scalatra.ScalatraBase
import org.scalatra.auth.strategy.BasicAuthStrategy
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import com.novus.salat.dao.SalatDAO


object userAuth {
  private var authenticator: Authenticator = null

  def getAuthenticator = {
    require(authenticator != null, "authenticator must have been created already")
    authenticator
  }

  def apply(usersDAO: SalatDAO[db.User, String]) = {
    //require(authenticator == null, "authenticator must not have been created already")
    authenticator = new Authenticator(usersDAO)
    authenticator
  }
}

case class AuthUser(userName: String)

class Authenticator(usersDAO: SalatDAO[db.User, String]) {

  private val encryptor = new StrongPasswordEncryptor

  def encryptPassword(password: String) = encryptor.encryptPassword(password)

  def checkPassword(password: String, user: db.User) = encryptor.checkPassword(password, user.password)

  def authenticateUser(userName: String, password: String): Option[db.User] =
    usersDAO.findOneById(userName) match {
      case Some(user) => if (checkPassword(password, user)) Some(user) else None
      case None => None
    }
}

class OurBasicAuthStrategy(protected override val app: ScalatraBase, realm: String)
  extends BasicAuthStrategy[AuthUser](app, realm) {

  lazy val authenticator = userAuth.getAuthenticator

  protected def validate(userName: String, password: String)
                        (implicit req: HttpServletRequest, res: HttpServletResponse) = {

    authenticator.authenticateUser(userName, password) match {
      case Some(user) => Some(AuthUser(userName))
      case None => None
    }
  }

  protected def getUserId(user: AuthUser)
                         (implicit req: HttpServletRequest, resp: HttpServletResponse) = user.userName
}
