package org.mmisw.orr.ont.swld

import java.io._

import com.hp.hpl.jena.ontology.OntModel
import com.typesafe.scalalogging.{StrictLogging => Logging}
import org.mmisw.orr.ont.util.{XmlBaseExtractor, Util2}
import org.xml.sax.InputSource

/**
  * Based on org.mmisw.orrclient.core.util.TempOntologyHelper.getTempOntologyInfo
  */
object ontFileLoader extends AnyRef with Logging {

  def loadOntModel(file: File, fileType: String): (File, OntModel) = {
    val lang = ontUtil.format2lang(fileType).getOrElse(
      throw new RuntimeException(s"unrecognized fileType=$fileType")
    )

    logger.debug(s"ontFileLoader.loadOntModel: lang=$lang")

    if (Util2.JENA_LANGS.contains(lang)) {
      (file, Util2.loadOntModel(file, lang))
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

  /**
    * <ul>
    * <li> namespace associated with the empty prefix, if any;
    * <li> URI of the xml:base of the document, if any;
    * </ul>
    */
  def getNamespaces(model: OntModel, file: File): Map[String,String] = {
    var map = Map[String,String]()

    Option(model.getNsPrefixURI("")) foreach { uriForEmptyPrefix =>
        map = map.updated("uriForEmptyPrefix", uriForEmptyPrefix)
    }

    try {
      val is = new InputSource(new StringReader(readRdf(file)))
      Option(XmlBaseExtractor.getXMLBase(is)) foreach { xmlBase =>
        map = map.updated("xmlBase", xmlBase.toString)
      }
    }
    catch {
      case e: Throwable => {
        val error: String = "error while trying to read xml:base attribute: " + e.getMessage
        logger.warn(error, e)
      }
    }
    map
  }

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
