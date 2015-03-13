package org.mmisw.orr.ont.vocabulary

import com.hp.hpl.jena.rdf.model.{Model, ModelFactory}

/**
 * Vocabulary definitions for Omv following
 * <a href="http://marinemetadata.org/files/mmi/OntologyExampleOMV.owl">this example</a>
 * in a similar way as with
 * <a href="http://jena.sourceforge.net/javadoc/com/hp/hpl/jena/vocabulary/DC_11.html">DC_11 in Jena</a>.
 *
 * @author Carlos Rueda
 */
object Omv {

  private val m_model: Model = ModelFactory.createDefaultModel

  val NS: String = "http://omv.ontoware.org/2005/05/ontology#"

  /** OM.1 */
  val uri = m_model.createProperty(NS, "uri")

  /** OM.22 */
  val name = m_model.createProperty(NS, "name")

  /** OM.9 */
  val description = m_model.createProperty(NS, "description")

  /** OM.4 ; is really "resourceType", not acronym */
  val acronym = m_model.createProperty(NS, "acronym")

  /** OM.2 */
  val version = m_model.createProperty(NS, "version")

  /** OM.12 */
  val keywords = m_model.createProperty(NS, "keywords")

  /** OSI.5 */
  val hasCreator = m_model.createProperty(NS, "hasCreator")

  /** OM.3 ; creation date of this version */
  val creationDate = m_model.createProperty(NS, "creationDate")

  /** OM.23 */
  val hasDomain = m_model.createProperty(NS, "hasDomain")

  /** OM.25 ; same as uri + '.owl' */
  val resourceLocator = m_model.createProperty(NS, "resourceLocator")

  /** OM.15 */
  val documentation = m_model.createProperty(NS, "documentation")

  /** OM.16 */
  val reference = m_model.createProperty(NS, "reference")

  /** OM.17 */
  val naturalLanguage = m_model.createProperty(NS, "naturalLanguage")

  /** OM.21 */
  val hasContributor = m_model.createProperty(NS, "hasContributor")

  /** OM.19 */
  val hasPriorVersion = m_model.createProperty(NS, "hasPriorVersion")

  val usedOntologyEngineeringTool = m_model.createProperty(NS, "usedOntologyEngineeringTool")
}
