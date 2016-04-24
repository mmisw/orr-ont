package org.mmisw.orr.ont.swld

import java.io._

import com.hp.hpl.jena.ontology.OntModel
import com.hp.hpl.jena.rdf.model.Resource
import com.hp.hpl.jena.vocabulary._
import com.typesafe.scalalogging.{StrictLogging => Logging}
import org.mmisw.orr.ont.util.{Util2, XmlBaseExtractor}
import org.mmisw.orr.ont.vocabulary.Skos
import org.xml.sax.InputSource


case class OntModelLoadedResult(file: File,
                                format: String,
                                ontModel: OntModel)

/** Info for a possible ontology URI */
case class PossibleOntologyInfo(explanations: List[String],
                                metadata: Map[String,List[String]])

/**
  * Based on org.mmisw.orrclient.core.util.TempOntologyHelper.getTempOntologyInfo
  */
object ontFileLoader extends AnyRef with Logging {

  def loadOntModel(file: File, fileType: String): OntModelLoadedResult = {
    val lang = ontUtil.format2lang(fileType).getOrElse(
      throw new RuntimeException(s"unrecognized fileType=$fileType")
    )

    logger.debug(s"ontFileLoader.loadOntModel: lang=$lang")

    if (Util2.JENA_LANGS.contains(lang)) {
      OntModelLoadedResult(file, fileType, Util2.loadOntModel(file, lang))
    }
    else if ("OWL/XML" == lang) {
      owlApiHelper.loadOntModel(file)
    }
    //else if ("voc2skos" == lang) {
    //  model = Voc2Skos.loadOntModel(file)
    //}
    else {
      val error = "Unexpected FileType. Please report this bug. " + "(" + lang + ")"
      logger.warn(error)
      throw new RuntimeException(error)
    }
  }

  def getPossibleOntologyUris(model: OntModel, file: File): Map[String, PossibleOntologyInfo] = {
    var map = Map[String, PossibleOntologyInfo]()

    def add(uri: String, explanation: String): Unit = {
      for (resource <- Option(model.getResource(uri))) {
        val newInfo = map.get(uri) match {
          case None =>
            PossibleOntologyInfo(List(explanation), ontUtil.extractAttributes(resource))

          case Some(info) =>  // one more explanation for the URI
            info.copy(explanations = explanation :: info.explanations)
        }

        map = map.updated(uri, newInfo)
      }
    }

    def tryResourcesWithType(resource: Resource): Unit = {
      logger.debug(s"tryResourcesWithType $resource")
      val it = model.listResourcesWithProperty(RDF.`type`, resource)
      while (it.hasNext) {
        val res = it.nextResource()
        add(res.getURI, s"Resource of type $resource")
      }
    }

    def tryXmlBase(): Unit = extractXmlBase(file) foreach(add(_, "Value of xml:base attribute"))

    def tryEmptyPrefix(): Unit = {
      logger.debug("tryEmptyPrefix")
      Option(model.getNsPrefixURI("")) foreach { uriForEmptyPrefix =>
        val uriNoTrailingSeparator = uriForEmptyPrefix.replaceAll("(#|/)+$", "")
        // only add uriForEmptyPrefix or uriNoTrailingSeparator if not already added per xml:base above
        if (map.get(uriForEmptyPrefix).isEmpty && map.get(uriNoTrailingSeparator).isEmpty) {
          add(uriForEmptyPrefix, "Namespace associated with empty prefix")
          if (uriNoTrailingSeparator != uriForEmptyPrefix) {
            add(uriNoTrailingSeparator, "Namespace associated with empty prefix but with no trailing separators")
          }
        }
      }
    }

    tryResourcesWithType(OWL.Ontology)
    if (map.isEmpty) {
      tryXmlBase()
      tryResourcesWithType(Skos.Collection)
      tryEmptyPrefix()
    }

    map
  }

  private def extractXmlBase(file: File): Option[String] = {
    try {
      val is = new InputSource(new StringReader(readAll(file)))
      for (xmlBase <- Option(XmlBaseExtractor.getXMLBase(is)))
        yield xmlBase.toString
    }
    catch {
      case e: Throwable => {
        logger.warn(s"error while trying to read xml:base attribute from $file", e)
        None
      }
    }
  }

  private def readAll(file: File): String = {
    val is: Reader = new InputStreamReader(new FileInputStream(file))
    try {
      val sw: StringWriter = new StringWriter
      val os: PrintWriter = new PrintWriter(sw)
      copyStream(is, os)
      os.flush()
      sw.toString
    }
    finally {
      try is.close()
      catch { case ignore: IOException => }
    }
  }

  private def copyStream(input: Reader, output: Writer): Long = {
    val buffer: Array[Char] = new Array[Char](8 * 1024)
    var count: Long = 0
    var n = input.read(buffer)
    while (-1 != n) {
      output.write(buffer, 0, n)
      count += n
      n = input.read(buffer)
    }
    count
  }
}
