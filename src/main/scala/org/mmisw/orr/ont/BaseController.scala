package org.mmisw.orr.ont


abstract class BaseController(implicit setup: Setup) extends OrrOntStack
    with SimpleMongoDbJsonConversion {

  protected val authoritiesDAO = setup.db.authoritiesDAO
  protected val usersDAO       = setup.db.usersDAO
  protected val ontDAO         = setup.db.ontDAO

  protected def verifyUser(userName: String): String = {
    if (setup.testing) userName
    else {
      usersDAO.findOneById(userName) match {
        case None => error(400, s"'$userName' invalid user")
        case _ => userName
      }
    }
  }

  protected def verifyUser(userNameOpt: Option[String]): String = userNameOpt match {
    case None => missing("userName")
    case Some(userName) => verifyUser(userName)
  }

}
