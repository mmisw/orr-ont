package org.mmisw.orr.ont.swld

import com.hp.hpl.jena.vocabulary.{OWL, RDF}
import org.specs2.mutable.Specification
import org.json4s._
import org.json4s.native.JsonMethods._


class v2rSpec extends Specification {
  implicit val formats = DefaultFormats

  val vr1 = V2RModel(
    uri = None,
    metadata = None,
    vocabs = List(
      Vocab(
        `class` = IdL(name = Some("Parameter")),
        properties = List(
          IdL(name = Some("definition")),
          IdL(uri = Some("http://some/prop"))
        ),
        terms = List(
          Term(name = Some("pressure"),
               attributes = List(
                 JString("Definition of pressure"),
                 JArray(List(
                  JString("one value for some/prop"),
                  JString("other value for some/prop")
                 ))
               )
          )
        )
      )
    )
  )

  val json = parse(new java.io.File("src/test/resources/vr1.v2r"))

  """v2r""" should {
    """create expected model with given URI""" in {
      val uri1 = "http://v2r-vocab"

      val model = v2r.getModel(vr1, Some(uri1))

      ontUtil.writeModel(uri1+"/", model, "n3",     new java.io.File("/tmp/vr1.n3"))
      //ontUtil.writeModel(uri1+"/", model, "jsonld", new java.io.File("/tmp/vr1.jsonld"))
      //ontUtil.writeModel(uri1+"/", model, "rj",     new java.io.File("/tmp/vr1.rj"))

      val Parameter  = model.createResource(uri1 + "/Parameter")
      val pressure   = model.createResource(uri1 + "/pressure")
      val definition = model.createProperty(uri1 + "/definition")
      val someProp   = model.createProperty("http://some/prop")

      model.contains(Parameter,  RDF.`type`, OWL.Class)            === true
      model.contains(definition, RDF.`type`, OWL.DatatypeProperty) === true

      ontUtil.getValues(pressure, definition).toSet === Set("Definition of pressure")
      ontUtil.getValues(pressure, someProp).toSet   === Set("one value for some/prop", "other value for some/prop")
    }

    """create expected model (no URI)""" in {
      val model = v2r.getModel(vr1)

      //ontUtil.writeModel(null, model, "n3", new java.io.File("/tmp/vr1_no_uri.n3"))

      val Parameter  = model.createResource("Parameter")
      val pressure   = model.createResource("pressure")
      val definition = model.createProperty("definition")
      val someProp   = model.createProperty("http://some/prop")

      model.contains(Parameter,  RDF.`type`, OWL.Class)            === true
      model.contains(definition, RDF.`type`, OWL.DatatypeProperty) === true

      ontUtil.getValues(pressure, definition).toSet === Set("Definition of pressure")
      ontUtil.getValues(pressure, someProp).toSet   === Set("one value for some/prop", "other value for some/prop")
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
