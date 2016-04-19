package org.mmisw.orr.ont.swld

import java.io._

import com.hp.hpl.jena.ontology.OntModel
import com.hp.hpl.jena.vocabulary.{DC_10, RDFS, DC_11, DCTerms}
import com.typesafe.scalalogging.{StrictLogging => Logging}
import org.mmisw.orr.ont.util.{XmlBaseExtractor, Util2}
import org.mmisw.orr.ont.vocabulary.Omv
import org.xml.sax.InputSource


case class OntModelLoadedResult(file: File,
                                format: String,
                                ontModel: OntModel)

case class PossibleOntologyName(propertyUri: String,
                                propertyValue: String)

/** Info for a possible ontology URI */
case class PossibleOntologyInfo(explanations: List[String],
                                names: Option[List[PossibleOntologyName]])
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

    def add(uriOpt: Option[String], explanation: String): Unit = {
      for {
        uri <- uriOpt
        ontology <- Option(model.getOntology(uri))
      } {
        val newInfo = map.get(uri) match {
          case None =>
            val explanations = List(explanation)
            val namesOpt = {
              var names = List[PossibleOntologyName]()
              for (property <- possibleNameProperties) {
                ontUtil.getValue(ontology, property) foreach { propertyValue =>
                  names = PossibleOntologyName(property.getURI, propertyValue) :: names
                }
              }
              if (names.nonEmpty) Some(names) else None
            }
            PossibleOntologyInfo(explanations, namesOpt)

          case Some(info) =>
            // one more explanation for the URI
            PossibleOntologyInfo(explanation :: info.explanations, info.names)
        }

        map = map.updated(uri, newInfo)
      }
    }

    // try xml:base:
    try {
      val is = new InputSource(new StringReader(readRdf(file)))
      for {
        xmlBase <- Option(XmlBaseExtractor.getXMLBase(is))
      }
        add(Option(xmlBase.toString), "Value of xml:base attribute")
    }
    catch {
      case e: Throwable => {
        logger.warn(s"error while trying to read xml:base attribute from $file", e)
      }
    }

    // try namespace associated with empty prefix:
    Option(model.getNsPrefixURI("")) foreach { uriForEmptyPrefix =>
      add(Option(uriForEmptyPrefix), "Namespace associated with empty prefix")
      val uri = uriForEmptyPrefix.replaceAll("(#|/)+$", "")
      if (uri != uriForEmptyPrefix) {
        add(Option(uri), "Namespace associated with empty prefix but with no trailing separators")
      }
    }

    map
  }

  private val possibleNameProperties =
    List(RDFS.label, Omv.name, DCTerms.title, DC_11.title, DC_10.title)

  /**
    * Reads an RDF file.
    */
  private def readRdf(file: File): String = {
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
