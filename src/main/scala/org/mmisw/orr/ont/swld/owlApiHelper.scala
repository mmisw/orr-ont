package org.mmisw.orr.ont.swld

import java.io.{File, FileOutputStream, IOException}

import com.typesafe.scalalogging.{StrictLogging => Logging}
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.io.{FileDocumentSource, RDFXMLOntologyFormat}
import org.semanticweb.owlapi.model.{MissingImportHandlingStrategy, OWLOntology, OWLOntologyLoaderConfiguration, OWLOntologyManager}

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
    val conf = new OWLOntologyLoaderConfiguration
    conf.setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT)
    val docSource = new FileDocumentSource(file)
    // Note: also calling the following deprecated method because the above mechanism
    // doesn't seem to be honored with currently used version of the OWL API:
    // TODO remove this call with some newer version of the OWL API
    m.setSilentMissingImportsHandling(true)
    val o: OWLOntology = m.loadOntologyFromOntologyDocument(docSource, conf)

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
