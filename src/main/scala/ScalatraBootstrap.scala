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

import org.apache.jena.system.JenaSystem


class ScalatraBootstrap extends LifeCycle with StrictLogging {
  JenaSystem.init()

  private[this] var setupOpt: Option[Setup] = None

  override def init(context: ServletContext) {

    logger.info(s"contextPath = '${context.getContextPath}'")

    val baseAppConfig: Config = ConfigFactory.load
    val configFilename: String = baseAppConfig.getString("configFile")
    if (configFilename == null) {
      throw new ServiceConfigurationError("Could not retrieve configuration parameter: configFile.  Check application.conf")
    }

    val config = {
      logger.info(s"Loading configuration from $configFilename")
      val configFile = new File(configFilename)
      if (!configFile.canRead) {
        throw new ServiceConfigurationError("Could not read configuration file " + configFile)
      }
      ConfigFactory.parseFile(configFile).resolve()
    }
    val cfg = Cfg(config)

    implicit val setup = new Setup(cfg, new Emailer(cfg.email))
    implicit val ontService = new OntService
    implicit val tsService: TripleStoreService = new TripleStoreServiceAgRest

    context.mount(new OrgController,           "/api/v0/org/*")
    context.mount(new UserController,          "/api/v0/user/*")
    context.mount(new OntController,           "/api/v0/ont/*")
    context.mount(new TermController,          "/api/v0/term/*")
    context.mount(new TripleStoreController,   "/api/v0/ts/*")
    context.mount(new ApiDocs,                 "/api-docs")
    context.mount(new SelfHostedOntController, "/*")

    setOrrPortalStuff(context, setup.cfg)

    setupOpt = Some(setup)
  }

  override def destroy(context: ServletContext) {
    setupOpt foreach { _.destroy() }
  }

  private def setOrrPortalStuff(context: ServletContext, cfg: Cfg): Unit = {
    import java.nio.file.{Paths, Files, StandardCopyOption}

    try setLocalConfigJs() catch { case e:Exception => logger.error("Error setting local.config.js", e) }

    try adjustIndexHtmls() catch { case e:Exception => logger.error("Error setting index.html files", e) }

    def setLocalConfigJs(): Unit = {
      val from = Paths.get(cfg.files.baseDirectory, "local.config.js")
      logger.info(s"setLocalConfigJs: from=$from")
      if (from.toFile.exists()) {
        val jsDir = new File(context.getRealPath("js/config.js")).getParentFile
        logger.info(s"setLocalConfigJs: jsDir=$jsDir")
        if (jsDir.exists()) {
          val dest = Paths.get(jsDir.getAbsolutePath, "local.config.js")
          logger.info(s"setLocalConfigJs: copying $from to $dest")
          Files.copy(from, dest, StandardCopyOption.REPLACE_EXISTING)
        }
      }
    }

    def adjustIndexHtmls(): Unit = {
      val indexes = List("index.html", "sparql/index.html")

      val googleSnippetOpt = cfg.googleAnalytics.propertyId map { propertyId =>
        s"""<script>
            |window.ga=window.ga||function(){(ga.q=ga.q||[]).push(arguments)};ga.l=+new Date;
            |ga('create', '$propertyId', 'auto');
            |ga('send', 'pageview');
            |</script>
            |<script async src='//www.google-analytics.com/analytics.js'></script>
        """.stripMargin
      }

      indexes foreach { indexHtml =>
        val indexPath = Paths.get(context.getRealPath(indexHtml))
        if (indexPath.toFile.exists()) {
          import java.nio.charset.StandardCharsets.UTF_8
          import scala.collection.JavaConversions._
          val contents = Files.readAllLines(indexPath, UTF_8).mkString("\n")

          var newContentsOpt: Option[String] = None

          googleSnippetOpt foreach { snippet =>
            val fragment = snippet + "\n</head>"
            val c = newContentsOpt.getOrElse(contents)
            if (!c.contains(fragment)) {
              newContentsOpt = Some(c.replace("</head>", fragment))
              logger.info(s"adjustIndexHtmls: set google analytics: $indexPath")
            }
          }

          cfg.branding.footer foreach { footer =>
            val fragment = footer + "\n</body>"
            val c = newContentsOpt.getOrElse(contents)
            if (!c.contains(fragment)) {
              newContentsOpt = Some(c.replace("</body>", fragment))
              logger.info(s"adjustIndexHtmls: set footer: $indexPath")
            }
          }

          newContentsOpt foreach { newContents =>
            Files.write(indexPath, newContents.getBytes(UTF_8))
          }
        }
      }
    }
  }
}
