package org.mmisw.orr.ont.swld

import com.hp.hpl.jena.vocabulary.{OWL, RDF}
import org.json4s._
import org.json4s.native.JsonMethods._
import org.mmisw.orr.ont.vocabulary.Omv
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
        predicate = "http://example/relation",
        objects = List("http://o1", "http://o2")
      )
    )
  )

  //println(s"mr1 json:\n${mr1.toPrettyJson}")

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
      val s1Resource  = model.createResource("http://s1")
      val s2Resource  = model.createResource("http://s2")
      val o1Resource  = model.createResource("http://o1")
      val o2Resource  = model.createResource("http://o2")
      val relationProp = model.createProperty("http://example/relation")

      model.contains(s1Resource, relationProp, o1Resource)  === true
      model.contains(s1Resource, relationProp, o2Resource)  === true
      model.contains(s2Resource, relationProp, o1Resource)  === true
      model.contains(s2Resource, relationProp, o2Resource)  === true
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
