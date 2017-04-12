package org.mmisw.orr.ont.vocabulary

import org.apache.jena.rdf.model.{Model, ModelFactory}

/**
 * Vocabulary definitions for OmvMmi following
 * <a href="http://marinemetadata.org/files/mmi/OntologyExampleOMV.owl">this example</a>
 * in a similar way as with
 * <a href="http://jena.sourceforge.net/javadoc/org.apache.jena/vocabulary/DC_11.html">DC_11 in Jena</a>.
 *
 * @author Carlos Rueda
 */
object OmvMmi {

  private val m_model: Model = ModelFactory.createDefaultModel

  val NS: String = "http://mmisw.org/ont/mmi/20081020/ontologyMetadata/"

  /** OM.5 ; source of omv:acronym */
  val shortNameUri = m_model.createProperty(NS, "shortNameUri")

  /** UL.1 */
  val contact = m_model.createProperty(NS, "contact")

  /** UL.2 */
  val contactRole = m_model.createProperty(NS, "contactRole")

  /** UL.5 */
  val accessStatus = m_model.createProperty(NS, "accessStatus")

  /** UL.8 */
  val accessStatusDate = m_model.createProperty(NS, "accessStatusDate")

  /** UL.9 ; until omv:hasLicense */
  val licenseCode = m_model.createProperty(NS, "licenseCode")

  /** UL.10 */
  val licenseReference = m_model.createProperty(NS, "licenseReference")

  /** UL.11 */
  val licenseAsOfDate = m_model.createProperty(NS, "licenseAsOfDate")

  /** UL.12 */
  val temporaryMmiRole = m_model.createProperty(NS, "temporaryMmiRole")

  /** UL.13 */
  val agreedMmiRole = m_model.createProperty(NS, "agreedMmiRole")

  /** UL.17 */
  val creditRequired = m_model.createProperty(NS, "creditRequired")

  /** UL.18 */
  val creditConditions = m_model.createProperty(NS, "creditConditions")

  /** UL.19 */
  val creditCitation = m_model.createProperty(NS, "creditCitation")

  /** OSI.1 */
  val origVocUri = m_model.createProperty(NS, "origVocUri")

  /** OSI.3 */
  val origVocManager = m_model.createProperty(NS, "origVocManager")

  /** OSI.7 */
  val origVocDocumentationUri = m_model.createProperty(NS, "origVocDocumentationUri")

  /** OSI.9.1 */
  val origVocShortName = m_model.createProperty(NS, "origVocShortName")

  /** OSI.9.2 */
  val origVocDescriptiveName = m_model.createProperty(NS, "origVocDescriptiveName")

  /** OSI.9.3 */
  val origVocVersionId = m_model.createProperty(NS, "origVocVersionId")

  /** OSI.9.4 */
  val origVocKeywords = m_model.createProperty(NS, "origVocKeywords")

  /** OSI.9.5 */
  val origVocSyntaxFormat = m_model.createProperty(NS, "origVocSyntaxFormat")

  /** OSM.1 */
  val origMaintainerCode = m_model.createProperty(NS, "origMaintainerCode")

  /** Instance of {@link Omv#usedOntologyEngineeringTool} */
  val voc2rdf = m_model.createProperty(NS, "voc2rdf")

  /** Instance of {@link Omv#usedOntologyEngineeringTool} */
  val vine = m_model.createProperty(NS, "vine")

  /** See <a href="http://code.google.com/p/mmisw/issues/detail?id=148">Issue #148</a> */
  val hasContentCreator = m_model.createProperty(NS, "hasContentCreator")

  /** See <a href="http://code.google.com/p/mmisw/issues/detail?id=99">Issue #99</a> */
  val hasResourceType = m_model.createProperty(NS, "hasResourceType")
}
