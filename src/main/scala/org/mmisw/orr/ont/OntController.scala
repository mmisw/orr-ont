package org.mmisw.orr.ont

import com.mongodb.casbah.Imports._
import com.typesafe.scalalogging.slf4j.Logging
import org.json4s.JsonAST.{JNothing, JValue}


class OntController(implicit setup: Setup) extends OrrOntStack
with SimpleMongoDbJsonConversion with Logging {

  val ontologies = setup.db.ontologiesColl
  val users = setup.db.usersColl

  val versionFormatter = new java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss")

  def dupUriError(uri: String) = {
    MongoDBObject("error" -> s"'$uri' already in collection")
  }

  get("/") {
    ontologies.find()
  }

  def verifyUser(userNameOpt: Option[String]): String = userNameOpt match {
    case None => missing("userName")
    case Some(userName) =>
      if (setup.testing) userName
      else {
        users.findOne(MongoDBObject("userName" -> userName)) match {
          case None => error(400, s"'$userName' invalid user")
          case _ => userName
        }
      }
  }

  /**
   * post a new ontology entry or a new version of an existing ontology entry
   */
  post("/") {
    val map = body()

    val uri = require(map, "uri")
    val userName = verifyUser(map.get("userName"))

    // for now, the version is always automatically assigned
    val now = new java.util.Date()
    val version = versionFormatter.format(now)
    val date    = dateFormatter.format(now)

    val newVersion = MongoDBObject("date" -> date, "submitter" -> userName)

    val q = MongoDBObject("uri" -> uri)
    ontologies.findOne(q) match {

      case None =>  // new ontology entry
        val name = require(map, "name")
        newVersion += "name" -> name

        val obj = MongoDBObject(
          "uri" -> uri,
          "name" -> name,
          "users" -> MongoDBObject(userName -> MongoDBObject("perms" -> "rw")),
          "versions" -> MongoDBObject(version -> newVersion)
        )
        ontologies += obj
        Ontology(uri, name, Some(version))

      case Some(ont) =>   // existing ontology entry.
        val users = ont.getAs[BasicDBObject]("users").head
        users += userName -> MongoDBObject("perms" -> "rw")
        val versions = ont.getAs[BasicDBObject]("versions").head
        versions += version -> newVersion
        val update = MongoDBObject(
          "uri" -> uri,
          "users" -> users,
          "versions" -> versions
        )
        if (map.contains("name")) update += "name" -> map.get("name").head
        logger.info(s"update: $update")
        val result = ontologies.update(q, update)
        OntologyResult(uri, Some(version), s"updated (${result.getN})")
    }
  }

  // updates a particular version
  put("/version") {
    val map = body()

    val uri     = require(map, "uri")
    val version = require(map, "version")

    val obj = MongoDBObject("uri" -> uri)
    ontologies.findOne(obj) match {
      case None =>
        error(404, s"'$uri' is not registered")

      case Some(found) =>
        // TODO verify version exists ...
        // todo do the update
        val update = found
        update.put("TODO", "implement put")
        val result = ontologies.update(obj, update)
        OntologyResult(uri, Some(version), s"updated (${result.getN})")
    }
  }

  delete("/") {
    val uri: String = params.getOrElse("uri", missing("uri"))

    val obj = MongoDBObject("uri" -> uri)
    val result = ontologies.remove(obj)
    OntologyResult(uri, comment = s"removed (${result.getN})")
  }

  post("/!/deleteAll") {
    val map = body()
    val pw = require(map, "pw")
    val special = setup.mongoConfig.getString("pw_special")
    if (special == pw) ontologies.remove(MongoDBObject()) else halt(401)
  }

}
