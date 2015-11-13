package org.mmisw.orr.ont.app

import com.mongodb.casbah.Imports._
import com.typesafe.scalalogging.{StrictLogging => Logging}
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
    else tsService.getSize(params.get("uri").orElse(params.get("context")))
  }

  /*
   * Loads an ontology.
   */
  post("/") {
    if (setup.testing.isDefined) "9999"
    else {
      val uri = require(params, "uri")
      tsService.loadUri(uri, Map(formats.toSeq: _*))
    }
  }

  /*
   * Reloads an ontology.
   */
  put("/") {
    if (setup.testing.isDefined) "9999"
    else params.get("uri") match {
      case Some(uri) => tsService.reloadUri(uri, Map(formats.toSeq: _*))
      case None      =>
        val query = MongoDBObject()  // todo: capture query params
        var uris = ontService.getOntologyUris(query)
        tsService.reloadAll(Map(formats.toSeq: _*))
    }
  }

  /*
   * Unloads an ontology, or the whole triple store.
   */
  delete("/") {
    if (setup.testing.isDefined) "9999"
    else params.get("uri") match {
      case Some(uri) => tsService.unloadUri(uri, Map(formats.toSeq: _*))
      case None      => tsService.unloadAll(Map(formats.toSeq: _*))
    }
  }

}
