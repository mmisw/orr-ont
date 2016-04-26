package org.mmisw.orr.ont.swld

import com.hp.hpl.jena.vocabulary.{OWL, RDF}
import org.specs2.mutable.Specification
import org.json4s._
import org.json4s.native.JsonMethods._


class v2rSpec extends Specification {
  implicit val formats = DefaultFormats

  val v2r1 = Voc2Rdf(
    namespace = "http://ns/",
    vocabs = List(
      Vocab(
        `class` = Element(name = Some("Parameter")),
        properties = List(
          Element(name = Some("definition")),
          Element(uri = Some("http://some/prop"))
        ),
        terms = List(
          List("pressure",
            "Definition of pressure", "value of some/prop")
        )
      )
    )
  )

  val jsonInput =
    """
      |{
      |  "namespace": "http://ns/",
      |  "vocabs": [
      |    {
      |      "class": {
      |        "name": "Parameter"
      |      },
      |      "properties": [
      |         {
      |           "name": "definition"
      |         },
      |         {
      |           "uri": "http://some/prop"
      |         }
      |      ],
      |      "terms": [
      |        ["pressure", "Definition of pressure", "value of some/prop"]
      |      ]
      |    }
      |  ]
      |}
    """.stripMargin

  val json  = parse(jsonInput)

  """v2r""" should {
    """parse json""" in {
      val v2r = json.extract[Voc2Rdf]
      v2r === v2r1
    }

    """create expected model""" in {
      val model = v2r1.getModel

      ontUtil.writeModel(v2r1.namespace, model, "n3", new java.io.File("/tmp/v2r1.n3"))
      ontUtil.writeModel(v2r1.namespace, model, "jsonld", new java.io.File("/tmp/v2r1.jsonld"))
      ontUtil.writeModel(v2r1.namespace, model, "rj", new java.io.File("/tmp/v2r1.rj"))

      val Parameter  = model.createResource("http://ns/Parameter")
      val pressure   = model.createResource("http://ns/pressure")
      val definition = model.createProperty("http://ns/definition")
      val someProp   = model.createProperty("http://some/prop")

      model.contains(Parameter,  RDF.`type`, OWL.Class)            must_== true
      model.contains(definition, RDF.`type`, OWL.DatatypeProperty) must_== true

      ontUtil.getValue(pressure, definition) === Some("Definition of pressure")
      ontUtil.getValue(pressure, someProp)   === Some("value of some/prop")
    }

    """serialize into "v2r" json format""" in {
      val v2r1Json = parse(v2r1.toJson)
      Diff(JNothing, JNothing, JNothing) === json.diff(v2r1Json)
    }
  }
}
