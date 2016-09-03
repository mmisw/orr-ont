package org.mmisw.orr.ont.service

import com.auth0.jwt.JWTSigner
import com.auth0.jwt.JWTVerifier
import com.typesafe.scalalogging.{StrictLogging => Logging}
import org.mmisw.orr.ont.auth.AuthUser

import scala.collection.JavaConversions._
import scala.util.{Failure, Success, Try}

/**
  * JWT token generator and verifier.
  * @param secret  A strong password to generate tokens
  */
class JwtUtil(secret: String) extends AnyRef with Logging {

  def createToken(payload: Map[String,AnyRef]): String = {
    generator.sign(payload)
  }

  def verifyToken(jwt: String): Option[AuthUser] = {
    logger.debug(s"JwtUtil.verifyToken: jwt=$jwt")
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

    logger.debug(s"verifyToken: verifyRes=$verifyRes")

    if (!verifyRes.containsKey("uid")) {
      throw new Exception("token payload does not contain 'uid' attribute'")
    }
    else {
      val uid: String = verifyRes.get("uid").asInstanceOf[String]
      if (uid.length > 0) {
        uid
      }
      else throw new Exception("token payload's 'uid' is empty")
    }
  }

  private[this] val generator = new JWTSigner(secret)

  private[this] val jwtVerifier = new JWTVerifier(secret)
}
