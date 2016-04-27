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
    */
  def loadOntModel(file: File): OntModelLoadedResult = {
    //Utf8Util.verifyUtf8(file)
    logger.debug("owlApiHelper.loadOntModel: loading file=" + file)

    val m: OWLOntologyManager = OWLManager.createOWLOntologyManager
    val o: OWLOntology = m.loadOntologyFromOntologyDocument(file)

    // create corresponding conversion to RDF/XML and load that file with
    // jena to return the expected OntModel

    val rdfFilename = file.getName.replaceAll("\\.[^.]*$", "\\.rdf")
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
      val ontModel = ontUtil.loadOntModel(uriFile, rdfFile, "rdf")

      // note: we report the original file and "owx" format
      OntModelLoadedResult(file, "owx", ontModel)
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
