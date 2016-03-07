package org.mmisw.orr.ont.app

import com.typesafe.config.ConfigFactory
import org.mmisw.orr.ont.Setup
import org.mmisw.orr.ont.auth.authUtil
import org.mmisw.orr.ont.service.JwtUtil
import org.mmisw.orr.ont.util.IEmailer
import org.specs2.mock.Mockito

trait BaseSpec extends Mockito {
  implicit val formats = org.json4s.DefaultFormats

  val config = ConfigFactory.parseString(
    """
      |admin {
      |  password = test
      |  email    = "dummy@example.org"
      |}
      |mongo {
      |  host = localhost
      |  port = 27017
      |  db   = orr-ont
      |  ontologies    = ontologies
      |  users         = users
      |  organizations = organizations
      |}
      |files {
      |    baseDirectory = ./orr-ont-base-directory
      |}
      |agraph {
      |  userName = dummy
      |  password = dummy
      |  tsName = mmiorr
      |  orrEndpoint = "example.org/repos/mmiorr"
      |}
      |
      |email {
      |  account {
      |    username = "noone@example.com"
      |    password = "nopw"
      |  }
      |  server {
      |    host = "smtp.example.net"
      |    port = 465
      |    prot = "smtps"
      |    debug = false
      |  }
      |  from    = "orr-ont <orr-ont@example.org>"
      |  replyTo = "orr-ont@example.org"
      |  mailer  = "orr-ont"
      |}
      |
      |firebase {
      |  secret = "dummy"
      |}
      |
      |""".stripMargin
  )

  // use collection names composed from the name of the test class
  private val testing = Some(getClass.getSimpleName)
  implicit val setup = new Setup(config,
    emailer = mock[IEmailer],
    testing = testing)

  val jwtUtil = new JwtUtil(config.getString("firebase.secret"))

  val adminCredentials = authUtil.basicCredentials("admin", setup.config.getString("admin.password"))

  def newUserName() = randomStr("user")

  def newOrgName() = randomStr("org")

  def newOntUri() = randomStr("ont")

  /** random string to compose names/uris */
  def randomStr(prefix: String) = s"$prefix-${java.util.UUID.randomUUID().toString}"
}
