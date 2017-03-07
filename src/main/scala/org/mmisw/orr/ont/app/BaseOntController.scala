package org.mmisw.orr.ont.app

import java.io.File

import com.typesafe.scalalogging.{StrictLogging ⇒ Logging}
import org.json4s.native.Serialization.writePretty
import org.mmisw.orr.ont.db.{Ontology, OntologyVersion}
import org.mmisw.orr.ont.service.{CannotQueryTerm, NoSuchTermFormat, _}
import org.mmisw.orr.ont.{OntologySummaryResult, OntologyVersionSummary, Setup}

import scala.util.{Failure, Success, Try}

/**
  */
abstract class BaseOntController(implicit setup: Setup,
                                 ontService: OntService,
                                 tsService: TripleStoreService
                                ) extends BaseController
with Logging {

  protected def resolveOntologyVersion(uri: String, versionOpt: Option[String] = None): (Ontology, OntologyVersion, String) = {
    Try(ontService.resolveOntologyVersion(uri, versionOpt)) match {
      case Success(res) => res
      case Failure(exc: NoSuch) => error(404, exc.details)
      case Failure(exc)         => error500(exc)
    }
  }

  protected def resolveOntologyVersion(ont: Ontology, versionOpt: Option[String]): (OntologyVersion, String) = {
    Try(ontService.resolveOntologyVersion(ont, versionOpt)) match {
      case Success(res)         => res
      case Failure(exc: NoSuch) => error(404, exc.details)
      case Failure(exc)         => error500(exc)
    }
  }

  protected def getOntologyFile(uri: String, version: String, reqFormat: String): (File, String) = {
    Try(ontService.getOntologyFile(uri, version, reqFormat)) match {
      case Success(res) => res
      case Failure(exc: NoSuchOntFormat)    => error(406, exc.details)
      case Failure(exc: CannotCreateFormat) => error(406, exc.details)
      case Failure(exc)                     => error500(exc)
    }
  }

  /**
    * Parameter reqFormatOpt mainly for purposes of the self-resolution mechanism.
    *
    * @param uri              URI to be resolved
    * @param reqFormatOpt     if already captured
    */
  protected def resolveOntOrTermUri(uri: String,
                                    reqFormatOpt: Option[String] = None
                                   ) = {

    // try to resolve ontology, possibly with http scheme change:
    val ontologyResolvedOpt = ontService.resolveOntology(uri)
    ontologyResolvedOpt match {
      case Some(ont) => completeOntologyUriResolution(ont, reqFormatOpt)
      case None => resolveTermUri(uri, reqFormatOpt)
    }
  }

  protected def checkOntUriExistence(uri: String): OntologySummaryResult = {
    ontService.resolveOntology(uri) match {
      case Some(ont) ⇒
        OntologySummaryResult(
          uri          = ont.uri,
          ownerName    = Some(ont.ownerName)
        )

      case None    ⇒ error(404, s"'$uri': No such ontology")
    }
  }

  protected def resolveOntUri(uri: String) = {
    ontService.resolveOntology(uri) match {
      case Some(ont) => completeOntologyUriResolution(ont)
      case None      => error(404, s"'$uri': No such ontology")
    }
  }

  protected def completeOntologyUriResolution(ont: Ontology, reqFormatOpt: Option[String] = None) = {
    val versionOpt: Option[String] = params.get("version")
    val (ontVersion, version) = resolveOntologyVersion(ont, versionOpt)

    // format is the one given if any, or the one in the db:
    val reqFormat = reqFormatOpt.getOrElse(params.get("format").getOrElse(ontVersion.format))

    // format=!md is our mechanism to request for metadata

    if (reqFormat == "!md") {
      // include 'versions' even when a particular version is requested
      val versions = ont.sortedVersionKeys map { v ⇒
        val ontVersion = ont.versions(v)
        OntologyVersionSummary(v,
          log          = ontVersion.log,
          visibility   = ontVersion.visibility,
          status       = ontVersion.status
        )
      }
      val ores = ontService.getOntologySummaryResult(ont, ontVersion, version,
        privileged = checkIsAdminOrExtra,
        includeMetadata = true,
        Some(versions)
      )
      writePretty(ores)
    }
    else {
      val (file, actualFormat) = getOntologyFile(ont.uri, version, reqFormat)
      contentType = formats(actualFormat)
      logger.debug(s"completeOntologyUriResolution: responding ${file.getAbsolutePath} contentType=$contentType")
      file
    }
  }

  protected def resolveTermUri(uri: String, reqFormatOpt: Option[String] = None): String = {
    val formatOpt = reqFormatOpt orElse params.get("format")
    tsService.resolveTermUri(uri, formatOpt, acceptHeader) match {
      case Right(TermResponse(result, resultContentType)) =>
        contentType = resultContentType
        result

      case Left(exc) =>
        exc match {
          case NoSuchTermFormat(_, format) => error(406, s"invalid format=$format")
          case CannotQueryTerm(_, msg)     => error(400, s"error querying uri=$uri: $msg")
          case _                           => error500(exc)
        }

      case null => error500(s"Unexpected: got null but Either expected -- Scala compiler bug?")
        // Noted with "self-hosted" test in SequenceSpec
        // The tsService.resolveTermUri above doesn't seem to be called, or at least not
        // properly called because it returns null right away; I even put an immediate
        // 'throw exception' there and it is not triggered at all!
    }
  }
}
