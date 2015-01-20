package org.mmisw.orr.ont.app

import com.typesafe.scalalogging.slf4j.Logging
import dispatch._
import org.mmisw.orr.ont.Setup
import org.mmisw.orr.ont.auth.authUtil
import org.mmisw.orr.ont.service.OntService
import org.scalatra.BadRequest

import scala.concurrent.Promise
import scala.util.{Failure, Success, Try}

/**
  */
class TripleStoreController(implicit setup: Setup, ontService: OntService) extends BaseOntController
with Logging {

  import scala.concurrent.ExecutionContext.Implicits.global

  before() {
    verifyAuthenticatedUser("admin")
  }

  /*
   * Gets the size of the store or the size of a particular named graph.
   */
  get("/") {
    println(s"params=$params")
    if (setup.testing.isDefined) "9999"
    else getSize(params.get("context"))
  }

  /*
   * Loads an ontology.
   */
  post("/") {
    if (setup.testing.isDefined) "9999"
    else params.get("uri") match {
      case Some(uri) => loadUri(uri)

      case None => // TODO load all ontologies?
        BadRequest(reason = "not loading all ontologies")
    }
  }

  /*
   * Reloads an ontology.
   */
  put("/") {
    if (setup.testing.isDefined) "9999"
    else params.get("uri") match {
      case Some(uri) => reloadUri(uri)

      case None => // TODO reload all ontologies?
        BadRequest(reason = "not reloading all ontologies")
    }
  }

  /*
   * Unloads an ontology.
   */
  delete("/") {
    if (setup.testing.isDefined) "9999"
    else params.get("uri") match {
      case Some(uri) => unloadUri(uri)

      case None => // TODO clear the triple store?
        BadRequest(reason = "not clearing triple store")
    }
  }


  ///////////////////////////////////////////////////////////////////////////

  private val orrEndpoint = setup.config.getConfig("agraph").getString("orrEndpoint")
  private val svc = host(orrEndpoint)

  private def getSize(contextOpt: Option[String] = None): Either[Throwable, String] = {
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
  private def loadUri(uri: String): Either[Throwable, String] = {
    val prom = Promise[Either[Throwable, String]]()

    logger.warn(s"loadUri: $uri")
    val (_, ontVersion, version) = resolveOntology(uri)
    val (file, actualFormat) = getOntologyFile(uri, version, ontVersion.format)
    contentType = formats("json")
    status = 200

    val (k, v) = if (setup.config.hasPath("import.aquaUploadsDir"))
      ("file", file.getAbsolutePath)
    else
      ("uri", uri)

    val userName = setup.config.getString("agraph.userName")
    val password = setup.config.getString("agraph.password")

    val req = (svc / "statements")
      .setContentType(formats(actualFormat), charset = "UTF-8")
      .addQueryParameter("context", "\"" + uri + "\"")
      .addQueryParameter(k, v)
      .setHeader("Accept", contentType)
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
  private def reloadUri(uri: String) = {
    logger.warn(s"reloadUri: $uri")
    val (ont, ontVersion, version) = resolveOntology(uri)
    val (file, actualFormat) = getOntologyFile(uri, version, ontVersion.format)
    contentType = formats(actualFormat)
    status = 200
    file
  }

  // TODO actual unloading
  private def unloadUri(uri: String) = {
    logger.warn(s"unloadUri: $uri")
    val (ont, ontVersion, version) = resolveOntology(uri)
    val (file, actualFormat) = getOntologyFile(uri, version, ontVersion.format)
    contentType = formats(actualFormat)
    status = 200
    file
  }

}
