package org.mmisw.orr.ont.swld

import java.io.File

import com.hp.hpl.jena.ontology.{OntModel, Ontology}
import com.hp.hpl.jena.rdf.model.{Model, Property}
import com.hp.hpl.jena.vocabulary.{OWL, RDF}
import com.typesafe.scalalogging.{StrictLogging => Logging}
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{write, writePretty}


case class IdL(name:  Option[String] = None,
               uri:   Option[String] = None,
               label: Option[String] = None
              ) {
  def getUri(namespaceOpt: Option[String] = None) =
    uri.getOrElse(namespaceOpt.getOrElse("") + name.get)

  def getLabel: String = label.getOrElse(name.getOrElse {
    val u = uri.getOrElse("")
    val i = Math.max(u.lastIndexOf('/'), u.lastIndexOf('#'))
    if (i >= 0 && i < u.length - 1) u.substring(i + 1) else u
  })
}

case class Term(name:        Option[String] = None,
                uri:         Option[String] = None,
                attributes:  List[JValue]
               ) {
  def getUri(namespaceOpt: Option[String] = None) =
    uri.getOrElse(namespaceOpt.getOrElse("") + name.get)
}

case class Vocab(`class`:     IdL,
                 properties:  List[IdL],
                 terms:       List[Term]
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

    terms foreach { term =>
      val termUri = term.getUri(namespaceOpt)

      val termResource = model.createResource(termUri)
      model.add(termResource, RDF.`type`, clazz)

      if (propList.size != term.attributes.size) {
        println(s"WARN: for term=$termUri, number of attributes is different from number of properties")
      }

      (propList zip term.attributes) foreach { pa: (Property, JValue) =>
        val (prop, jValue) = pa

        val primitive: PartialFunction[JValue, Unit] = {
          case JString(v)   => model.add(       termResource, prop, v)
          case JBool(v)     => model.addLiteral(termResource, prop, v)
          case JInt(v)      => model.add(       termResource, prop, v.toString())   // BigInteger
          case JDouble(v)   => model.addLiteral(termResource, prop, v)

          case j => println(s"WARN: for term=$termUri, prop=$prop, value $j not handled")
        }

        val array: PartialFunction[JValue, Unit] = { case JArray(arr) => arr foreach primitive }

        (array orElse primitive)(jValue)
      }
    }
  }
}

case class MdEntry(uri:     String,
                   value:   JValue
                  ) {

  def addStatements(ontology: Ontology): Unit = {
    val model = Option(ontology.getModel).getOrElse(throw new RuntimeException("ontology must have model"))
    val property = model.createProperty(uri)

    val primitive: PartialFunction[JValue, Unit] = {
      case JString(v)   => model.add(       ontology, property, v)
      case JBool(v)     => model.addLiteral(ontology, property, v)
      case JInt(v)      => model.add(       ontology, property, v.toString())   // BigInteger
      case JDouble(v)   => model.addLiteral(ontology, property, v)

      case j => println(s"WARN: for property=$property, value $j not handled")
    }

    val array: PartialFunction[JValue, Unit] = { case JArray(arr) => arr foreach primitive }

    (array orElse primitive)(value)
  }
}

case class V2RModel(uri:       Option[String],
                    metadata:  Option[List[MdEntry]],
                    vocabs:    List[Vocab]
                   ) {

  def addStatements(model: OntModel, altUriOpt: Option[String] = None): Unit = {
    val uriOpt: Option[String] = altUriOpt orElse uri

    // ontology metadata:
    for {
      mdEntries <- metadata
      uri       <- uriOpt     // only add metadata if we do have a defined URI
      mdEntry   <- mdEntries
    } {
      mdEntry.addStatements(model.createOntology(uri))
    }

    // ontology contents:
    vocabs foreach (_.addStatements(model, uriOpt map (_ + "/")))
  }

  implicit val formats = Serialization.formats(NoTypeHints)

  def toJson: String = write(this)

  def toPrettyJson: String = writePretty(this)
}

object v2r extends AnyRef with Logging {

  /**
    * Gets the jena model corresponding to the given V2RModel.
    *
    * @param vr            model
    * @param altUriOpt     If given, this is used as the base URI for the model.
    *                      If not, the URI of the V2RModel is used (if defined)
    * @return OntModel
    */
  def getModel(vr: V2RModel, altUriOpt: Option[String] = None): OntModel = {
    val ontModel = ontUtil.createDefaultOntModel
    vr.addStatements(ontModel, altUriOpt)
    ontModel
  }

  def loadOntModel(file: File, uriOpt: Option[String] = None): OntModelLoadedResult = {
    logger.debug(s"v2r.loadOntModel: loading file=$file uriOpt=$uriOpt")

    implicit val formats = DefaultFormats
    val json = parse(file)
    val vr   = json.extract[V2RModel]

    val altUriOpt = uriOpt orElse Some(file.getCanonicalFile.toURI.toString)
    val model = getModel(vr, altUriOpt)
    OntModelLoadedResult(file, "v2r", model)
  }

}
