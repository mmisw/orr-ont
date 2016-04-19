import java.io.File

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import java.util.ServiceConfigurationError
import org.mmisw.orr.ont._
import org.mmisw.orr.ont.app._
import org.mmisw.orr.ont.service.{TripleStoreService, TripleStoreServiceAgRest, OntService}
import org.mmisw.orr.ont.util.Emailer
import org.scalatra._
import javax.servlet.ServletContext


class ScalatraBootstrap extends LifeCycle with StrictLogging {

  private[this] var setupOpt: Option[Setup] = None

  override def init(context: ServletContext) {

    logger.info(s"contextPath = '${context.getContextPath}'")

    val baseAppConfig: Config = ConfigFactory.load
    val configFilename: String = baseAppConfig.getString("configFile")
    if (configFilename == null) {
      throw new ServiceConfigurationError("Could not retrieve configuration parameter: configFile.  Check application.conf")
    }

    val config = {  // todo refactor config loading
      logger.info(s"Loading configuration from $configFilename")
      val configFile = new File(configFilename)
      if (!configFile.canRead) {
        throw new ServiceConfigurationError("Could not read configuration file " + configFile)
      }
      ConfigFactory.parseFile(configFile).resolve()
    }

    implicit val setup = new Setup(config, new Emailer(config.getConfig("email")))
    implicit val ontService = new OntService
    implicit val tsService: TripleStoreService = new TripleStoreServiceAgRest

    context.mount(new OrgController,           "/api/v0/org/*")
    context.mount(new UserController,          "/api/v0/user/*")
    context.mount(new OntController,           "/api/v0/ont/*")
    context.mount(new TripleStoreController,   "/api/v0/ts/*")
    context.mount(new FirebaseController,      "/api/v0/fb/")
    context.mount(new SelfHostedOntController, "/*")

    setupOpt = Some(setup)
  }

  override def destroy(context: ServletContext) {
    setupOpt foreach { _.destroy() }
  }
}
