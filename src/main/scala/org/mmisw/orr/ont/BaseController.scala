package org.mmisw.orr.ont


abstract class BaseController(implicit setup: Setup) extends OrrOntStack
    with SimpleMongoDbJsonConversion {

  protected val authoritiesDAO = setup.db.authoritiesDAO
  protected val usersDAO       = setup.db.usersDAO
  protected val ontDAO         = setup.db.ontDAO


  protected def getUser(userName: String): db.User = {
    usersDAO.findOneById(userName).getOrElse(error(400, s"'$userName' invalid user"))
  }

  protected def verifyUser(userName: String): db.User = {
    if (setup.testing) db.User(userName, "tFirstName", "tlastName", "tPassword")
    else getUser(userName)
  }

  protected def verifyUser(userNameOpt: Option[String]): db.User = userNameOpt match {
    case None => missing("userName")
    case Some(userName) => verifyUser(userName)
  }

}
