package org.mmisw.orr.ont.swld

import com.hp.hpl.jena.rdf.model.ModelFactory
import java.io.{FileWriter, File}
import com.typesafe.scalalogging.slf4j.Logging

import com.github.jsonldjava.jena.JenaJSONLD


object ontUtil extends AnyRef with Logging {

  JenaJSONLD.init()

  // todo review
  val mimeMappings: Map[String, String] = Map(
      "rdf"     -> "application/rdf+xml"
    , "owl"     -> "application/rdf+xml"
    , "jsonld"  -> "application/json+ld"    // http://www.ietf.org/rfc/rfc6839.txt
    , "n3"      -> "text/n3"                // http://www.w3.org/TeamSubmission/n3/
    , "ttl"     -> "text/turtle"            // http://www.w3.org/TeamSubmission/turtle/
    , "nt"      -> "text/plain"             // http://www.w3.org/TR/2004/REC-rdf-testcases-20040210/
    , "nq"      -> "application/n-quads"    // http://www.w3.org/TR/2013/NOTE-n-quads-20130409/
    , "trig"    -> "application/trig"       // http://www.w3.org/TR/2013/WD-trig-20130409/
    , "rj"      -> "application/rdf+json"   // https://dvcs.w3.org/hg/rdf/raw-file/default/rdf-json/index.html
  )

  // preliminary
  def convert(uri: String, fromFile: File, fromFormat: String, toFile: File, toFormat: String) : Option[File] = {

    def doIt(fromLang: String, toLang: String): File = {
      if (fromLang == toLang) fromFile
      else {
        // TODO manage resources below
        val model = ModelFactory.createDefaultModel()
        val fromPath = fromFile.toURI.toURL.toString
        model.read(fromPath, fromLang)
        val writer = model.getWriter(toLang)
        logger.info(s"jenaUtil.convert: path=$fromPath")
        val toWriter = new FileWriter(toFile)
        writer.write(model, toWriter, uri)
        toFile
      }
    }

    for {fromLang <- format2lang(storedFormat(fromFormat))
         toLang   <- format2lang(storedFormat(toFormat))
    } yield doIt(fromLang, toLang)
  }

  // for the files actually stored, for example file.rdf serves both the rdf and the owl formats
  def storedFormat(format: String) = format.toLowerCase match {
    case "owl"  | "rdf"     => "rdf"
    case "json" | "jsonld"  => "jsonld"
    case "ttl"  | "n3"      => "n3"
    case f => f
  }

  // https://jena.apache.org/documentation/io/
  private def format2lang(format: String) = Option(format.toLowerCase match {
    case "rdf"          => "RDF/XML"
    case "jsonld"       => "JSON-LD"
    case "n3"           => "N3"
    case "ttl"          => "Turtle"
    case "nt"           => "N-Triples"
    case "nq"           => "N-Quads"
    case "trig"         => "TriG"
    case "rj"           => "RDF/JSON"
    case _              => null
  })
}
