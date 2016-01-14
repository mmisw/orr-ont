package org.mmisw.orr.ont.app

import com.typesafe.scalalogging.{StrictLogging => Logging}
import com.firebase.security.token.TokenGenerator
import org.mmisw.orr.ont.Setup
import scala.collection.JavaConversions._

class FirebaseController(implicit setup: Setup) extends BaseController
      with Logging {

  post("/auth") {
    val map = body()
    val userName = require(map, "username")
    val password = require(map, "password")
    val user = getUser(userName)

    if (userAuth.checkPassword(password, user)) {
      val custom: java.util.Map[String,String] = Map(
        "displayName" -> s"${user.firstName} ${user.lastName}",
        "email" -> user.email,
        "phone" -> user.phone.getOrElse("")
      )

      val authPayload = Map(
        "uid" -> userName,
        "custom" -> custom
      )
      val generator = new TokenGenerator(setup.config.getString("firebase.secret"))
      val token = generator.createToken(authPayload)
      Token(token)
    }
    else error(401, "bad password")
  }

}

case class Token(token: String)
