package org.mmisw.orr.ont.swld

import com.hp.hpl.jena.vocabulary.{OWL, RDF}
import org.specs2.mutable.Specification
import org.json4s._
import org.json4s.native.JsonMethods._


class v2rSpec extends Specification {
  implicit val formats = DefaultFormats

  val vr1 = V2RModel(
    namespace = None,
    vocabs = List(
      Vocab(
        `class` = IdL(name = Some("Parameter")),
        properties = List(
          IdL(name = Some("definition")),
          IdL(uri = Some("http://some/prop"))
        ),
        terms = List(
          Term(name = Some("pressure"),
               attributes = List("Definition of pressure", "value of some/prop")
          )
        )
      )
    )
  )

  val jsonInput =
    """
      |{
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
      |        {
      |          "name": "pressure",
      |          "attributes": ["Definition of pressure", "value of some/prop"]
      |        }
      |      ]
      |    }
      |  ]
      |}
    """.stripMargin

  val json  = parse(jsonInput)

  """v2r""" should {
    """create expected model with given namespace""" in {
      val ns1 = "http://ns/"

      val model = v2r.getModel(vr1, Some(ns1))

      //ontUtil.writeModel(ns1, model, "n3",     new java.io.File("/tmp/vr1.n3"))
      //ontUtil.writeModel(ns1, model, "jsonld", new java.io.File("/tmp/vr1.jsonld"))
      //ontUtil.writeModel(ns1, model, "rj",     new java.io.File("/tmp/vr1.rj"))

      val Parameter  = model.createResource(ns1 + "Parameter")
      val pressure   = model.createResource(ns1 + "pressure")
      val definition = model.createProperty(ns1 + "definition")
      val someProp   = model.createProperty("http://some/prop")

      model.contains(Parameter,  RDF.`type`, OWL.Class)            === true
      model.contains(definition, RDF.`type`, OWL.DatatypeProperty) === true

      ontUtil.getValue(pressure, definition) === Some("Definition of pressure")
      ontUtil.getValue(pressure, someProp)   === Some("value of some/prop")
    }

    """create expected model (no namespace)""" in {
      val model = v2r.getModel(vr1)

      //ontUtil.writeModel(null, model, "n3", new java.io.File("/tmp/vr1_no_ns.n3"))

      val Parameter  = model.createResource("Parameter")
      val pressure   = model.createResource("pressure")
      val definition = model.createProperty("definition")
      val someProp   = model.createProperty("http://some/prop")

      model.contains(Parameter,  RDF.`type`, OWL.Class)            === true
      model.contains(definition, RDF.`type`, OWL.DatatypeProperty) === true

      ontUtil.getValue(pressure, definition) === Some("Definition of pressure")
      ontUtil.getValue(pressure, someProp)   === Some("value of some/prop")
    }

    """obtain expected model by parsing direct json input""" in {
      val v2r = json.extract[V2RModel]
      v2r === vr1
    }

    """serialize model into json format with not diff with direct json input""" in {
      //println(s"\n\nvr1JsonString=${vr1.toPrettyJson}\n\n")
      val vr1Json = parse(vr1.toJson)
      Diff(JNothing, JNothing, JNothing) === json.diff(vr1Json)
    }
  }
}
