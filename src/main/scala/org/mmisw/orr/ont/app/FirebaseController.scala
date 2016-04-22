package org.mmisw.orr.ont.app

import com.typesafe.scalalogging.{StrictLogging => Logging}
import org.mmisw.orr.ont.Setup
import org.mmisw.orr.ont.service.NoSuchUser

import scala.util.{Failure, Success, Try}

class FirebaseController(implicit setup: Setup) extends BaseController
      with Logging {

  // authenticates user returning a JWT if successful
  post("/auth") {
    val map = body()
    val userName = require(map, "username")
    val password = require(map, "password")

    val user = Try(userService.getUser(userName)) match {
      case Success(res)            => res
      case Failure(exc: NoSuchUser) => error(401, "invalid credentials")
      case Failure(exc)             => error500(exc)
    }

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
