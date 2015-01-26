package org.mmisw.orr.ont.service

import com.typesafe.scalalogging.slf4j.Logging
import dispatch._
import org.mmisw.orr.ont.Setup
import org.mmisw.orr.ont.auth.authUtil

import scala.concurrent.Promise
import scala.util.{Failure, Success, Try}

/**
 * Implementation based on AG REST endpoint (incomplete)
 */
class TripleStoreServiceAgRest(implicit setup: Setup, ontService: OntService) extends BaseService(setup)
with TripleStoreService with Logging {

  import scala.concurrent.ExecutionContext.Implicits.global

  def getSize(contextOpt: Option[String] = None): Either[Throwable, String] = {
    val prom = Promise[Either[Throwable, String]]()

    val sizeReq = contextOpt match {
      case Some(context) => (svc / "size").addQueryParameter("context", context)
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

  // TODO actual loading
  def loadUri(uri: String, formats: Map[String, String]): Either[Throwable, String] = {
    val prom = Promise[Either[Throwable, String]]()

    logger.warn(s"loadUri: $uri")
    val (_, ontVersion, version) = ontService.resolveOntology(uri)
    val (file, actualFormat) = ontService.getOntologyFile(uri, version, ontVersion.format)

    val (k, v) = if (setup.config.hasPath("import.aquaUploadsDir"))
      ("file", file.getAbsolutePath)
    else
      ("url", uri)

    val userName = setup.config.getString("agraph.userName")
    val password = setup.config.getString("agraph.password")

    val req = (svc / "statements")
      .setContentType(formats(actualFormat), charset = "UTF-8")
      .addQueryParameter("context", "\"" + uri + "\"")
      .addQueryParameter(k, v)
      .setHeader("Accept", formats("json"))
      .setHeader("Authorization", authUtil.basicCredentials(userName, password))

    println(s"REQ query params=${req.toRequest.getQueryParams.toString}")
    println(s"REQ headers=${req.toRequest.getHeaders.toString}")
    val complete = dispatch.Http(req.POST OK as.String)
    complete onComplete {
      case Success(content)   => prom.complete(Try(Right(content)))
      case Failure(exception) => prom.complete(Try(Left(exception)))
    }

    val res = prom.future()
    println(s"RES=$res")
    res
  }

  // TODO actual reloading
  def reloadUri(uri: String) = {
    logger.warn(s"reloadUri: $uri")
    val (ont, ontVersion, version) = ontService.resolveOntology(uri)
    val (file, actualFormat) = ontService.getOntologyFile(uri, version, ontVersion.format)
    Right("TODO")
  }

  // TODO actual unloading
  def unloadUri(uri: String) = {
    logger.warn(s"unloadUri: $uri")
    val (ont, ontVersion, version) = ontService.resolveOntology(uri)
    val (file, actualFormat) = ontService.getOntologyFile(uri, version, ontVersion.format)
    Right("TODO")
  }

  ///////////////////////////////////////////////////////////////////////////

  private val orrEndpoint = setup.config.getConfig("agraph").getString("orrEndpoint")
  private val svc = host(orrEndpoint)

}
