package org.mmisw.orr.ont

import org.apache.commons.codec.binary.Base64

trait BaseSpec {
  implicit val formats = org.json4s.DefaultFormats
  com.mongodb.casbah.commons.conversions.scala.RegisterJodaTimeConversionHelpers()

  implicit val setup = new Setup("/etc/orront.conf", testing = true)

  val adminCredentials = basicCredentials("admin", setup.config.getString("admin.password"))

  def basicCredentials(userName: String, password: String) = {
    val bytes = s"$userName:$password".getBytes
    "Basic " + new String(Base64.encodeBase64(bytes))
  }
}
