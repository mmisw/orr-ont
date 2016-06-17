package org.mmisw.orr.ont.service

case class TermResponse(contents: String, contentTye: String)

trait TripleStoreService {

  def setFormats(formats: Map[String, String]): Unit

  def initialize(): Unit

  def getSize(contextOpt: Option[String] = None): Either[Throwable, String]

  def loadUriFromLocal(uri: String, reload: Boolean = false): Either[Throwable, String]

  /**
   * Loads the given ontology in the triple store.
   */
  def loadUri(uri: String): Either[Throwable, String]

  /**
   * Loads the given ontology in the triple store with replacement of the triples
   * in the corresponding graph.
   */
  def reloadUri(uri: String): Either[Throwable, String]

  /**
   * Reloads the given list of ontologies.
   */
  def reloadUris(uris: Iterator[String]): Either[Throwable, String]

  /**
   * Reloads the whole triple store with all registered ontologies.
   */
  def reloadAll(): Either[Throwable, String]

  /**
   * Unloads the given ontology from the triple store.
   */
  def unloadUri(uri: String): Either[Throwable, String]

  /**
   * Clears the triple store.
   */
  def unloadAll(): Either[Throwable, String]

  /**
    * Resolves a URI via SPARQL query to retrieve associated properties.
    *
    * @param uri           Requested URI
    * @param formatOpt     Desired format, which takes precedence if given
    * @param acceptHeader  Accept header when format is not given
    */
  def resolveTermUri(uri: String, formatOpt: Option[String], acceptHeader: List[String]
                    ): Either[Error, TermResponse]

}
