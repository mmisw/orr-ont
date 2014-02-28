package org.mmisw.orr.ont

import com.mongodb.casbah.Imports._
import com.typesafe.scalalogging.slf4j.Logging

import org.scalatra.servlet.{FileItem, SizeConstraintExceededException, FileUploadSupport}
import org.scalatra.{RequestEntityTooLarge, FlashMapSupport}
import javax.servlet.annotation.MultipartConfig
import java.io.File

@MultipartConfig(maxFileSize = 5*1024*1024)
class OntController(implicit setup: Setup) extends OrrOntStack
with FileUploadSupport with FlashMapSupport
with SimpleMongoDbJsonConversion with Logging {

//  configureMultipartHandling(MultipartConfig(maxFileSize = Some(5 * 1024 * 1024)))

  error {
    case e: SizeConstraintExceededException =>
      error(412, "The file you uploaded exceeded the 5MB limit.")
  }

  val ontologies = setup.db.ontologiesColl
  val users = setup.db.usersColl

  val versionFormatter = new java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss")

  def dupUriError(uri: String) = {
    MongoDBObject("error" -> s"'$uri' already in collection")
  }

  def getOnt(uri: String, versionOpt: Option[String]) = {
    ontologies.findOne(MongoDBObject("uri" -> uri)) match {
      case None => error(404, s"'$uri' is not registered")

      case Some(ont) =>
        val versions: MongoDBObject = ont.asDBObject("versions").asInstanceOf[BasicDBObject]
        versionOpt match {
          case None =>
            ont.getAs[String]("latestVersion") match {
              case None =>
                // should not happen
                error(500, s"'$uri': No latestVersion entry. Please notify this bug.")

              case Some(version) =>
                versions.get(version) match {
                  case None =>
                    // should not happen
                    error(500, s"'$uri', latest version '$version' is not registered. Please notify this bug.")

                  case Some(versionEntry) =>
                    val mo: MongoDBObject = versionEntry.asInstanceOf[BasicDBObject]
                    // todo get metadata
                    VersionInfo(uri, mo.getAsOrElse("name", ""), version, mo.getAsOrElse("date", ""))
                }
            }

          case Some(version) =>
            // val versions = new MongoDBObject(ont.asDBObject("versions").asInstanceOf[BasicDBObject])
            versions.get(version) match {
              case None => error(404, s"'$uri', version '$version' is not registered")

              case Some(versionEntry) =>
                val mo: MongoDBObject = versionEntry.asInstanceOf[BasicDBObject]
                // todo get metadata
                VersionInfo(uri, mo.getAsOrElse("name", ""), version, mo.getAsOrElse("date", ""))
            }
        }
    }
  }

  get("/") {
    params.get("uri") match {
      case Some(uri) =>
        getOnt(uri, params.get("version"))

      case None =>
        // TODO just list with basic info?
        ontologies.find()
    }
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
   * post a new ontology entry or a new version of an existing ontology entry.
   */
  post("/") {
    val uri = require(params, "uri")
    val nameOpt = params.get("name")
    val userName = verifyUser(params.get("userName"))

    val file = fileParams.getOrElse("file", missing("file"))
    val format = require(params, "format")

    val fileContents = new String(file.get(), file.charset.getOrElse("utf8"))
    //val contentType = file.contentType.getOrElse("application/octet-stream")

    logger.info(s"uploaded file size ${fileContents.length} ='$fileContents'  format=$format")

    // for now, the version is always automatically assigned
    val now = new java.util.Date()
    val version = versionFormatter.format(now)
    val date    = dateFormatter.format(now)

    val newVersion = MongoDBObject("date" -> date, "submitter" -> userName)

    val q = MongoDBObject("uri" -> uri)
    ontologies.findOne(q) match {

      case None =>  // new ontology entry
        val name = nameOpt.getOrElse(missing("name"))
        newVersion += "name" -> name

        val obj = MongoDBObject(
          "uri" -> uri,
          "latestVersion" -> version,
          "users" -> MongoDBObject(userName -> MongoDBObject("perms" -> "rw")),
          "versions" -> MongoDBObject(version -> newVersion)
        )
        writeOntology(uri, version, file, format)
        ontologies += obj
        Ontology(uri, name, Some(version))

      case Some(ont) =>   // existing ontology entry.
        val users = ont.getAs[BasicDBObject]("users").head
        users += userName -> MongoDBObject("perms" -> "rw")
        val versions = ont.getAs[BasicDBObject]("versions").head
        nameOpt foreach (name => newVersion += "name" -> name)
        versions += version -> newVersion
        val update = MongoDBObject(
          "uri" -> uri,
          "latestVersion" -> version,
          "users" -> users,
          "versions" -> versions
        )
        logger.info(s"update: $update")
        writeOntology(uri, version, file, format)
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

  def writeOntology(uri: String, version: String,
                    file: FileItem, format: String)(implicit setup: Setup) = {

    val baseDir = setup.filesConfig.getString("baseDirectory")
    val ontsDir = new File(baseDir, "onts")

    val uriEnc = uri.replace('/', '|')

    val uriDir = new File(ontsDir, uriEnc)

    val versionDir = new File(uriDir, version)
    versionDir.mkdirs() || error(500, s"could not create directory: $versionDir")

    val destFilename = s"file.$format"
    val dest = new File(versionDir, destFilename)

    file.write(dest)
  }

}
