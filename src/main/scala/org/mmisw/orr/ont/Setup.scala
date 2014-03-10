package org.mmisw.orr.ont

import com.typesafe.scalalogging.slf4j.Logging
import java.io.File
import java.util.ServiceConfigurationError
import com.typesafe.config.ConfigFactory
import org.mmisw.orr.ont.db.Db


/**
 * Sets up the application according to configuration.
 */
class Setup(configFilename: String, val testing: Boolean = false) extends AnyRef with Logging {

  private[this] var dbOpt: Option[Db] = None

  logger.info(s"Loading configuration from $configFilename")
  val configFile = new File(configFilename)
  if (!configFile.canRead) {
    throw new ServiceConfigurationError("Could not read configuration file " + configFile)
  }

  val config = ConfigFactory.parseFile(configFile)
  // todo omit/obfuscate any passwords in output logging
  logger.debug(s"Loaded configuration: $config")

  val mongoConfig = {
    val mc = config.getConfig("mongo")
    if (testing) {
      val string = List("ontologies", "users", "organizations") map {collName =>
        val testName = s"test-${mc.getString(collName)}"
        s"$collName=$testName"
      } mkString "\n"
      logger.info(s"test mode: using:\n$string")
      ConfigFactory.parseString(string).withFallback(mc)
    }
    else mc
  }

  val filesConfig = {
    val fc = config.getConfig("files")
    if (testing) {
      val string = List("baseDirectory") map {name =>
        val testName = s"${fc.getString(name)}-test"
        s"$name=$testName"
      } mkString "\n"
      logger.info(s"test mode: using:\n$string")
      ConfigFactory.parseString(string).withFallback(fc)
    }
    else fc
  }

  val db: Db = new Db(mongoConfig)

  dbOpt = Some(db)

  def destroy() {
    logger.debug(s"destroying application setup")
    dbOpt foreach { _.destroy() }
  }

}
