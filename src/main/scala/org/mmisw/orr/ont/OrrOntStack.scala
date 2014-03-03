package org.mmisw.orr.ont

import org.scalatra._
import org.scalatra.json.NativeJsonSupport
import org.json4s.{DefaultFormats, Formats}
import com.mongodb.casbah.Imports._
import org.json4s.JsonAST.JNothing


trait OrrOntStack extends ScalatraServlet with NativeJsonSupport {

  protected implicit val jsonFormats: Formats = DefaultFormats

  protected val dateFormatter = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")

  protected def error(status: Int, msg: String): Nothing = halt(status, MongoDBObject("error" -> msg))

  protected def missing(paramName: String): Nothing = error(400, s"'$paramName' param missing")

  protected def bug(msg: String): Nothing = error(500, s"$msg. Please notify this bug.")

  protected def body(): Map[String, String] = {
    val json = parse(request.body)
    if (json != JNothing) json.extract[Map[String, String]] else error(400, "missing json body")
  }

  protected def require(map: Map[String, String], paramName: String) = {
    val value = map.getOrElse(paramName, missing(paramName)).trim
    if (value.length > 0) value else error(400, s"'$paramName' param value missing")
  }

  protected def require(map: Params, paramName: String) = {
    val value = map.getOrElse(paramName, missing(paramName)).trim
    if (value.length > 0) value else error(400, s"'$paramName' param value missing")
  }

  addMimeMapping("application/rdf+xml", "rdf")
  addMimeMapping("application/rdf+xml", "owl")

  addMimeMapping("text/plain", "n3")   // should actually be text/n3?

  //addMimeMapping("application/ld+json", "json")  // TODO: JSON-LD

  before() {
    contentType = formats("json")
  }
}
