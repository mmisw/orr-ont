package org.mmisw.orr.ont.service

import com.typesafe.scalalogging.{StrictLogging ⇒ Logging}
import dispatch._
import org.json4s.{DefaultFormats, Formats}
import org.json4s.native.JsonParser
import org.mmisw.orr.ont.{Setup, TripleStoreResult}
import org.mmisw.orr.ont.auth.authUtil
import org.mmisw.orr.ont.swld.ontUtil.AcceptAndSelectForTerm
import org.mmisw.orr.ont.swld.{FileExt, ontUtil}
import org.mmisw.orr.ont.util.FileUtils

import scala.concurrent.Promise
import scala.util.{Failure, Success, Try}

/**
 * Implementation based on AG REST endpoint.
 */
class TripleStoreServiceAgRest(implicit setup: Setup, ontService: OntService) extends BaseService(setup)
with TripleStoreService with Logging {

  // TODO get version piece in some dynamic way (eg, buildInfo-like)
  private val userAgent = "ORR-Ont/3.8.4"

  import scala.concurrent.ExecutionContext.Implicits.global

  var formats: Map[String,String] = Map()

  def setFormats(formats: Map[String,String]): Unit = { this.formats = formats }

  /**
    * Initializes the triple store for ORR.
    */
  def initialize(): Unit = {
    logger.info("TripleStoreServiceAgRest.initialize")

    def doInit(): Unit = {
      logger.info("TripleStoreServiceAgRest.doInit")
      createRepository()
      createAnonymousUserIfMissing()
    }

    // use getSize as a way to check whether the repository already exists
    getSize() match {
      case e@Right(content) =>
        logger.info("AG repository already exists.")
        createAnonymousUserIfMissing()

      case Left(exc) =>
        logger.info(s"Could not get AG repository size (${exc.getMessage})")

        setup.cfg.agraph.initDelay match {
          case None =>
            logger.info(s"Assuming non-existence. Attempting to create AG repository")
            doInit()

          case Some(delaySecs) =>
            logger.info(s"Perhaps AG is not fully started yet. Will check again in $delaySecs seconds")
            new Thread(new Runnable {
              def run() {
                Thread.sleep(delaySecs * 1000)
                logger.info(s"Checking AG repository again")
                getSize() match {
                  case Right(content) =>
                    logger.info("AG repository exists.")
                    createAnonymousUserIfMissing()

                  case Left(exc2) =>
                    logger.info(s"2nd attempt: could not get AG repository size (${exc2.getMessage})." +
                      s" Assuming non-existence. Attempting to create AG repository")
                    doInit()
              }
            }}).start()
        }
    }
  }

  def getSize(contextOpt: Option[String] = None): Either[Throwable, TripleStoreResult] = {
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
    logger.debug(s"getSize res=$res")
    res match {
      case Left(ex) ⇒ Left(ex)
      case Right(content) ⇒
        try Right(TripleStoreResult(uri = contextOpt, size = Some(content.toLong)))
        catch {
          case ex: NumberFormatException ⇒ Left(ex)
        }
    }
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
    val (_, _, version) = ontService.resolveOntologyVersion(uri)
    val (file, actualFormat) = ontService.getOntologyFile(uri, version, "rdf")
    val contentType = ontUtil.mimeMappings(actualFormat)

    val absPath = file.getAbsolutePath

    FileUtils.makeReadableByAnyone(file, setup.baseDir) foreach { error =>
      logger.warn(s"loadUriFromLocal: error trying to make $absPath readable: $error")
    }

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
      .setHeader("User-Agent", userAgent)

    val method = if (reload) req.PUT else req.POST

    if (false) debugReqResponse(method, "loadUriFromLocal")

    val future = dispatch.Http(method OK as.String)

    val prom = Promise[Either[Throwable, String]]()
    future onComplete {
      case Success(content)   => prom.complete(Try(Right(content)))
      case Failure(exception) => prom.complete(Try(Left(exception)))
    }
    prom.future()
  }

  def loadUri(uri: String): Either[Throwable, String] =
    loadUriFromLocal(uri, reload = false)

  def reloadUri(uri: String): Either[Throwable, String] =
    loadUriFromLocal(uri, reload = true)

  def reloadUris(uris: Iterator[String]): Either[Throwable, TripleStoreResult] = {
    logger.warn(s"reloadUris:")
    uris map reloadUri
    Right(TripleStoreResult(msg = Some(s"Done, ${uris.size} ontologies reloaded")))
  }

  def reloadAll(): Either[Throwable, TripleStoreResult] = {
    logger.warn(s"reloadAll:")
    unloadAll()
    val uris = ontService.getAllOntologyUris.toList
    logger.debug(s"loading: ${uris.size} ontologies...")
    uris map loadUri
    Right(TripleStoreResult(msg = Some(s"Done, ${uris.size} ontologies reloaded")))
  }

  def unloadUri(uri: String): Either[Throwable, TripleStoreResult] =
    unload(Some(uri))

  def unloadAll(): Either[Throwable, TripleStoreResult] =
    unload(None)

  /**
    * Resolves a URI via SPARQL query to retrieve associated properties.
    *
    * If a format is given, an ad hoc simple-format to mime-type mapping is performed.
    *
    * In general, the acceptHeader value can be one from the following depending on whether
    * or not the URI is going to be resolved with "select" query.
    *
    * For "select" query:
    *   application/json,
    *   application/processed-csv,
    *   application/sparql-results+json,
    *   application/sparql-results+ttl,
    *   application/sparql-results+xml,
    *   application/x-direct-upis,
    *   application/x-lisp-structured-expression,
    *   text/csv,
    *   text/integer,
    *   text/simple-csv,
    *   text/tab-separated-values,
    *   text/table.
    *
    * For non-select:
    *    application/rdf+xml (RDF/XML)
    *    text/plain (N-triples)
    *    text/x-nquads (N-quads)
    *    application/trix (TriX)
    *    text/rdf+n3 (N3)
    *    text/integer (return only a result count)
    *    application/json,
    *    application/x-quints+json
    *
    * (possible acceptHeader values determined from AG documentation and also by triggering response errors.)
    */
  def resolveTermUri(uri: String,
                     formatOpt: Option[String],
                     acceptHeader: List[String]
                    ): Either[Error, TermResponse] = {

    termResolver.resolveTermUri(uri, formatOpt, acceptHeader)
  }

  ///////////////////////////////////////////////////////////////////////////

  // ad hoc helper to diagnose issues with requests to the AG
  private def debugReqResponse(req: Req, msg: String = ""): Unit = {
    val str = new StringBuilder(s"debugReqResponse: $msg\n")
    str ++= s"response:\n"
    val asMyDebug = as.Response { response =>
      str ++= s" status=${response.getStatusCode}\n"
      str ++= s" body=${response.getResponseBody}\n"
      import scala.collection.JavaConverters._
      scala.collection.JavaConverters.mapAsScalaMapConverter(
        response.getHeaders
      ).asScala.toMap.mapValues(_.asScala.toList)
    }

    val response: Either[Throwable, Map[String, List[String]]] = Http(req > asMyDebug).either()
    response match {
      case Right(headers) =>
        headers.foreach { case (k, v) => str ++= s"\t$k : $v\n" }
        logger.debug(str.toString())

      case Left(exception) =>
        logger.error(s"\terror=${exception.getMessage}", exception)
    }
  }

  private def createRepository(): Either[Throwable, String] = {
    val prom = Promise[Either[Throwable, String]]()

    // NOTE: Not using `svc` directly because host(orrEndpoint) adds a trailing slash
    // to the URL thus making AG to fail with a 404
    val req = sparqlEndpoint
      .setHeader("Accept", formats("json"))
      .setHeader("Authorization", authUtil.basicCredentials(userName, password))
      .setHeader("User-Agent", userAgent)

    dispatch.Http(req.PUT OK as.String) onComplete {
      case Success(content) =>
        prom.complete(Try(Right(content)))
        logger.info(s"AG repository creation succeeded. content=$content")

      case Failure(exception) => prom.complete(Try(Left(exception)))
        logger.warn("AG repository creation failed", exception)
    }
    prom.future()
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
      .setHeader("User-Agent", userAgent)
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
      .setHeader("User-Agent", userAgent)
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
      .setHeader("User-Agent", userAgent)
    logger.debug(s"setAccessForAnonymousUser: $req")
    dispatch.Http(req.PUT OK as.String) onComplete {
      case Success(content)   =>
        logger.debug(s"setAccessForAnonymousUser succeeded. response: $content")
      case Failure(exception) =>
        logger.warn(s"Could not set access for AG anonymous user", exception)
    }
  }

  /**
   * Unloads a particular ontology, or the whole triple store.
   */
  private def unload(uriOpt: Option[String]): Either[Throwable, TripleStoreResult] = {
    logger.warn(s"unload: uriOpt=$uriOpt")

    val baseReq = (svc / "statements")
      .setHeader("Authorization", authUtil.basicCredentials(userName, password))
      .setHeader("Accept", formats("json"))
      .setHeader("User-Agent", userAgent)

    val req = uriOpt match {
      case Some(uri) => baseReq.addQueryParameter("context", "\"" + uri + "\"")
      case None      => baseReq
    }

    logger.debug(s"unload REQ query params=${req.toRequest.getQueryParams}")
    logger.debug(s"unload REQ headers=${req.toRequest.getHeaders}")

    val prom = Promise[Either[Throwable, TripleStoreResult]]()

    val complete = dispatch.Http(req.DELETE OK as.String)
    complete onComplete {
      case Success(content)   => prom.complete(Try(Right(TripleStoreResult(msg = Some(content)))))
      case Failure(exception) => prom.complete(Try(Left(exception)))
    }

    val res = prom.future()
    logger.debug(s"unload res=$res")
    res
  }

  private val agConfig = setup.cfg.agraph

  private val agHost     = agConfig.host
  private val agPort     = agConfig.port
  private val userName   = agConfig.userName
  private val password   = agConfig.password
  private val repoName   = agConfig.repoName

  private val agEndpoint  = s"$agHost:$agPort"
  private val orrEndpoint = s"$agEndpoint/repositories/$repoName"

  private val svc = host(orrEndpoint).setHeader("User-Agent", userAgent)

  private val sparqlEndpoint = url(agConfig.sparqlEndpoint)

  private object termResolver {
    import java.util.regex.Pattern

    def resolveTermUri(uri: String,
                       formatOpt: Option[String],
                       acceptHeader: List[String]
                      ): Either[Error, TermResponse] = {

      logger.debug(s"resolveTermUri: uri=$uri, formatOpt=$formatOpt, acceptHeader=$acceptHeader")

      formatOpt match {
        // 1. highest precedence for 'format' parameter:
        case Some(format) ⇒
          ontUtil.format2acceptForTerm(format) match {
            case Some((accept, isSelect)) ⇒
              doRequest(uri, accept, isSelect)
            case None ⇒
              Left(NoSuchTermFormat(uri, format))
          }

        case None ⇒
          ontUtil.recognizedFileExtensionForTerm(uri) match {
            // 2. then, "file extension" if any:
            case Some((uri2, FileExt(fileExt))) ⇒
              ontUtil.format2acceptForTerm(fileExt) match {
                case Some((accept, isSelect)) ⇒
                  doRequest(uri2, accept, isSelect)
                case None ⇒
                  Left(NoSuchTermFormat(uri, fileExt))
              }

            // 3. Accept header:
            case _ ⇒
              val (accept, isSelect) = accept2AcceptAndSelect(acceptHeader)
              doRequest(uri, accept, isSelect)
          }
      }
    }

    def doRequest(uri: String, accept: String, isSelect: Boolean): Either[Error, TermResponse] = {
      val template = {
        if (goodIriCharactersPattern.matcher(uri).matches) {
          if (isSelect) PROPS_SELECT_QUERY_TEMPLATE else PROPS_CONSTRUCT_QUERY_TEMPLATE
        }
        else {
          if (isSelect) PROPS_SELECT_QUERY_TEMPLATE_ALTERNATE else PROPS_CONSTRUCT_QUERY_TEMPLATE_ALTERNATE
        }
      }
      val query = template.replace("{E}", uri)

      logger.debug(s"resolveTermUri: uri=$uri, accept=$accept, isSelect=$isSelect, query=[$query]")

      val req = sparqlEndpoint
        .setHeader("Content-Type", "application/x-www-form-urlencoded")
        .setHeader("Accept", accept)
        .setHeader("User-Agent", userAgent)
        .addParameter("query", query)

      if (false) debugReqResponse(req.POST, s"resolveTermUri: $uri")

      val future = dispatch.Http(req.POST > as.String)

      val prom = Promise[Either[Error, TermResponse]]()
      future onComplete {
        case Success(content)   => prom.success(Right(TermResponse(content, accept)))
        case Failure(exception) => prom.success(Left(CannotQueryTerm(uri, exception.getMessage)))
      }
      prom.future()
    }

    private def accept2AcceptAndSelect(acceptHeader: List[String]): AcceptAndSelectForTerm = {
      if (acceptHeader.isEmpty || acceptHeader == List("*/*")) {
        (formats("json"), true)
      }
      else {
        val acceptsForNoSelect = List(
          "application/rdf+xml",
          "text/x-nquads",
          "application/trix",
          "text/turtle",
          "text/rdf+n3",
          "application/x-quints+json",
          "text/integer"
        )
        val noSelect = acceptHeader.exists(a => acceptsForNoSelect.contains(a))
        (acceptHeader.mkString(","), !noSelect)
      }
    }

    /**
      * Simple regex to verify that a URI is an IRI for purposes of its direct use in SPARQL query.
      * See http://www.w3.org/TR/rdf-sparql-query/#QSynIRI
      * Note: this pattern is only about the characters that are allowed, not about the structure of the IRI.
      * Also, for simplicity, it excludes any space as determined by java via the "\\s" regex; this
      * may not exactly match the specification but should be good enough for our purposes.
      */
    private val goodIriCharactersPattern = Pattern.compile("[^\\s<>\"{}|\\\\^`]*")

    private val PROPS_SELECT_QUERY_TEMPLATE =
      """select distinct ?property ?value
        |where { <{E}> ?property ?value . }
        |order by ?property
      """.stripMargin.trim

    /**
      * Alternate SELECT Query template to obtain all properties associated with an entity
      * whose URI cannot be used directly per SPARQL restrictions
      */
    private val PROPS_SELECT_QUERY_TEMPLATE_ALTERNATE =
      """select ?property ?value
        |where { ?s ?property ?value FILTER (str(?s) = "{E}") }
        |order by ?property
        |""".stripMargin.trim

    /**
      * CONSTRUCT Query template to obtain all properties associated with an entity
      */
    private val PROPS_CONSTRUCT_QUERY_TEMPLATE =
      """construct { <{E}> ?prop ?value }
        |where { <{E}> ?prop ?value . }
      """.stripMargin.trim

    /**
      * Alternate CONSTRUCT Query template to obtain all properties associated with an entity
      * whose URI cannot be used directly per SPARQL restrictions
      */
    private val PROPS_CONSTRUCT_QUERY_TEMPLATE_ALTERNATE =
      """construct { ?s ?property ?value }
        |where { ?s ?property ?value FILTER (str(?s) = "{E}") }
      """.stripMargin.trim
  }
}
