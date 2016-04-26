package org.mmisw.orr.ont.swld

import com.hp.hpl.jena.rdf.model.{Model, ModelFactory, Property}
import com.hp.hpl.jena.vocabulary.{OWL, RDF, RDFS}


case class Element(name:    Option[String] = None,
                   uri:     Option[String] = None,
                   label:   Option[String] = None
                  ) {

  def getUri(namespace: String) = {
    uri.getOrElse(namespace + name.get)
  }

  def getLabel: String = {
    label.getOrElse(name.getOrElse{
      val u = uri.getOrElse("")
      val i = Math.max(u.lastIndexOf('/'), u.lastIndexOf('#'))
      if (i >= 0 && i < u.length - 1) u.substring(i + 1) else u
    })
  }
}

case class Vocab(`class`:  Element,
                 properties:    List[Element],
                 terms:         List[List[String]]
                ) {

  def addStatements(namespace: String, model: Model): Unit = {
    val clazz = model.createResource(`class`.getUri(namespace))
    model.add(clazz, RDF.`type`, OWL.Class)

    val propList: List[Property] = properties map { pe =>
      val property = model.createProperty(pe.getUri(namespace))
      if (pe.uri.isEmpty) model.add(property, RDF.`type`, OWL.DatatypeProperty)
      //Else, we don't define any attributes for the given property
      property
    }

    terms foreach { cols =>
      val termName = cols.head

      val term = model.createResource(namespace + termName)
      model.add(term, RDF.`type`, clazz)

      val numCols = Math.min(propList.size, cols.size - 1)

      var i = 0
      while (i < numCols) {
        val prop  = propList(i)
        val value = cols(i + 1)
        model.add(term, prop, value)
        i += 1
      }
    }
  }
}

case class Voc2Rdf(namespace: String,
                   vocabs: List[Vocab]
                  ) {

  def getModel: Model = {
    val model = ModelFactory.createDefaultModel()
    vocabs foreach (_.addStatements(namespace, model))
    model
  }

}

