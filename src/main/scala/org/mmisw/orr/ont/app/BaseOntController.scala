package org.mmisw.orr.ont.app

import java.io.File

import com.typesafe.scalalogging.{StrictLogging => Logging}
import org.mmisw.orr.ont.Setup
import org.mmisw.orr.ont.db.{Ontology, OntologyVersion}
import org.mmisw.orr.ont.service.{CannotCreateFormat, NoSuch, NoSuchOntFormat, OntService}

import scala.util.{Failure, Success, Try}

/**
  */
abstract class BaseOntController(implicit setup: Setup, ontService: OntService) extends BaseController
with Logging {

  protected def resolveOntology(uri: String, versionOpt: Option[String] = None): (Ontology, OntologyVersion, String) = {
    Try(ontService.resolveOntology(uri, versionOpt)) match {
      case Success(res) => res
      case Failure(exc: NoSuch) => error(404, exc.details)
      case Failure(exc) => error(500, exc.getMessage)
    }
  }

  protected def getOntologyFile(uri: String, version: String, reqFormat: String): (File, String) = {
    Try(ontService.getOntologyFile(uri, version, reqFormat)) match {
      case Success(res) => res
      case Failure(exc: NoSuchOntFormat) => error(406, exc.details)
      case Failure(exc: CannotCreateFormat) => error(406, exc.details)
      case Failure(exc) => {
        println(s"getOntologyFile: error with uri=$uri version=$version reqFormat=$reqFormat")
        exc.printStackTrace()
        error(500, exc.getMessage)
      }
    }
  }

}
