package org.mmisw.orr.ont.service

import com.mongodb.casbah.commons.MongoDBObject

/**
 * preliminary
 */
trait TripleStoreService {

  def getSize(contextOpt: Option[String] = None): Either[Throwable, String]

  /**
   * Loads the given ontology in the triple store.
   */
  def loadUri(uri: String, formats: Map[String, String]): Either[Throwable, String]

  /**
   * Loads the given ontology in the triple store with replacement of the triples
   * in the corresponding graph.
   */
  def reloadUri(uri: String, formats: Map[String, String]): Either[Throwable, String]

  /**
   * Reloads the given list of ontologies.
   */
  def reloadUris(uris: Iterator[String], formats: Map[String, String]): Either[Throwable, String]

  /**
   * Reloads the whole triple store with all registered ontologies.
   */
  def reloadAll(formats: Map[String, String]): Either[Throwable, String]

  /**
   * Unloads the given ontology from the triple store.
   */
  def unloadUri(uri: String, formats: Map[String, String]): Either[Throwable, String]

  /**
   * Clears the triple store.
   */
  def unloadAll(formats: Map[String, String]): Either[Throwable, String]

}
