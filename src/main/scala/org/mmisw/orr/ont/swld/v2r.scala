package org.mmisw.orr.ont.swld

import com.hp.hpl.jena.rdf.model.{Model, ModelFactory, Property}
import com.hp.hpl.jena.vocabulary.{OWL, RDF}
import org.json4s._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{writePretty, write}


case class Element(name:   Option[String] = None,
                   uri:    Option[String] = None,
                   label:  Option[String] = None
                  ) {

  def getUri(namespaceOpt: Option[String]) = uri.getOrElse(namespaceOpt.getOrElse("") + name.get)

  def getLabel: String = label.getOrElse(name.getOrElse {
    val u = uri.getOrElse("")
    val i = Math.max(u.lastIndexOf('/'), u.lastIndexOf('#'))
    if (i >= 0 && i < u.length - 1) u.substring(i + 1) else u
  })
}

case class Vocab(`class`:     Element,
                 properties:  List[Element],
                 terms:       List[List[String]]
                ) {

  def addStatements(model: Model, namespaceOpt: Option[String] = None): Unit = {
    val clazz = model.createResource(`class`.getUri(namespaceOpt))
    model.add(clazz, RDF.`type`, OWL.Class)

    val propList: List[Property] = properties map { pe =>
      val property = model.createProperty(pe.getUri(namespaceOpt))
      if (pe.uri.isEmpty) model.add(property, RDF.`type`, OWL.DatatypeProperty)
      //Else, we don't define any attributes for the given property
      property
    }

    terms foreach { cols =>
      val termName = cols.head

      val term = model.createResource(namespaceOpt.getOrElse("") + termName)
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

case class V2RModel(namespace: Option[String],
                    vocabs:    List[Vocab]
                   ) {

  def addStatements(model: Model, namespaceOpt: Option[String] = None): Unit = {
    vocabs foreach (_.addStatements(model, namespaceOpt orElse namespace))
  }

  implicit val formats = Serialization.formats(NoTypeHints)

  def toJson: String = write(this)

  def toPrettyJson: String = writePretty(this)
}

object v2r {

  /**
    * Gets the jena model corresponding to the given V2RModel.
    * @param vr            model
    * @param namespaceOpt  If given, this is used as the resulting namespace in the model.
    *                      If not, the namespace of the V2RModel is used (if defined)
    * @return
    */
  def getModel(vr: V2RModel, namespaceOpt: Option[String] = None): Model = {
    val model = ModelFactory.createDefaultModel()
    vr.addStatements(model, namespaceOpt)
    model
  }
}
