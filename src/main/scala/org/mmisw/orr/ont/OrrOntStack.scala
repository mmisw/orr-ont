package org.mmisw.orr.ont

import org.scalatra._
import org.scalatra.json.NativeJsonSupport
import org.json4s.{DefaultFormats, Formats}
import com.mongodb.casbah.Imports._
import org.json4s.JsonAST.{JArray, JString, JValue, JNothing}


trait OrrOntStack extends ScalatraServlet with NativeJsonSupport {

  protected implicit val jsonFormats: Formats = DefaultFormats

  protected val dateFormatter = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")

  protected def error(status: Int, msg: String): Nothing = halt(status, MongoDBObject("error" -> msg))

  protected def missing(paramName: String): Nothing = error(400, s"'$paramName' param missing")

  protected def bug(msg: String): Nothing = error(500, s"$msg. Please notify this bug.")

  protected def require(map: Params, paramName: String) = {
    val value = map.getOrElse(paramName, missing(paramName)).trim
    if (value.length > 0) value else error(400, s"'$paramName' param value missing")
  }

  protected def body(): Map[String, JValue] = {
    val json = parse(request.body)
    if (json != JNothing) json.extract[Map[String, JValue]] else error(400, "missing json body")
  }

  protected def require(map: Map[String, JValue], paramName: String) = {
    val value = map.getOrElse(paramName, missing(paramName))
    if (!value.isInstanceOf[JString]) error(400, s"'$paramName' param value is not a string")
    val str = value.asInstanceOf[JString].values.trim
    if (str.length > 0) str else error(400, s"'$paramName' param value missing")
  }

  protected def getString(map: Map[String, JValue], paramName: String): Option[String] = {
    map.get(paramName) map {value =>
      if (!value.isInstanceOf[JString]) error(400, s"'$paramName' param value is not a string")
      value.asInstanceOf[JString].values
    }
  }

  protected def getSeq(map: Map[String, JValue], paramName: String): List[String] = {
    val value = map.getOrElse(paramName, missing(paramName))
    if (!value.isInstanceOf[JArray]) error(400, s"'$paramName' param value is not an array")
    value.asInstanceOf[JArray].arr map (_.asInstanceOf[JString].values)
  }

  addMimeMapping("application/rdf+xml", "rdf")
  addMimeMapping("application/rdf+xml", "owl")

  addMimeMapping("text/plain", "n3")   // should actually be text/n3?

  //addMimeMapping("application/ld+json", "json")  // TODO: JSON-LD

  before() {
    contentType = formats("json")
  }
}
