import com.typesafe.scalalogging.slf4j.Logging
import java.util.ServiceConfigurationError
import org.mmisw.orr.ont._
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

    context.mount(new AuthorityController,  "/authority/*")
    context.mount(new UserController,       "/user/*")
    context.mount(new OntController,        "/ont/*")

    setupOpt = Some(setup)
  }

  override def destroy(context: ServletContext) {
    setupOpt foreach { _.destroy() }
  }
}
