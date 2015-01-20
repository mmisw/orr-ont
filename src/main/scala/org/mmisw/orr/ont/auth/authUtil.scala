package org.mmisw.orr.ont.auth

import org.apache.commons.codec.binary.Base64

object authUtil {

  def basicCredentials(userName: String, password: String): String = {
    val bytes = s"$userName:$password".getBytes
    "Basic " + new String(Base64.encodeBase64(bytes))
  }
}
