package org.mmisw.orr.ont

import com.typesafe.scalalogging.slf4j.Logging
import java.io.File
import java.util.ServiceConfigurationError
import com.typesafe.config.ConfigFactory


/**
 * Sets up the application according to configuration.
 */
class Setup(configFilename: String, test: Boolean = false) extends AnyRef with Logging {

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
    if (test) {
      val coll = s"${mc.getString("ontologies")}-test"
      logger.info(s"test mode: using ontologies collection: $coll")
      ConfigFactory.parseString(s"ontologies=$coll").withFallback(mc)
    }
    else mc
  }

  val db: Db = new Db(mongoConfig)

  dbOpt = Some(db)

  def destroy() {
    logger.debug(s"destroying application setup")
    dbOpt foreach { _.destroy() }
  }

}
