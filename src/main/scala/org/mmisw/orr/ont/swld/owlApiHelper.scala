package org.mmisw.orr.ont.swld

import java.io.{IOException, FileOutputStream, File}

import com.typesafe.scalalogging.{StrictLogging => Logging}
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.io.RDFXMLOntologyFormat
import org.semanticweb.owlapi.model.{OWLOntology, OWLOntologyManager}

/**
  * Converted from org.mmisw.orrclient.core.owl.OwlApiHelper in old mmiorr
  */
object owlApiHelper extends AnyRef with Logging {

  /**
    * Reads a model from a text file that can be parsed by the OWL API library.
    * Internally it loads the file and then saves it in RDF/XML to then use
    * Jena to load this converted version.
    */
  def loadOntModel(file: File): OntModelLoadedResult = {
    //Utf8Util.verifyUtf8(file)
    logger.debug("owlApiHelper.loadOntModel: loading file=" + file)

    val m: OWLOntologyManager = OWLManager.createOWLOntologyManager
    val o: OWLOntology = m.loadOntologyFromOntologyDocument(file)

    val rdfFilename = file.getName.replaceAll("\\.owl$", "\\.rdf")
    val rdfFile = new File(file.getParent, rdfFilename)
    logger.debug("owlApiHelper.loadOntModel: saving RDF/XML in =" + rdfFile)
    try {
      val fos = new FileOutputStream(rdfFile)
      try {
        m.saveOntology(o, new RDFXMLOntologyFormat, fos)
      }
      finally {
        fos.close()
      }

      val uriFile = rdfFile.getCanonicalFile.toURI.toString
      logger.debug("owlApiHelper.loadOntModel: now loading using Jena uriFile=" + uriFile)
      OntModelLoadedResult(rdfFile, "rdf", ontUtil.loadOntModel(uriFile, rdfFile, "rdf"))
    }
    catch {
      case ex: Throwable => {
        val error: String = ex.getClass.getName + " : " + ex.getMessage
        logger.error(error, ex)
        throw new IOException(error)
      }
    }
  }
}
