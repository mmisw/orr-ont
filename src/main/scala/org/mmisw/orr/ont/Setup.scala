package org.mmisw.orr.ont

import com.typesafe.scalalogging.slf4j.Logging
import java.io.File
import java.util.ServiceConfigurationError
import com.typesafe.config.ConfigFactory


/**
 * Sets up the application according to configuration.
 */
class Setup(configFilename: String) extends AnyRef with Logging {

  private[this] var dbOpt: Option[Db] = None

  logger.info(s"Loading configuration from $configFilename")
  val configFile = new File(configFilename)
  if (!configFile.canRead) {
    throw new ServiceConfigurationError("Could not read configuration file " + configFile)
  }

  val config = ConfigFactory.parseFile(configFile)
  // todo omit/obfuscate any passwords in output logging
  logger.debug(s"Loaded configuration: $config")

  val mongoConfig = config.getConfig("mongo")

  implicit val db: Db = new Db(mongoConfig)

  dbOpt = Some(db)

  def destroy() {
    logger.debug(s"destroying application setup")
    dbOpt foreach { _.destroy() }
  }

}
