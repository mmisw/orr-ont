package org.mmisw.orr.ont.app

import java.io.File

import com.novus.salat._
import com.novus.salat.global._
import com.typesafe.scalalogging.slf4j.Logging
import org.mmisw.orr.ont.db.{Ontology, OntologyVersion}
import org.mmisw.orr.ont.service.{NoSuch, NoSuchOntFormat, OntService}
import org.mmisw.orr.ont.{PendOntologyResult, Setup}

import scala.util.{Failure, Success, Try}

/**
 * Controller to dispatch "self-hosted ontology" requests, mainly for resolution (GET)
 * and not for updates/deletions (although these might be added later if useful).
 */
class SelfHostedOntController(implicit setup: Setup, ontService: OntService) extends BaseController
    with Logging {

  get("/(.*)".r) {
    multiParams("captures").headOption match {
      case Some(suffix) => if (suffix.startsWith("api")) pass() else resolve(suffix)
      case _ => pass()
    }
  }

  ///////////////////////////////////////////////////////////////////////////

  private val orgOrUserPattern = "ont/([^/]+)$".r

  private def resolve(suffix: String) = {
    logger.debug(s"resolve: suffix='$suffix'")
    orgOrUserPattern.findFirstMatchIn(suffix).toList.headOption match {
      case Some(m) => resolveOrgOrUser(m.group(1))
      case None =>
        val uri = request.getRequestURL.toString
        logger.debug(s"self-resolving '$uri' ...")
        resolveUri(uri)
    }
  }

  /*
   * Dispatches organization OR user ontology request (/ont/xyz).
   * This intends to emulate behavior in previous Ont service
   * when xyx corresponds to an existing authority abbreviation
   * (with generation of list of associated ontologies).
   * Here this is very preliminary. Also possible dispatch in the case
   * where xyz corresponds to a userName.
   * TODO review the whole thing.
   */
  private def resolveOrgOrUser(xyz: String) = {
    logger.debug(s"resolve: xyz='$xyz'")
    orgsDAO.findOneById(xyz) match {
      case Some(org) =>
        org.ontUri match {
          case Some(ontUri) => redirect(ontUri)
          case None =>
            try selfResolve
            catch {
              case exc: AnyRef =>
                logger.info(s"EXC in selfResolve: $exc")
                // TODO dispatch some synthetic response as in previous Ont
                error(500, s"TODO: generate summary for organization '$xyz'")
            }
        }
      case None =>
        usersDAO.findOneById(xyz) match {
          case Some(user) =>
            user.ontUri match {
              case Some(ontUri) => redirect(ontUri)
              case None =>
                error(500, s"TODO: generate summary for user '$xyz'")
            }
          case None => error(404, s"No organization or user by given name: '$xyz'")
        }
    }
  }

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
      case Failure(exc: NoSuch) => error(404, exc.details)
      case Failure(exc)         => error(500, exc.getMessage)
    }
  }

  private def getOntologyFile(uri: String, version: String, reqFormat: String): (File, String) = {
    Try(ontService.getOntologyFile(uri, version, reqFormat)) match {
      case Success(res)                   => res
      case Failure(exc: NoSuchOntFormat)  => error(406, exc.details)
      case Failure(exc) => error(500, exc.getMessage)
    }
  }

}
