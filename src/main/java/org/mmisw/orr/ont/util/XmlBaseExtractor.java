package org.mmisw.orr.ont.util;

/**
 * Created by carueda on 4/12/16.
 */

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.xerces.parsers.SAXParser;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Extracts the xml:base URI from an input stream.
 *
 * <p>
 * Code adapted from
 * <a href="http://smi-protege.stanford.edu/svn/owl/trunk/src/edu/stanford/smi/protegex/owl/repository/util/XMLBaseExtractor.java?rev=10126&sortby=rev&view=log"
 * >here</a>.
 *
 * <p>
 * User: matthewhorridge<br>
 * The University Of Manchester<br>
 * Medical Informatics Group<br>
 * Date: Sep 21, 2005<br>
 * <br>
 * <p/> matthew.horridge@cs.man.ac.uk<br>
 * www.cs.man.ac.uk/~horridgm<br>
 * <br>
 */
public class XmlBaseExtractor {

  private final Log log = LogFactory.getLog(XmlBaseExtractor.class);

  private InputSource is;

  private URI xmlBase;

  private String rootElementName;

  private String defaultNamespace;

  public XmlBaseExtractor(InputSource is) {
    this.is = is;
    this.xmlBase = null;
  }

  public static URI getXMLBase(InputSource is) throws Exception {
    XmlBaseExtractor xmlBaseExtractor = new XmlBaseExtractor(is);
    return xmlBaseExtractor.getXMLBase();
  }

  public URI getXMLBase() throws Exception {
    SAXParser parser = new SAXParser();
    parser.setContentHandler(new MyHandler());
    parser.parse(is);
    return xmlBase;
  }

  public String getRootElementName() {
    return rootElementName;
  }

  public String getDefaultNamespace() {
    return defaultNamespace;
  }

  private class MyHandler extends DefaultHandler {

    private boolean startElement;

    public void startPrefixMapping(String prefix, String uri)
        throws SAXException {

      if (prefix == null || prefix.equals("")) {
        defaultNamespace = uri;
      }

    }

    public void startElement(String namespaceURI, String localName,
                             String qName, Attributes atts) throws SAXException {
      if (startElement == false) {
        rootElementName = qName;
        for (int i = 0; i < atts.getLength(); i++) {
          if (atts.getQName(i).equals("xml:base")) {
            URI attURL = null;
            try {
              attURL = new URI(atts.getValue(i));
            }
            catch (URISyntaxException e) {
              log.error("Exception caught", e);
            }
            xmlBase = attURL;
          }
        }
        startElement = true;
      }

// why this in the orginal code? (carueda)
//			else {
//				throw new SAXException("No xml:base");
//			}
    }

  }
}
