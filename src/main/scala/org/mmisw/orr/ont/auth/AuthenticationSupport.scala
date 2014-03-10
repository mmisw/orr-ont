package org.mmisw.orr.ont.auth

import org.scalatra.auth.strategy.BasicAuthSupport
import org.scalatra.auth.{ScentrySupport, ScentryConfig}
import org.scalatra.ScalatraBase


trait AuthenticationSupport extends ScentrySupport[AuthUser] with BasicAuthSupport[AuthUser] {
  self: ScalatraBase =>

  val realm = "orr-ont realm"

  protected def fromSession = { case userName: String => AuthUser(userName)  }
  protected def toSession   = { case user: AuthUser       => user.userName }

  protected val scentryConfig = new ScentryConfig{}.asInstanceOf[ScentryConfiguration]

  override protected def configureScentry = {
    scentry.unauthenticated {
      scentry.strategies("Basic").unauthenticated()
    }
  }

  override protected def registerAuthStrategies = {
    scentry.register("Basic", app => new OurBasicAuthStrategy(app, realm))
  }
}
