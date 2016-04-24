package org.mmisw.orr.ont.vocabulary

import com.hp.hpl.jena.rdf.model.Model
import com.hp.hpl.jena.rdf.model.ModelFactory
import com.hp.hpl.jena.rdf.model.Resource
import com.hp.hpl.jena.vocabulary.OWL
import com.hp.hpl.jena.vocabulary.RDF
import com.hp.hpl.jena.vocabulary.RDFS
import com.hp.hpl.jena.vocabulary.XSD

/**
 * SKOS vocabulary elements. Copied from cf2rdf.
 *
 * @todo move this object to a more appropriate place.
 *
 * @author Carlos Rueda
 * @see http://www.w3.org/2004/02/skos/core#
 */
object Skos {
  private val model = ModelFactory.createDefaultModel()

  val NS = "http://www.w3.org/2004/02/skos/core#"

  private def property(local: String) = model.createProperty(NS, local)

  val Concept        = model.createResource(NS + "Concept",    OWL.Class)
  val Collection     = model.createResource(NS + "Collection", OWL.Class)

  val prefLabel      = property("prefLabel")
  val altLabel       = property("altLabel")
  val hiddenLabel    = property("hiddenLabel")

  val definition     = property("definition")
  val note           = property("note")
  val changeNote     = property("changeNote")
  val editorialNote  = property("editorialNote")
  val example        = property("example")
  val historyNote    = property("historyNote")
  val scopeNote      = property("scopeNote")
  val hasTopConcept  = property("hasTopConcept")

  val broader        = property("broader")
  val narrower       = property("narrower")
  val related        = property("related")
  val exactMatch     = property("exactMatch")
  val closeMatch     = property("closeMatch")
  val broadMatch     = property("broadMatch")
  val narrowMatch    = property("narrowMatch")
  val relatedMatch   = property("relatedMatch")

  def createModel: Model = {
    val newModel = ModelFactory.createDefaultModel()
    newModel.setNsPrefix("skos", NS)
    newModel.add(model)
    //newModel.add(Skos.broader, OWL.inverseOf, Skos.narrower)
    //newModel.add(Skos.broader, RDF.`type`,  OWL.TransitiveProperty)
    //newModel.add(Skos.broader, RDFS.range,  OWL.TransitiveProperty)
    //newModel.add(Skos.narrower, RDF.`type`,  OWL.TransitiveProperty)
    //newModel.add(Skos.related, RDF.`type`, OWL.SymmetricProperty)
    newModel
  }

  def addConceptSubClass(model: Model, conceptUri: String) = {
    val conceptSubClass = model.createResource(conceptUri)
    model.add(model.createStatement(conceptSubClass, RDF.`type`, OWL.Class))
    model.add(model.createStatement(conceptSubClass, RDFS.subClassOf, Skos.Concept))
    conceptSubClass
  }

  def addDatatypeProperty(model: Model, conceptSubClass: Resource, propUri:String, propLabel:String) = {
    val prop = model.createProperty(propUri)
    model.add(model.createStatement(prop, RDF.`type`, OWL.DatatypeProperty))
    model.add(model.createStatement(prop, RDFS.domain, conceptSubClass))
    model.add(model.createStatement(prop, RDFS.range, XSD.xstring))
    if ( propLabel != null ){
      model.add(model.createStatement(prop, RDFS.label, propLabel))
    }
    prop
  }
}
