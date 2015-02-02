package org.mmisw.orr.ont.service

import com.typesafe.scalalogging.slf4j.Logging
import dispatch._
import org.mmisw.orr.ont.Setup
import org.mmisw.orr.ont.auth.authUtil

import scala.concurrent.Promise
import scala.util.{Failure, Success, Try}

/**
 * Implementation based on AG REST endpoint.
 */
class TripleStoreServiceAgRest(implicit setup: Setup, ontService: OntService) extends BaseService(setup)
with TripleStoreService with Logging {

  import scala.concurrent.ExecutionContext.Implicits.global

  def getSize(contextOpt: Option[String] = None): Either[Throwable, String] = {
    val prom = Promise[Either[Throwable, String]]()

    val sizeReq = contextOpt match {
      case Some(context) => (svc / "size").addQueryParameter("context", "\"" + context + "\"")
      case _ => svc / "size"
    }
    logger.warn(s"getSize: $sizeReq")
    dispatch.Http(sizeReq OK as.String) onComplete {
      case Success(content)   => prom.complete(Try(Right(content)))
      case Failure(exception) => prom.complete(Try(Left(exception)))
    }
    val res = prom.future()
    println(s"RES=$res")
    res
  }

  def loadUri(uri: String, formats: Map[String, String]): Either[Throwable, String] =
    loadUri(reload = false, uri, formats)

  def reloadUri(uri: String, formats: Map[String, String]): Either[Throwable, String] =
    loadUri(reload = true, uri, formats)

  def reloadUris(uris: Iterator[String], formats: Map[String, String]) = {
    logger.warn(s"reloadUris:")
    uris map { reloadUri(_, formats)}
    Right("done")
  }

  def reloadAll(formats: Map[String, String]) = {
    logger.warn(s"reloadAll:")
    unloadAll(formats)
    val uris = ontService.getAllOntologyUris.toList
    logger.warn(s"loading: ${uris.size} ontologies...")
    uris map { loadUri(_, formats)}
    Right("done")
  }

  def unloadUri(uri: String, formats: Map[String, String]): Either[Throwable, String] =
    unload(Some(uri), formats)

  def unloadAll(formats: Map[String, String]): Either[Throwable, String] =
    unload(None, formats)

  ///////////////////////////////////////////////////////////////////////////

  /**
   * Loads the given ontology in the triple store.
   * If reload is true, the contents are replaced.
   */
  private def loadUri(reload: Boolean, uri: String, formats: Map[String, String]): Either[Throwable, String] = {
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
  private def unload(uriOpt: Option[String], formats: Map[String, String]): Either[Throwable, String] = {
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

  private val orrEndpoint = setup.config.getConfig("agraph").getString("orrEndpoint")
  private val svc = host(orrEndpoint)

  private val userName = setup.config.getString("agraph.userName")
  private val password = setup.config.getString("agraph.password")

}
