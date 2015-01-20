package org.mmisw.orr.ont.app

import java.io.File

import com.novus.salat._
import com.typesafe.scalalogging.slf4j.Logging
import org.mmisw.orr.ont.db.{OntologyVersion, Ontology}
import org.mmisw.orr.ont.{PendOntologyResult, Setup}
import org.mmisw.orr.ont.service.{NoSuchOntFormat, NoSuch, OntService}
import org.scalatra.BadRequest

import scala.util.{Failure, Success, Try}

/**
  */
class TripleStoreController(implicit setup: Setup, ontService: OntService) extends BaseOntController
with Logging {

  before() {
    verifyAuthenticatedUser("admin")
  }

  /*
   * Loads an ontology.
   */
  post("/") {
    params.get("uri") match {
      case Some(uri) => loadUri(uri)

      case None => // TODO load all ontologies?
        BadRequest(reason = "not loading all ontologies")
    }
  }

  /*
   * Reloads an ontology.
   */
  put("/") {
    params.get("uri") match {
      case Some(uri) => reloadUri(uri)

      case None => // TODO reload all ontologies?
        BadRequest(reason = "not reloading all ontologies")
    }
  }

  /*
   * Unloads an ontology.
   */
  delete("/") {
    params.get("uri") match {
      case Some(uri) => unloadUri(uri)

      case None => // TODO clear the triple store?
        BadRequest(reason = "not clearing triple store")
    }
  }


  ///////////////////////////////////////////////////////////////////////////

  // TODO actual loading
  private def loadUri(uri: String) = {
    logger.warn(s"loadUri: $uri")
    val (ont, ontVersion, version) = resolveOntology(uri)
    val (file, actualFormat) = getOntologyFile(uri, version, ontVersion.format)
    contentType = formats(actualFormat)
    status = 201
    file
  }

  // TODO actual reloading
  private def reloadUri(uri: String) = {
    logger.warn(s"reloadUri: $uri")
    val (ont, ontVersion, version) = resolveOntology(uri)
    val (file, actualFormat) = getOntologyFile(uri, version, ontVersion.format)
    contentType = formats(actualFormat)
    status = 201
    file
  }

  // TODO actual unloading
  private def unloadUri(uri: String) = {
    logger.warn(s"unloadUri: $uri")
    val (ont, ontVersion, version) = resolveOntology(uri)
    val (file, actualFormat) = getOntologyFile(uri, version, ontVersion.format)
    contentType = formats(actualFormat)
    status = 201
    file
  }

}
