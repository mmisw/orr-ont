package org.mmisw.orr.ont

import com.typesafe.scalalogging.{StrictLogging => Logging}
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.mmisw.orr.ont.db.Db


/**
 * Sets up the application according to configuration.
 *
 * @param config   Base configuration
 * @param testing  optional string for testing purposes
 */
class Setup(val config: Config, val testing: Option[String] = None) extends AnyRef with Logging {

  private[this] var dbOpt: Option[Db] = None

  // todo omit/obfuscate any passwords in output logging
  logger.debug(s"Loaded configuration: $config")

  val mongoConfig = {
    val mc = config.getConfig("mongo")
    testing match {
      case None => mc

      case Some(baseName) =>
        // adjust collection names:
        val string = List("ontologies", "users", "organizations") map { collName =>
          val testName = s"ztest-$baseName-${mc.getString(collName)}"
          s"$collName=$testName"
        } mkString "\n"
        logger.info(s"test mode: using:\n$string")
        ConfigFactory.parseString(string).withFallback(mc)
    }
  }

  val filesConfig = {
    val fc = config.getConfig("files")
    testing match {
      case None => fc

      case Some(baseName) =>
        val string = List("baseDirectory") map { name =>
          val testName = s"${fc.getString(name)}-test"
          s"$name=$testName"
        } mkString "\n"
        logger.info(s"test mode: using:\n$string")
        ConfigFactory.parseString(string).withFallback(fc)
    }
  }

  com.mongodb.casbah.commons.conversions.scala.RegisterJodaTimeConversionHelpers()

  val db: Db = new Db(mongoConfig)

  dbOpt = Some(db)

  def destroy() {
    logger.debug(s"destroying application setup")
    dbOpt foreach { _.destroy() }
  }

}
