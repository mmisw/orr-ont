package org.mmisw.orr.ont.service

import com.auth0.jwt.JWTVerifier
import com.firebase.security.token.TokenGenerator
import com.typesafe.scalalogging.{StrictLogging => Logging}
import org.mmisw.orr.ont.auth.AuthUser

import scala.collection.JavaConversions._
import scala.util.{Failure, Success, Try}

/**
  * JWT token generator and verifier.
  * @param secret Firebase secret
  */
class JwtUtil(secret: String) extends AnyRef with Logging {

  def createToken(uid: String, customMap: Map[String,String]): String = {
    val customJMap: java.util.Map[String,String] = customMap
    val authPayload = Map("uid" -> uid, "custom" -> customJMap)
    generator.createToken(authPayload)
  }

  def verifyToken(jwt: String): Option[AuthUser] = {
    Try(doVerifyToken(jwt)) match {
      case Success(uid) =>
        Some(AuthUser(uid))

      case Failure(exc) =>
        println("verifyToken exc=" + exc)
        None
    }
  }

  private[this] def doVerifyToken(jwt: String): String = {
    val verifyRes: java.util.Map[String, AnyRef] = jwtVerifier.verify(jwt)

    println(s"verifyToken: verifyRes=$verifyRes")
    logger.debug(s"verifyToken: verifyRes=$verifyRes")

    if (!verifyRes.containsKey("d")) {
      throw new Exception("token payload does not contain 'd' attribute'")
    }
    else {
      val d: java.util.Map[String, String] = verifyRes.get("d").asInstanceOf[java.util.Map[String, String]]
      val uid: String = d.get("uid")
      if (uid != null && uid.length > 0) {
        uid
      }
      else throw new Exception("token payload's 'd' attribute' does not contain 'uid'")
    }
  }

  private[this] val generator = new TokenGenerator(secret)

  private[this] val jwtVerifier: JWTVerifier = new JWTVerifier(secret)
}
