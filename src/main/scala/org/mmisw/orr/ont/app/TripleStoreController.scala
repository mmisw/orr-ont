package org.mmisw.orr.ont.app

import com.typesafe.scalalogging.slf4j.Logging
import org.mmisw.orr.ont.Setup
import org.mmisw.orr.ont.service.{OntService, TripleStoreService}
import org.scalatra.BadRequest


/**
  */
class TripleStoreController(implicit setup: Setup, ontService: OntService, tsService: TripleStoreService) extends BaseOntController
with Logging {

  before() {
    verifyAuthenticatedUser("admin")
  }

  /*
   * Gets the size of the store or the size of a particular named graph.
   */
  get("/") {
    println(s"params=$params")
    if (setup.testing.isDefined) "9999"
    else tsService.getSize(params.get("context"))
  }

  /*
   * Loads an ontology.
   */
  post("/") {
    if (setup.testing.isDefined) "9999"
    else params.get("uri") match {
      case Some(uri) => tsService.loadUri(uri, Map(formats.toSeq: _*))

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
      case Some(uri) => tsService.reloadUri(uri)

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
      case Some(uri) => tsService.unloadUri(uri)

      case None => // TODO clear the triple store?
        BadRequest(reason = "not clearing triple store")
    }
  }

}
