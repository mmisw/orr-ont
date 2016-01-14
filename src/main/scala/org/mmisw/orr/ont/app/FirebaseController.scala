package org.mmisw.orr.ont.app

import com.typesafe.scalalogging.{StrictLogging => Logging}
import org.mmisw.orr.ont.Setup

class FirebaseController(implicit setup: Setup) extends BaseController
      with Logging {

  // authenticates user returning a JWT if successful
  post("/auth") {
    val map = body()
    val userName = require(map, "username")
    val password = require(map, "password")
    val user = getUser(userName)

    if (userAuth.checkPassword(password, user)) {
      val custom = Map(
        "displayName" -> s"${user.firstName} ${user.lastName}",
        "email" -> user.email,
        "phone" -> user.phone.getOrElse("")
      )
      val jwt = jwtUtil.createToken(userName, custom)
      Token(jwt)
    }
    else error(401, "invalid credentials")
  }
}

case class Token(token: String)
