package org.mmisw.orr.ont.auth

import org.scalatra.{ScalatraBase, Unauthorized}

/**
 * Given a secret key, it provides a method for checking whether the
 * request has been properly signed, and a method to force such validation.
 */
trait ApiAuthenticationSupport extends {
  self: ScalatraBase =>

  protected val secretKey: String

  val sigParamName = "sgn"

  /**
   * Returns true iff the request has been properly signed.
   * Allows to restrict or expand on the reported information depending
   * on whether the request has been properly signed.
   */
  protected def isSignedRequest: Boolean = {
    val str = signMe
    val signed = apiAuthenticator(secretKey, str, params.get(sigParamName))
    println(s"$str -> signed=$signed")
    signed
  }

  /**
   * Halts the current request with an Unauthorized result unless the request
   * has been properly signed.
   */
  protected def validateRequest() = {
    if (!isSignedRequest) {
      halt(Unauthorized("unsigned request"))
    }
  }

  /**
   * Concatenates the HTTP verb and request path in a format suitable for HMAC signing.
   */
  protected def signMe = {
    val str = request.requestMethod + "," + request.scriptName + requestPath
    println(s"signMe='$str'")  // TODO remove this
    str
  }
}
