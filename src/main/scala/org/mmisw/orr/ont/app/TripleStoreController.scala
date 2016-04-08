package org.mmisw.orr.ont.app

import com.typesafe.scalalogging.{StrictLogging => Logging}
import org.mmisw.orr.ont.Setup
import org.mmisw.orr.ont.service.{OntService, TripleStoreService}


/**
  */
class TripleStoreController(implicit setup: Setup, ontService: OntService, tsService: TripleStoreService) extends BaseOntController
with Logging {

  tsService.setFormats(Map(formats.toSeq: _*))

  before() {
    verifyIsAuthenticatedUser("admin")
  }

  /*
   * Gets the size of the store or the size of a particular named graph.
   */
  get("/") {
    logger.debug(s"GET params=$params")
    tsService.getSize(params.get("uri").orElse(params.get("context")))
  }

  /*
   * Loads an ontology.
   */
  post("/") {
    logger.debug(s"POST params=$params")
    val uri = require(params, "uri")
    tsService.loadUri(uri)
  }

  /*
   * Reloads an ontology by given uri, or all if not uri parameter given
   */
  put("/") {
    logger.debug(s"PUT params=$params")
    params.get("uri") match {
      case Some(uri) => tsService.reloadUri(uri)
      case None      => tsService.reloadAll()
    }
  }

  /*
   * Unloads an ontology, or the whole triple store.
   */
  delete("/") {
    logger.debug(s"DELETE params=$params")
    params.get("uri") match {
      case Some(uri) => tsService.unloadUri(uri)
      case None      => tsService.unloadAll()
    }
  }

}
