package org.mmisw.orr.ont

import com.mongodb.casbah.Imports._


class OntServlet(implicit setup: Setup) extends OrrOntStack with SimpleMongoDbJsonConversion {

  val ontologies = setup.db.ontologiesColl

  def dupUriError(uri: String) = {
    MongoDBObject("error" -> s"'$uri' already in collection")
  }

  get("/") {
    ontologies.find()
  }

  post("/") {
    val json = parse(request.body)
    val map = json.extract[Map[String, String]]

    val uri:   String = map.getOrElse("uri",  halt(400, "uri"))
    val name:  String = map.getOrElse("name", halt(400, "name"))

    val q = MongoDBObject("uri" -> uri)
    ontologies.findOne(q) match {
      case None =>
        val obj = MongoDBObject("uri" -> uri, "name" -> name)
        ontologies += obj
        Ontology(uri, name)

      case Some(_) => halt(400, dupUriError(uri))
    }
  }

  put("/") {
    val json = parse(request.body)
    val map = json.extract[Map[String, String]]

    val uri:   String = map.getOrElse("uri",  halt(400, "uri"))
    val name:  String = map.getOrElse("name", halt(400, "name"))

    val obj = MongoDBObject("uri" -> uri)
    ontologies.findOne(obj) match {
      case None =>
        halt(404, s"'$uri' does not exist in collection")

      case Some(found) =>
        val update = $set("name" -> name)
        val result = ontologies.update(obj, update)
        Result(uri, s"updated (${result.getN})")
    }
  }

  delete("/") {
    val uri: String = params.getOrElse("uri", halt(400, "uri"))

    val obj = MongoDBObject("uri" -> uri)
    val result = ontologies.remove(obj)
    Result(uri, s"removed (${result.getN})")
  }

  post("/!/deleteAll") {
    val json = parse(request.body)
    val map = json.extract[Map[String, String]]
    val pw: String = map.getOrElse("pw",  halt(400, "pw"))
    val special = setup.mongoConfig.getString("special")
    if (special == pw) ontologies.remove(MongoDBObject()) else halt(401)
  }

}
