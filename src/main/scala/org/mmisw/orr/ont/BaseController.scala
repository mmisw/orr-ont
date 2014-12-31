package org.mmisw.orr.ont

import org.mmisw.orr.ont.auth.AuthenticationSupport


abstract class BaseController(implicit setup: Setup) extends OrrOntStack
  with AuthenticationSupport with SimpleMongoDbJsonConversion {

  protected val orgsDAO     = setup.db.orgsDAO
  protected val usersDAO    = setup.db.usersDAO
  protected val ontDAO      = setup.db.ontDAO
  protected val userAuth    = setup.db.authenticator


  protected def verifyAuthenticatedUser(userNames: String*) {
    basicAuth
    if (!userNames.contains(user.userName)) halt(403, s"unauthorized")
  }

  protected def getUser(userName: String): db.User = {
    usersDAO.findOneById(userName).getOrElse(
      error(400, s"'$userName' user is not registered"))
  }

  protected def verifyUser(userName: String): db.User = {
    getUser(userName)
  }

  protected def verifyUser(userNameOpt: Option[String]): db.User = userNameOpt match {
    case None => missing("userName")
    case Some(userName) => verifyUser(userName)
  }

  protected def getOrg(orgName: String): db.Organization = {
    orgsDAO.findOneById(orgName).getOrElse(
      error(404, s"'$orgName' organization is not registered"))
  }
}
