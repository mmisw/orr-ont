package org.mmisw.orr.ont.swld

import com.hp.hpl.jena.rdf.model.Property
import com.hp.hpl.jena.vocabulary.{OWL, RDF}
import org.json4s._
import org.json4s.native.JsonMethods._
import org.mmisw.orr.ont.vocabulary.{Omv, Skos}
import org.specs2.mutable.Specification


class m2rSpec extends Specification {

  implicit val formats = DefaultFormats

  val mr1 = M2RModel(
    uri = None,
    metadata = Some(Map(
      Omv.name.getURI -> JString("An ORR mapping ontology")
    )),
    mappedOnts = List("http://mappedOnt1", "http://mappedOnt2"),
    mappings = List(
      M2RMapGroup(
        subjects = List("http://s1", "http://s2"),
        predicate = Skos.closeMatch.getURI,
        objects = List("http://o1", "http://o2")
      ),
      M2RMapGroup(
        subjects = List("http://s3"),
        predicate = Skos.relatedMatch.getURI,
        objects = List("http://o3")
      ),
      M2RMapGroup(
        subjects = List("http://s4"),
        predicate = OWL.sameAs.getURI,
        objects = List("http://o4")
      )
    )
  )

  println(s"mr1 json:\n${mr1.toPrettyJson}")

  val json = parse(new java.io.File("src/test/resources/mr1.m2r"))

  """m2r""" should {
    """create expected model""" in {
      val uri1 = "http://m2r-vocab"

      val model = m2r.getModel(mr1, Some(uri1))

      ontUtil.writeModel(uri1+"/", model, "n3",     new java.io.File("/tmp/mr1.n3"))
      //ontUtil.writeModel(uri1+"/", model, "jsonld", new java.io.File("/tmp/mr1.jsonld"))
      //ontUtil.writeModel(uri1+"/", model, "rj",     new java.io.File("/tmp/mr1.rj"))

      val uri1Ont = model.createResource(uri1)

      // imports:
      val mappedOnt1 = model.createResource("http://mappedOnt1")
      val mappedOnt2 = model.createResource("http://mappedOnt2")
      model.contains(uri1Ont, OWL.imports, mappedOnt1) === true
      model.contains(uri1Ont, OWL.imports, mappedOnt2) === true

      // metadata:
      model.contains(uri1Ont, RDF.`type`, OWL.Ontology) === true
      ontUtil.getValues(uri1Ont, Omv.name).toSet === Set("An ORR mapping ontology")

      // contents:
      def verifyTriple(predicateProperty: Property)(subjectUri: String, objectUri: String) = {
        val subjectResource   = model.createResource(subjectUri)
        val objectResource    = model.createResource(objectUri)
        model.contains(subjectResource, predicateProperty, objectResource) === true
      }

      val verifyCloseMatch = verifyTriple(Skos.closeMatch)_
      verifyCloseMatch("http://s1", "http://o1")
      verifyCloseMatch("http://s1", "http://o2")
      verifyCloseMatch("http://s2", "http://o1")
      verifyCloseMatch("http://s2", "http://o2")

      val verifyRelatedMatch = verifyTriple(Skos.relatedMatch)_
      verifyRelatedMatch("http://s3", "http://o3")

      val verifySameAs = verifyTriple(OWL.sameAs)_
      verifySameAs("http://s4", "http://o4")
    }

    """obtain expected model by parsing direct json input""" in {
      val m2r = json.extract[M2RModel]
      m2r === mr1
    }

    """serialize model into json format with not diff with direct json input""" in {
      //println(s"\n\nmr1JsonString=${mr1.toPrettyJson}\n\n")
      val mr1Json = parse(mr1.toJson)
      Diff(JNothing, JNothing, JNothing) === json.diff(mr1Json)
    }
  }
}
