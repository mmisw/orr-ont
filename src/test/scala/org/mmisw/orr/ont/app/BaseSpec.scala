package org.mmisw.orr.ont.app

import org.mmisw.orr.ont.Setup
import org.mmisw.orr.ont.auth.authUtil

trait BaseSpec {
  implicit val formats = org.json4s.DefaultFormats

  // use collection names composed from the name of the test class
  implicit val setup = new Setup("/etc/orront.conf", testing = Some(getClass.getSimpleName))

  val adminCredentials = authUtil.basicCredentials("admin", setup.config.getString("admin.password"))

  def newUserName() = randomStr("user")

  def newOrgName() = randomStr("org")

  def newOntUri() = randomStr("ont")

  /** random string to compose names/uris */
  def randomStr(prefix: String) = s"$prefix-${java.util.UUID.randomUUID().toString}"
}
