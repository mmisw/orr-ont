package org.mmisw.orr.ont

import com.typesafe.scalalogging.{StrictLogging => Logging}
import org.mmisw.orr.ont.db.Db
import org.mmisw.orr.ont.util.IEmailer


/**
 * Sets up the application according to configuration.
 *
 * @param cfg   Base configuration
 * @param emailer  emailer
 * @param testing  optional string for testing purposes
 */
class Setup(val cfg: Cfg,
            val emailer: IEmailer,
            val testing: Option[String] = None
            ) extends AnyRef with Logging {

  private[this] var dbOpt: Option[Db] = None

  // todo omit/obfuscate any passwords in output logging
  if (logger.underlying.isInfoEnabled()) {
    logger.info(s"Configuration:\n$cfg")
  }

  val mongoConfig = {
    val mc = cfg.mongo
    testing match {
      case None => mc

      case Some(baseName) =>
        // adjust collection names:
        mc.copy(
          ontologies    = s"ztest-$baseName-${mc.ontologies}",
          users         = s"ztest-$baseName-${mc.users}",
          organizations = s"ztest-$baseName-${mc.organizations}"
        )
    }
  }

  val filesConfig = {
    val fc = cfg.files
    testing match {
      case None => fc

      case Some(baseName) =>
        fc.copy(baseDirectory = s"${fc.baseDirectory}-test")
    }
  }

  val instanceName = cfg.branding.instanceName

  com.mongodb.casbah.commons.conversions.scala.RegisterJodaTimeConversionHelpers()

  val db: Db = new Db(mongoConfig)

  dbOpt = Some(db)

  val recaptchaPrivateKey = cfg.recaptcha.privateKey

  def destroy() {
    logger.debug(s"destroying application setup")
    dbOpt foreach { _.destroy() }
  }

}
