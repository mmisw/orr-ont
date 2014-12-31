package org.mmisw.orr.ont

import org.apache.commons.codec.binary.Base64

trait BaseSpec {
  implicit val formats = org.json4s.DefaultFormats
  com.mongodb.casbah.commons.conversions.scala.RegisterJodaTimeConversionHelpers()

  // use collection names composed from the name of the test class
  implicit val setup = new Setup("/etc/orront.conf", testing = Some(getClass.getSimpleName))

  val adminCredentials = basicCredentials("admin", setup.config.getString("admin.password"))

  def basicCredentials(userName: String, password: String) = {
    val bytes = s"$userName:$password".getBytes
    "Basic " + new String(Base64.encodeBase64(bytes))
  }

  def newUserName() = randomStr("user")

  def newOrgName() = randomStr("org")

  def newOntUri() = randomStr("ont")

  /** random string to compose names/uris */
  def randomStr(prefix: String) = s"$prefix-${java.util.UUID.randomUUID().toString}"
}
