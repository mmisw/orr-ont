package org.mmisw.orr.ont.app

import com.typesafe.scalalogging.{StrictLogging => Logging}
import org.mmisw.orr.ont.Setup
import org.mmisw.orr.ont.service.{OntService, TripleStoreService}


class TripleStoreController(implicit setup: Setup, ontService: OntService, tsService: TripleStoreService) extends BaseController
with Logging {

  tsService.setFormats(Map(formats.toSeq: _*))

  /*
   * triple store initialization.
   * See also post("/_init") below, which will allow to re-attempt this
   * operation with an explicit request in case the call here fails for
   * some reason (possibly because the AG server was not running).
   */
  tsService.initialize()

  before() {
    verifyIsAdminOrExtra()
  }

  /*
   * Gets the size of the store or the size of a particular named graph.
   */
  get("/") {
    logger.debug(s"GET params=$params")
    tsService.getSize(params.get("uri").orElse(params.get("context"))) match {
      case Right(result) ⇒ result
      case Left(exc) ⇒ error(400, exc.getMessage)
    }
  }

  /*
   * special request to re-try to initialize triple store.
   */
  post("/_init") {
    tsService.initialize()
  }

  /*
   * Loads an ontology.
   */
  post("/") {
    logger.debug(s"POST params=$params")
    val uri = require(params, "uri")
    tsService.loadUri(uri) match {
      case Right(result) ⇒ result
      case Left(exc) ⇒ error(400, exc.getMessage)
    }
  }

  /*
   * Reloads an ontology by given uri, or all if not uri parameter given
   */
  put("/") {
    logger.debug(s"PUT params=$params")
    val reloadResult = params.get("uri") match {
      case Some(uri) => tsService.reloadUri(uri)
      case None      => tsService.reloadAll()
    }
    reloadResult match {
      case Right(result) ⇒ result
      case Left(exc) ⇒ error(400, exc.getMessage)
    }
  }

  /*
   * Unloads an ontology, or the whole triple store.
   */
  delete("/") {
    logger.debug(s"DELETE params=$params")
    val reloadResult = params.get("uri") match {
      case Some(uri) => tsService.unloadUri(uri)
      case None      => tsService.unloadAll()
    }
    reloadResult match {
      case Right(result) ⇒ result
      case Left(exc) ⇒ error(400, exc.getMessage)
    }
  }
}
