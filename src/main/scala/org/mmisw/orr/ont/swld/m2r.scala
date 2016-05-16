package org.mmisw.orr.ont.swld

import java.io.File

import com.hp.hpl.jena.ontology.OntModel
import com.hp.hpl.jena.rdf.model.Model
import com.hp.hpl.jena.vocabulary.OWL
import com.typesafe.scalalogging.{StrictLogging => Logging}
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{write, writePretty}


case class M2RMapGroup(subjects:   List[String],
                       predicate:  String,
                       objects:    List[String]
                      ) {

  def addStatements(model: Model): Unit = {
    val predicateProperty = model.createProperty(predicate)
    subjects foreach { subjectUri =>
      val subjectResource = model.createResource(subjectUri)
      objects foreach { objectUri =>
        val objectResource = model.createResource(objectUri)
        if (!model.contains(subjectResource, predicateProperty, objectResource))
          model.add(subjectResource, predicateProperty, objectResource)
      }
    }
  }
}

case class M2RModel(uri:        Option[String],
                    metadata:   Option[Map[String, JValue]],
                    mappedOnts: List[String],
                    mappings:   List[M2RMapGroup]
                   ) {

  def addStatements(model: OntModel, altUriOpt: Option[String] = None): Option[String] = {
    val uriOpt: Option[String] = altUriOpt orElse uri

    // ontology metadata and imports (only add if we do have a defined URI)
    for (uri <- uriOpt) {
      val ontology = model.createOntology(uri)
      metadata foreach { ontUtil.addMetadata(model, ontology, _) }

      mappedOnts foreach { mappedOntUri =>
        val mappedOntResource = model.createResource(mappedOntUri)
        model.add(ontology, OWL.imports, mappedOntResource)
      }
    }

    // ontology data:
    mappings foreach (_.addStatements(model))

    uriOpt
  }

  implicit val formats = Serialization.formats(NoTypeHints)

  def toJson: String = write(this)

  def toPrettyJson: String = writePretty(this)
}

object m2r extends AnyRef with Logging {

  /**
    * Gets the jena model corresponding to the given M2RModel.
    *
    * @param mr            model
    * @param altUriOpt     If given, this is used as the base URI for the model.
    *                      If not, the URI of the M2RModel is used (if defined)
    * @return OntModel
    */
  def getModel(mr: M2RModel, altUriOpt: Option[String] = None): OntModel = {
    val ontModel = ontUtil.createDefaultOntModel
    val uriOpt = mr.addStatements(ontModel, altUriOpt)
    uriOpt foreach(uri => ontModel.setNsPrefix("", uri + "/"))
    ontModel
  }

  def loadOntModel(file: File, uriOpt: Option[String] = None): OntModelLoadedResult = {
    logger.debug(s"m2r.loadOntModel: loading file=$file uriOpt=$uriOpt")

    implicit val formats = DefaultFormats
    val json = parse(file)
    val mr   = json.extract[M2RModel]

    val altUriOpt = uriOpt orElse Some(file.getCanonicalFile.toURI.toString)
    val model = getModel(mr, altUriOpt)
    OntModelLoadedResult(file, "m2r", model)
  }
}
