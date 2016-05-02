package org.mmisw.orr.ont.swld

import com.hp.hpl.jena.ontology.{OntDocumentManager, OntModel, OntModelSpec, Ontology}
import com.hp.hpl.jena.rdf.model._
import java.io.{File, FileInputStream, FileOutputStream}

import com.typesafe.scalalogging.{StrictLogging => Logging}
import com.github.jsonldjava.jena.JenaJSONLD
import org.mmisw.orr.ont.vocabulary.{Omv, OmvMmi}

import scala.collection.JavaConversions._


object ontUtil extends AnyRef with Logging {

  JenaJSONLD.init()

  // todo review
  val mimeMappings: Map[String, String] = Map(
      "rdf"     -> "application/rdf+xml"    // https://www.w3.org/TR/REC-rdf-syntax/
    , "owl"     -> "application/rdf+xml"    // https://www.w3.org/TR/REC-rdf-syntax/
    , "owx"     -> "application/owl+xml"    // https://www.w3.org/TR/owl-xml-serialization/
    , "v2r"     -> "application/v2r+json"
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
        logger.info(s"ontUtil.convert: path=$fromFile toLang=$toLang")
        val model = readModel2(uri, fromFile, fromLang)
        val writer = model.getWriter(toLang)
        val os = new FileOutputStream(toFile)
        try writer.write(model, os, uri)
        finally os.close()
        toFile
      }
    }

    for {fromLang <- format2lang(storedFormat(fromFormat))
         toLang   <- format2lang(storedFormat(toFormat))
    } yield doIt(fromLang, toLang)
  }

  // TODO review along with mapping in storedFormat method
  val storedFormats = List("rdf", "n3", "owx", "jsonld", "v2r")

  // for the files actually stored
  def storedFormat(format: String) = format.toLowerCase match {
    case "owx"              => "owx"    // https://www.w3.org/TR/owl-xml-serialization/
    case "v2r"              => "v2r"
    case "owl"  | "rdf"     => "rdf"
    case "json" | "jsonld"  => "jsonld"
    case "ttl"  | "n3"      => "n3"
    case f => f   // TODO explicitly include other formats we are providing, "rj",
  }

  def getPropsFromOntMetadata(uri: String,
                              file: File,
                              format: String
                             ): Map[String,List[String]] = {

    logger.debug(s"getPropsFromOntMetadata: uri=$uri file=$file format=$format")
    val ontModel = loadOntModel(uri, file, format)
    Option(ontModel.getOntology(uri)) match {
      case Some(ontology) =>
        try extractAttributes(ontology)
        catch {
          case ex: Throwable =>
            ex.printStackTrace()
            Map.empty
        }

      case _ => Map.empty
    }
  }

  // resourceType not always captured as a resource but as a literal having
  // the form of a URI; this ad hoc method extracts the suffix after separator.
  def simplifyResourceType(resourceType: String): String = {
    val idx = resourceType.lastIndexOf('/')
    if (idx >= 0 && idx < resourceType.length - 1) resourceType.substring(idx + 1)
    else resourceType
  }

  def getValues(sub: Resource, pro: Property): List[String] = {
    var list = List[String]()
    val it = sub.listProperties(pro)
    if (it != null) while (it.hasNext) {
      val stmt = it.next()
      val nodeString = nodeAsString(stmt.getObject)
      list = nodeString :: list
    }
    list
  }

  def getValue(sub: Resource, pro: Property): Option[String] = {
    for {
      sta <- Option(sub.getProperty(pro))
      node: RDFNode = sta.getObject
    } yield getValueAsString(node)
  }

  def getValueAsString(node: RDFNode): String = node match {
    case lit: Literal  => lit.getLexicalForm
    case res: Resource => res.getURI
  }

  def extractAttributes(resource: Resource): Map[String,List[String]] = {
    var map = Map[String,List[String]]()

    val it = resource.listProperties()
    if ( it != null) {
      while (it.hasNext) {
        val stmt = it.next()
        val prop: Property = stmt.getPredicate
        val propUri = prop.getURI
        val node: RDFNode = stmt.getObject
        val nodeString = nodeAsString(node)

        val newValues = nodeString :: (map.get(propUri) match {
          case None         => List()
          case Some(values) => values
        })

        map = map.updated(propUri, newValues)
      }
    }
    map
  }

  def toOntMdList(md: Map[String,List[String]]): List[Map[String, AnyRef]] =
    md.map(uv => Map("uri" -> uv._1, "values" -> uv._2)).toList

  private def nodeAsString(n: RDFNode): String =
    if (n.isResource) n.asResource().getURI
    else n.asLiteral().getString

  /**
   * ad hoc initial mechanism to report some of the ontology metadata.
   */
  def extractSomeProps(md: Map[String,List[String]]): Map[String,String] = {

    var map = Map[String, String]()

    val resourceTypeListOpt = md.get(OmvMmi.hasResourceType.getURI)
    if (resourceTypeListOpt.isDefined) {
      map = map.updated("resourceType", resourceTypeListOpt.get.head)
    }

    val ontologyTypeListOpt = md.get(Omv.usedOntologyEngineeringTool.getURI)
    if (ontologyTypeListOpt.isDefined) {
      val usedOntologyEngineeringTool = ontologyTypeListOpt.head.head
      val ontologyType = if (usedOntologyEngineeringTool == OmvMmi.voc2rdf.getURI)
        "vocabulary"
      else if (usedOntologyEngineeringTool == OmvMmi.vine.getURI)
        "mapping"
      else ""
      if (ontologyType.length > 0)
        map = map.updated("ontologyType", ontologyType)
    }

    map
  }

  private def listPropertyValues(ont: Ontology, prop: Property): List[String] =
    listPropertyValueNodes(ont, prop) map nodeAsString

  private def listPropertyValueNodes(ontology: Ontology, prop: Property): List[RDFNode] = {
    val values = collection.mutable.ListBuffer[RDFNode]()
    val it = ontology.listPropertyValues(prop)
    if ( it != null) {
      while (it.hasNext) {
        values += it.next()
      }
    }
    values.toList
  }

  /** Loads an ontology model from a file. */
  def loadOntModel(uri: String, file: File, format: String): OntModel = {
    val lang = format2lang(storedFormat(format)).getOrElse(throw new IllegalArgumentException)
    logger.debug(s"Loading uri='$uri' file=$file lang=$lang")
    readModel2(uri, file, lang)
  }

  private def readModel2(uri: String, file: File, lang: String): OntModel = {
    val path = file.getAbsolutePath
    logger.debug(s"readModel: path='$path' lang='$lang'")
    if ("OWX" == lang) {
      owlApiHelper.loadOntModel(file).ontModel
    }
    else if ("V2R" == lang) {
      v2r.loadOntModel(file, Some(uri)).ontModel
    }
    else {
      val ontModel = createDefaultOntModel
      ontModel.setDynamicImports(false)
      ontModel.getDocumentManager.setProcessImports(false)
      readModel(uri, file, lang, ontModel)
      ontModel
    }
  }

  private def readModel(uri: String, file: File, lang: String, model: Model): Unit = {
    val path = file.getAbsolutePath
    logger.debug(s"readModel: path='$path' lang='$lang'")
    val is = new FileInputStream(file)
    try model.read(is, uri, lang)
    finally is.close()
  }

  def createDefaultOntModel: OntModel = {
    val spec: OntModelSpec = new OntModelSpec(OntModelSpec.OWL_MEM)
    spec.setDocumentManager(new OntDocumentManager)
    ModelFactory.createOntologyModel(spec, null)
  }

  // https://jena.apache.org/documentation/io/
  def format2lang(format: String) = Option(format.toLowerCase match {
    case "owx"          => "OWX"      // to use OWL API
    case "v2r"          => "V2R"      // see v2r module
    case "owl" | "rdf"  => "RDF/XML"
    case "jsonld"       => "JSON-LD"
    case "n3"           => "N3"
    case "ttl"          => "Turtle"
    case "nt"           => "N-Triples"
    case "nq"           => "N-Quads"
    case "trig"         => "TriG"
    case "rj"           => "RDF/JSON"
    case _              => null
  })

  def removeUnusedNsPrefixes(model: Model): Unit = {
    val usedPrefixes = scala.collection.mutable.HashSet.empty[String]
    val ns: NsIterator = model.listNameSpaces
    while (ns.hasNext) {
        val namespace: String = ns.nextNs
        val prefix = model.getNsURIPrefix(namespace)
        if (prefix != null) {
          usedPrefixes.add(prefix)
        }
    }
    val pm = model.getNsPrefixMap
    for (prefix <- pm.keySet) {
      if (!usedPrefixes.contains(prefix)) {
        model.removeNsPrefix(prefix)
      }
    }
  }

  def writeModel(base: String, model: Model, format: String, toFile: File): Unit = {
    val writer = model.getWriter(format2lang(format).get)

    // set these props and let jena decide which will actually apply depending on the format
    writer.setProperty("xmlbase", base)
    writer.setProperty("showXmlDeclaration", "true")
    writer.setProperty("relativeURIs", "same-document")

    val os = new FileOutputStream(toFile)
    try writer.write(model, os, base)
    finally os.close()

  }
  /**
    * Modifies all statements having its subject, predicate or object in the given old namespace
    * or equal to that old namespace, so those components get "transferred" to the given new namespace.
    *
    * Any trailing separators ('#' or '/') in oldNamespace are ignored.
    *
    * Except for exact matches with old namespace, the separator in the new namespace is always '/'.
    *
    * Based on UnversionedConverter._replaceNameSpace in old Ont, but with some modifications.
    *
    */
  def replaceNamespace(model: OntModel, oldNameSpace: String, newNameSpace: String): Unit = {
    require(!newNameSpace.endsWith("/"))  // we add the "/" separator here
    logger.debug(s"replaceNamespace: moving terms from $oldNameSpace to $newNameSpace")

    def inOldNamespace(r: Resource) = {
      val ns = r.getNameSpace
      ns != null && oldNameSpace == ns.replaceAll("(#|/)+$", "")
    }

    val subjectsChanged   = scala.collection.mutable.HashSet.empty[String]
    val predicatesChanged = scala.collection.mutable.HashSet.empty[String]
    val objectsChanged    = scala.collection.mutable.HashSet.empty[String]

    val oldStatements = scala.collection.mutable.ArrayBuffer.empty[Statement]
    val newStatements = scala.collection.mutable.ArrayBuffer.empty[Statement]

    val existingStatements: StmtIterator = model.listStatements
    while (existingStatements.hasNext) {
      val statement = existingStatements.nextStatement

      logger.debug(s"statement=$statement")

      val (sbj, prd, obj) = (statement.getSubject, statement.getPredicate, statement.getObject)

      var any_change = false

      var (n_sbj, n_prd, n_obj) = (sbj, prd, obj)

      if (oldNameSpace == sbj.getURI) {
        // URI of the registration itself (presumably an ontology resource)
        n_sbj = model.createResource(newNameSpace)
        subjectsChanged += sbj.getURI
        any_change = true
      }
      else if (inOldNamespace(sbj)) {
        n_sbj = model.createResource(newNameSpace + "/" + sbj.getLocalName)
        subjectsChanged += sbj.getURI
        any_change = true
      }

      if (inOldNamespace(prd)) {
        n_prd = model.createProperty(newNameSpace + "/" + prd.getLocalName)
        predicatesChanged += prd.getURI
        any_change = true
      }

      obj match {
        case r: Resource =>
          if (oldNameSpace == r.getURI) {
            n_obj = model.createResource(newNameSpace)
            objectsChanged += r.getURI
            any_change = true
          }
          else if (inOldNamespace(r)) {
            n_obj = model.createResource(newNameSpace + "/" + r.getLocalName)
            objectsChanged += r.getURI
            any_change = true
          }
        case l: Literal =>
          if (oldNameSpace == l.getLexicalForm) {
            n_obj = model.createLiteral(newNameSpace)
            objectsChanged += oldNameSpace
            any_change = true
          }
      }

      if (any_change) {
        oldStatements.add(statement)
        val newStatement = model.createStatement(n_sbj, n_prd, n_obj)
        newStatements.add(newStatement)
      }
    }

    oldStatements foreach model.remove
    newStatements foreach model.add

    removeUnusedNsPrefixes(model)
    model.setNsPrefix("", newNameSpace + "/")

    if (logger.underlying.isDebugEnabled) {
      logger.debug(s"replaceNamespace: statements affected=${newStatements.size} " +
        s"subjectsChanged=${subjectsChanged.size} " +
        s"predicatesChanged=${predicatesChanged.size}, objectsChanged=${objectsChanged.size}")

      logger.debug("RESULTING MODEL:")
      logger.debug(s" prefixMap=${model.getNsPrefixMap}")
      logger.debug(s" statements:")
      val existingStatements: StmtIterator = model.listStatements
      while (existingStatements.hasNext) {
        logger.debug(s"   ${existingStatements.nextStatement}")
      }
    }
  }
}
