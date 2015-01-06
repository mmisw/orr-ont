import com.typesafe.scalalogging.slf4j.Logging
import java.util.ServiceConfigurationError
import org.mmisw.orr.ont._
import org.mmisw.orr.ont.app.{OntController, OrgController, UserController, SelfHostedOntController}
import org.mmisw.orr.ont.service.OntService
import org.scalatra._
import javax.servlet.ServletContext


class ScalatraBootstrap extends LifeCycle with Logging {

  private[this] var setupOpt: Option[Setup] = None

  override def init(context: ServletContext) {

    logger.info(s"contextPath = '${context.getContextPath}'")

    val configFilename = context.getInitParameter("configFile")
    if (configFilename == null) {
      throw new ServiceConfigurationError("Could not retrieve configuration parameter: configFile.  Check web.xml")
    }

    implicit val setup = new Setup(configFilename)
    implicit val ontService = new OntService

    context.mount(new OrgController,           "/api/v0/org/*")
    context.mount(new UserController,          "/api/v0/user/*")
    context.mount(new OntController,           "/api/v0/ont/*")
    context.mount(new SelfHostedOntController, "/*")

    setupOpt = Some(setup)
  }

  override def destroy(context: ServletContext) {
    setupOpt foreach { _.destroy() }
  }
}
