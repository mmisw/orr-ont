package org.mmisw.orr.ont.service

import com.typesafe.scalalogging.{StrictLogging => Logging}
import dispatch._
import org.json4s.{DefaultFormats, Formats}
import org.json4s.native.JsonParser
import org.mmisw.orr.ont.Setup
import org.mmisw.orr.ont.auth.authUtil
import org.mmisw.orr.ont.swld.ontUtil

import scala.concurrent.Promise
import scala.util.{Failure, Success, Try}

/**
 * Implementation based on AG REST endpoint.
 */
class TripleStoreServiceAgRest(implicit setup: Setup, ontService: OntService) extends BaseService(setup)
with TripleStoreService with Logging {

  import scala.concurrent.ExecutionContext.Implicits.global

  var formats: Map[String,String] = Map()

  def setFormats(formats: Map[String,String]): Unit = { this.formats = formats }

  def initialize(): Unit = {
    createRepositoryIfMissing()
    createAnonymousUserIfMissing()
  }

  def getSize(contextOpt: Option[String] = None): Either[Throwable, String] = {
    val prom = Promise[Either[Throwable, String]]()

    val sizeReq = (contextOpt match {
      case Some(context) => (svc / "size").addQueryParameter("context", "\"" + context + "\"")
      case _ => svc / "size"
    }).setHeader("Authorization", authUtil.basicCredentials(userName, password))
    logger.warn(s"getSize: $sizeReq")
    dispatch.Http(sizeReq OK as.String) onComplete {
      case Success(content)   => prom.complete(Try(Right(content)))
      case Failure(exception) => prom.complete(Try(Left(exception)))
    }
    val res = prom.future()
    println(s"RES=$res")
    res
  }

  /**
    * Loads a given ontology in the triple store assuming that the AG server and
    * this orr-ont instance share the same data volume.
    *
    * (This operation uses the "file" parameter in the corresponding AG REST call
    * to load the statements in the file.)
    *
    * If reload is true, the contents are replaced.
    */
  def loadUriFromLocal(uri: String, reload: Boolean = false): Either[Throwable, String] = {
    val (_, _, version) = ontService.resolveOntology(uri)
    val (file, actualFormat) = ontService.getOntologyFile(uri, version, "rdf")
    val contentType = ontUtil.mimeMappings(actualFormat)

    val absPath = file.getAbsolutePath
    logger.debug( s"""loadUriFromLocal:
         |  uri=$uri reload=$reload
         |  absPath=$absPath  contentType=$contentType
         |  orrEndpoint=$orrEndpoint
       """.stripMargin)

    val req = (svc / "statements")
      .setContentType(formats(actualFormat), charset = "UTF-8")
      .addQueryParameter("context", "\"" + uri + "\"")
      .addQueryParameter("file", absPath)
      .setHeader("Content-Type", contentType)
      .setHeader("Accept", formats("json"))
      .setHeader("Authorization", authUtil.basicCredentials(userName, password))

    logger.debug(s"loadUriFromLocal: req=$req")

    val future = dispatch.Http((if (reload) req.PUT else req.POST) OK as.String)

    val prom = Promise[Either[Throwable, String]]()
    future onComplete {
      case Success(content)   => prom.complete(Try(Right(content)))
      case Failure(exception) => prom.complete(Try(Left(exception)))
    }
    prom.future()
  }

  def loadUri(uri: String): Either[Throwable, String] =
    loadUri(reload = false, uri)

  def reloadUri(uri: String): Either[Throwable, String] =
    loadUri(reload = true, uri)

  def reloadUris(uris: Iterator[String]) = {
    logger.warn(s"reloadUris:")
    uris map reloadUri
    Right("done")
  }

  def reloadAll() = {
    logger.warn(s"reloadAll:")
    unloadAll()
    val uris = ontService.getAllOntologyUris.toList
    logger.warn(s"loading: ${uris.size} ontologies...")
    uris map loadUri
    Right("done")
  }

  def unloadUri(uri: String): Either[Throwable, String] =
    unload(Some(uri))

  def unloadAll(): Either[Throwable, String] =
    unload(None)

  ///////////////////////////////////////////////////////////////////////////

  private def createRepositoryIfMissing(): Either[Throwable, String] = {
    // use getSize as a way to check whether the repository already exists
    getSize() match {
      case e@Right(content) =>
        logger.debug("AG repository already exists")
        e

      case Left(exc) =>
        logger.info(s"Could not get AG repository size (${exc.getMessage})." +
          " Assuming non-existence. Will now attempt to create AG repository")
        val prom = Promise[Either[Throwable, String]]()

        // NOTE: Not using `svc` directly because host(orrEndpoint) adds a trailing slash
        // to the URL thus making AG to fail with a 404
        val req = url(s"http://$orrEndpoint")
          .setHeader("Accept", formats("json"))
          .setHeader("Authorization", authUtil.basicCredentials(userName, password))

        dispatch.Http(req.PUT OK as.String) onComplete {
          case Success(content) =>
            prom.complete(Try(Right(content)))
            logger.info(s"AG repository creation succeeded. content=$content")

          case Failure(exception) => prom.complete(Try(Left(exception)))
            logger.warn("AG repository creation failed", exception)
        }
        prom.future()
    }
  }

  private def createAnonymousUserIfMissing(): Unit = {
    getUsers foreach { users =>
      if (!users.contains("anonymous")) createAnonymousUser()
    }
  }

  private def getUsers: Option[List[String]] = {
    val prom = Promise[Option[List[String]]]()
    val usersReq = (host(agEndpoint) / "users")
      .setHeader("Accept", formats("json"))
      .setHeader("Authorization", authUtil.basicCredentials(userName, password))
    logger.debug(s"getUsers: $usersReq")
    dispatch.Http(usersReq OK as.String) onComplete {
      case Success(content)   =>
        implicit val jsonFormats: Formats = DefaultFormats
        val users = JsonParser.parse(content).extract[List[String]]
        logger.debug(s"got AG users: $users")
        prom.complete(Try(Some(users)))
      case Failure(exception) =>
        logger.warn(s"Could not get AG users", exception)
        prom.complete(Try(None))
    }
    prom.future()
  }

  private def createAnonymousUser(): Unit = {
    val req = (host(agEndpoint) / "users" / "anonymous")
      .setHeader("Accept", formats("json"))
      .setHeader("Authorization", authUtil.basicCredentials(userName, password))
    logger.debug(s"createAnonymousUser: $req")
    dispatch.Http(req.PUT OK as.String) onComplete {
      case Success(content)   =>
        logger.debug(s"createAnonymousUser succeeded. response: $content")
        setAccessForAnonymousUser()

      case Failure(exception) =>
        logger.warn(s"Could not create AG anonymous user", exception)
    }
  }

  private def setAccessForAnonymousUser(): Unit = {
    val req = (host(agEndpoint) / "users" / "anonymous" / "access")
      .addQueryParameter("read", "true")
      .addQueryParameter("catalog", "/")
      .addQueryParameter("repository", repoName)
      .setHeader("Accept", formats("json"))
      .setHeader("Authorization", authUtil.basicCredentials(userName, password))
    logger.debug(s"setAccessForAnonymousUser: $req")
    dispatch.Http(req.PUT OK as.String) onComplete {
      case Success(content)   =>
        logger.debug(s"setAccessForAnonymousUser succeeded. response: $content")
      case Failure(exception) =>
        logger.warn(s"Could not set access for AG anonymous user", exception)
    }
  }

  /**
   * Loads the given ontology in the triple store.
   * If reload is true, the contents are replaced.
   */
  private def loadUri(reload: Boolean, uri: String): Either[Throwable, String] = {
    val prom = Promise[Either[Throwable, String]]()

    logger.warn(s"loadUri: $uri")
    val (_, ontVersion, version) = ontService.resolveOntology(uri)
    val (file, actualFormat) = ontService.getOntologyFile(uri, version, ontVersion.format)

    val (k, v) = if (setup.config.hasPath("import.aquaUploadsDir"))
      ("file", file.getAbsolutePath)
    else
      ("url", uri)

    val req = (svc / "statements")
      .setContentType(formats(actualFormat), charset = "UTF-8")
      .addQueryParameter("context", "\"" + uri + "\"")
      .addQueryParameter(k, v)
      .setHeader("Accept", formats("json"))
      .setHeader("Authorization", authUtil.basicCredentials(userName, password))

//    println(s"REQ query params=${req.toRequest.getQueryParams}")
//    println(s"REQ headers=${req.toRequest.getHeaders}")
    val complete = dispatch.Http((if (reload) req.PUT else req.POST) OK as.String)
    complete onComplete {
      case Success(content)   => prom.complete(Try(Right(content)))
      case Failure(exception) => prom.complete(Try(Left(exception)))
    }

    val res = prom.future()
    //println(s"RES=$res")
    res
  }

  /**
   * Unloads a particular ontology, or the whole triple store.
   */
  private def unload(uriOpt: Option[String]): Either[Throwable, String] = {
    logger.warn(s"unload: uriOpt=$uriOpt")
    val prom = Promise[Either[Throwable, String]]()

    val baseReq = (svc / "statements")
      .setHeader("Authorization", authUtil.basicCredentials(userName, password))
      .setHeader("Accept", formats("json"))

    val req = uriOpt match {
      case Some(uri) => baseReq.addQueryParameter("context", "\"" + uri + "\"")
      case None      => baseReq
    }

    println(s"REQ query params=${req.toRequest.getQueryParams}")
    println(s"REQ headers=${req.toRequest.getHeaders}")

    val complete = dispatch.Http(req.DELETE OK as.String)
    complete onComplete {
      case Success(content)   => prom.complete(Try(Right(content)))
      case Failure(exception) => prom.complete(Try(Left(exception)))
    }

    val res = prom.future()
    println(s"RES=$res")
    res
  }

  private val agConfig = setup.config.getConfig("agraph")

  private val agHost     = agConfig.getString("host")
  private val agPort     = agConfig.getString("port")
  private val userName   = agConfig.getString("userName")
  private val password   = agConfig.getString("password")
  private val repoName   = agConfig.getString("repoName")

  private val agEndpoint  = s"$agHost:$agPort"
  private val orrEndpoint = s"$agEndpoint/repositories/$repoName"

  private val svc = host(orrEndpoint)
}
