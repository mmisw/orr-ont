package org.mmisw.orr.ont.service

import org.mmisw.orr.ont.Setup
import org.mmisw.orr.ont.db.User

abstract class BaseService(setup: Setup) {

  protected val orgsDAO     = setup.db.orgsDAO
  protected val usersDAO    = setup.db.usersDAO
  protected val ontDAO      = setup.db.ontDAO
  protected val userAuth    = setup.db.authenticator

  protected def verifyUser(userName: String): User = {
    usersDAO.findOneById(userName).getOrElse(throw NoSuchUser(userName))
  }

}
