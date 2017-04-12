package org.mmisw.orr.ont.swld

import java.io.File

import org.apache.jena.ontology.OntModel
import org.apache.jena.rdf.model.Model
import org.apache.jena.vocabulary.OWL
import com.typesafe.scalalogging.{StrictLogging => Logging}
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{write, writePretty}
import org.mmisw.orr.ont.vocabulary.{Omv, Skos}


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

  implicit val formats: Formats = Serialization.formats(NoTypeHints)

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
    ontModel.setNsPrefix("omv",  Omv.NS)
    ontModel.setNsPrefix("skos", Skos.NS)
    ontModel
  }

  def loadOntModel(file: File, uriOpt: Option[String] = None): OntModelLoadedResult = {
    logger.debug(s"m2r.loadOntModel: loading file=$file uriOpt=$uriOpt")

    val mr = loadM2RModel(file, simplify = true)

    val altUriOpt = uriOpt orElse Some(file.getCanonicalFile.toURI.toString)
    val ontModel = getModel(mr, altUriOpt)

    if (false && logger.underlying.isDebugEnabled()) {
      import org.mmisw.orr.ont.swld.ontUtil.writeModel
      saveM2RModel(mr,                          new File("TEST.m2r"))
      writeModel(altUriOpt.get, ontModel, "N3", new File("TEST.n3"))
    }

    OntModelLoadedResult(file, "m2r", ontModel)
  }

  def loadM2RModel(file: File, simplify: Boolean = false): M2RModel = {
    logger.debug(s"loadM2RModel: file=$file  simplify=$simplify")
    implicit val formats = DefaultFormats
    val json = parse(file)
    val mr = json.extract[M2RModel]
    if (simplify) simplifyM2RModel(mr) else mr
  }

  def loadM2RModel(contents: String): M2RModel = {
    implicit val formats = DefaultFormats
    val json = parse(contents)
    json.extract[M2RModel]
  }

  def saveM2RModel(mr: M2RModel, file: File, simplify: Boolean = false): Unit = {
    logger.debug(s"saveM2RModel: saving file=$file  simplify=$simplify")

    val model = if (simplify) simplifyM2RModel(mr) else mr

    java.nio.file.Files.write(file.toPath,
      model.toPrettyJson.getBytes(java.nio.charset.StandardCharsets.UTF_8))
  }

  /**
    * Removes duplicates in metadata and in each mapping group
    */
  def simplifyM2RModel(mr: M2RModel): M2RModel = {
    val distinctMappings = mr.mappings map { group ⇒
      group.copy(
        objects = group.objects.distinct,
        subjects = group.subjects.distinct
      )
    }
    val simplifiedMetadata = mr.metadata map { md ⇒
      md map { case (uri, value) =>
        val distinctValueElements: JValue = value match {
          case JArray(list) ⇒ JArray(list.distinct)
          case jv ⇒ jv
        }
        (uri, distinctValueElements)
      }
    }
    mr.copy(
      metadata = simplifiedMetadata,
      mappings = distinctMappings
    )
  }
}
