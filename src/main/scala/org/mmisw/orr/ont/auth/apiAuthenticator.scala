package org.mmisw.orr.ont.auth

/**
 * Utility to support API authentication.
 * Adapted from https://github.com/scalatra/scalatra-in-action
 */
object apiAuthenticator {

  /**
   * Returns true iff a request (as represented by the signMe string) has been properly signed.
   * @param secretKey  secret key
   * @param signMe     Representation of request
   * @param hmacOpt    hmac
   */
  def apply(secretKey: String, signMe: String, hmacOpt: Option[String]): Boolean = {
    hmacOpt match {
      case Some(hmac) => HmacUtils.verify(secretKey, signMe, hmac)
      case None =>
        notifySig(secretKey, signMe)
        false
    }
  }

  private def notifySig(secretKey: String, signMe: String): Unit = {
    val base64hmac = HmacUtils.sign(secretKey, signMe)
    val urlEncodedHmac = java.net.URLEncoder.encode(base64hmac, "UTF-8")
    println(s"expecting signature: " + urlEncodedHmac)
  }
}


// from https://github.com/scalatra/scalatra-in-action
object HmacUtils {
  import javax.crypto.Mac
  import javax.crypto.spec.SecretKeySpec
  import sun.misc.BASE64Encoder

  def verify(secretKey: String, signMe: String, hmac: String) = sign(secretKey, signMe) == hmac

  def sign(secretKey: String, signMe: String): String = {
    val secret = new SecretKeySpec(secretKey.getBytes, "HmacSHA1")
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(secret)
    val hmac = mac.doFinal(signMe.getBytes)
    val signed = new BASE64Encoder().encode(hmac)
    println(s"signed=$signed")
    signed
  }
}
