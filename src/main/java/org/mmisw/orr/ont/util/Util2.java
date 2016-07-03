package org.mmisw.orr.ont.util;

import com.hp.hpl.jena.ontology.OntDocumentManager;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntModelSpec;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.shared.JenaException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.xml.sax.SAXParseException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Elements from org.mmisw.orrclient.core.util.Util2 in old mmiorr
 */
public class Util2 {

  public static final List<String> JENA_LANGS = Collections.unmodifiableList(Arrays.asList(
      "RDF/XML",
      "N3",
      "N-TRIPLE",
      "N-TRIPLES",
      "JSON-LD",
      "RDF/JSON",
      "TURTLE"
  ));

  /**
   * Reads an ontology from a file.
   */
  public static OntModel loadOntModel(File file, String lang) throws IOException {

    if ( lang != null && ! JENA_LANGS.contains(lang.toUpperCase()) ) {
      throw new IllegalArgumentException("lang argument must be null or one of " +JENA_LANGS);
    }

    try {
      return _loadModel(file, lang, false);
    }
    catch ( Throwable jenaExc ) {
      // XML parse exception?
      String errorMessage = getXmlParseExceptionErrorMessage(jenaExc);
      if ( errorMessage != null ) {
        throw new IOException(errorMessage, jenaExc);
      }

      // other kind of problem:
      String error = jenaExc.getClass().getName()+ " : " +jenaExc.getMessage();
      throw new IOException(error, jenaExc);
    }
  }

  private static OntModel _loadModel(File file, String lang, boolean processImports)
  throws IOException {
    OntModel model = _createDefaultOntModel();
    model.setDynamicImports(false);
    model.getDocumentManager().setProcessImports(processImports);
    if ( log.isDebugEnabled() ) {
      log.debug("loading model " + file + "  LANG=" +lang);
    }
    FileInputStream is = new FileInputStream(file);
    model.read(is, null, lang);
    is.close();
    return model;
  }

  private static OntModel _createDefaultOntModel() {
    OntModelSpec spec = new OntModelSpec(OntModelSpec.OWL_MEM);
    OntDocumentManager docMang = new OntDocumentManager();
    spec.setDocumentManager(docMang);
    return ModelFactory.createOntologyModel(spec, null);
  }

  /**
   * Helper to determine it's a SAXParseException so we can provide a bit of more information.
   */
  private static String getXmlParseExceptionErrorMessage(Throwable jenaExc) {
    if ( ! (jenaExc instanceof JenaException) ) {
      return null;
    }

    Throwable cause = jenaExc.getCause();
    if ( ! (cause instanceof SAXParseException) ) {
      return null;
    }

    SAXParseException spe = (SAXParseException) cause;
    return spe.getMessage() +
        "\n  Line number: " + spe.getLineNumber()+" Column number: " +spe.getColumnNumber()
			  //+(spe.getSystemId() != null ? "\n System ID: " + spe.getSystemId() : "" )
			  //+(spe.getPublicId() != null ? "\n Public ID: " + spe.getPublicId() : "" )
        ;
  }

  private static final Log log = LogFactory.getLog(Util2.class);
}
