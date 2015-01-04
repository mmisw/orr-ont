package org.mmisw.orr.ont

import java.io.File

import com.novus.salat._
import com.novus.salat.global._
import com.typesafe.scalalogging.slf4j.Logging
import org.mmisw.orr.ont.db.{OntologyVersion, Ontology}

import scala.util.{Failure, Success, Try}

/**
 * Controller to dispatch "self-hosted ontology" requests, mainly for resolution (GET)
 * and not for updates/deletions (although these might be added later if useful).
 */
class SelfHostedOntController(implicit setup: Setup, ontService: OntService) extends BaseController
    with Logging {

  get("/(.*)".r) {
    multiParams("captures").headOption match {
      case Some(someSuffix) => if (someSuffix.startsWith("api")) pass() else selfResolve
      case _ => pass()
    }
  }

  ///////////////////////////////////////////////////////////////////////////

  private def selfResolve = {
    val uri = request.getRequestURL.toString
    logger.debug(s"self-resolving '$uri' ...")
    resolveUri(uri)
  }

  private def resolveUri(uri: String) = {
    val (ont, ontVersion, version) = resolveOntology(uri, params.get("version"))

    // format is the one given if any, or the one in the db:
    val reqFormat = params.get("format").getOrElse(ontVersion.format)

    // TODO determine mechanism to request for file contents or metadata:  format=!md is preliminary

    if (reqFormat == "!md") {
      val ores = PendOntologyResult(ont.uri, ontVersion.name, ont.orgName, ont.sortedVersionKeys)
      grater[PendOntologyResult].toCompactJSON(ores)
    }
    else {
      val (file, actualFormat) = getOntologyFile(uri, version, reqFormat)
      contentType = formats(actualFormat)
      file
    }
  }

  private def resolveOntology(uri: String, versionOpt: Option[String]): (Ontology, OntologyVersion, String) = {
    Try(ontService.resolveOntology(uri, versionOpt)) match {
      case Success(res)         => res
      case Failure(exc: NoSuch) => error(400, exc.message)
      case Failure(exc)         => error(500, exc.getMessage)
    }
  }

  private def getOntologyFile(uri: String, version: String, reqFormat: String): (File, String) = {
    Try(ontService.getOntologyFile(uri, version, reqFormat)) match {
      case Success(res)                   => res
      case Failure(exc: NoSuchOntFormat)  => error(406, exc.message)
      case Failure(exc) => error(500, exc.getMessage)
    }
  }

}
