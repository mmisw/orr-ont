package org.mmisw.orr.ont


class BaseService(setup: Setup) {

  protected val orgsDAO     = setup.db.orgsDAO
  protected val usersDAO    = setup.db.usersDAO
  protected val ontDAO      = setup.db.ontDAO
  protected val userAuth    = setup.db.authenticator

}
