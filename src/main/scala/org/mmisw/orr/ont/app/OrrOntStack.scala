package org.mmisw.orr.ont.app

import com.mongodb.casbah.Imports._
import org.json4s.JsonAST.{JArray, JNothing, JString, JValue}
import org.json4s.ext.JodaTimeSerializers
import org.json4s.{DefaultFormats, Formats}
import org.mmisw.orr.ont.swld.ontUtil
import org.scalatra._
import org.scalatra.json.NativeJsonSupport


trait OrrOntStack extends ScalatraServlet with NativeJsonSupport with CorsSupport {

  protected implicit val jsonFormats: Formats = DefaultFormats ++ JodaTimeSerializers.all

  protected val dateFormatter = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")

  protected def error(status: Int, msg: String): Nothing = halt(status, MongoDBObject("error" -> msg))

  protected def error(status: Int, details: Seq[(String,String)]): Nothing = halt(status, MongoDBObject(details: _*))

  protected def error500(exc: Throwable): Nothing = {
    exc.printStackTrace()
    halt(500, MongoDBObject("error" -> exc.getMessage))
  }

  protected def error500(msg: String): Nothing = halt(500, MongoDBObject("error" -> msg))

  protected def missing(paramName: String): Nothing = error(400, s"'$paramName' param missing")

  protected def bug(msg: String): Nothing = error500(s"$msg. Please notify this bug.")

  protected def require(map: Params, paramName: String): String = {
    val value = map.getOrElse(paramName, missing(paramName)).trim
    if (value.length > 0) value else error(400, s"'$paramName' param value missing")
  }

  protected def requireIriOrUri(map: Params): String = {
    val value = map.get("iri").getOrElse(map.getOrElse("uri", missing("iri"))).trim
    if (value.length > 0) value else missing("iri")
  }

  protected def getIriOrUri(map: Params): Option[String] = {
    (map.get("iri") orElse map.get("uri")).map(_.trim)
  }

  protected def acceptOnly(paramNames: String*) {
    val unrecognized = params.keySet -- Set(paramNames: _*)
    if (unrecognized.nonEmpty) error(400, s"unrecognized parameters: $unrecognized")
  }

  protected def body(): Map[String, JValue] = {
    val json = parsedBody
    if (json != JNothing) json.extract[Map[String, JValue]] else error(400, "missing json body")
  }

  protected def bodyOpt() : Option[Map[String, JValue]] = {
    val json = parsedBody
    if (json != JNothing) Some(json.extract[Map[String, JValue]]) else None
  }

  protected def require(map: Map[String, JValue], paramName: String): String = {
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

  protected def getSeq(map: Map[String, JValue], paramName: String, canBeEmpty: Boolean = false): List[String] = {
    val value = map.getOrElse(paramName, missing(paramName))
    if (!value.isInstanceOf[JArray]) error(400, s"'$paramName' param value is not an array")
    val arr = value.asInstanceOf[JArray].arr
    if (!canBeEmpty && arr.isEmpty) error(400, s"'$paramName' array param cannot be empty")
    arr map (_.asInstanceOf[JString].values)
  }

  protected def getArray(map: Map[String, JValue], paramName: String): List[JValue] = {
    val value = map.getOrElse(paramName, missing(paramName))
    if (!value.isInstanceOf[JArray]) error(400, s"'$paramName' param value is not an array")
    value.asInstanceOf[JArray].arr
  }

  protected def getSet(map: Map[String, JValue], paramName: String, canBeEmpty: Boolean = false): Set[String] =
    getSeq(map, paramName, canBeEmpty).toSet

  protected def toStringMap(map: Map[String, JValue]): Map[String,String] = {
    map.map {case (s, value) => (s, value.asInstanceOf[JString].values.trim)}
  }

  ontUtil.mimeMappings foreach { xm => addMimeMapping(xm._2, xm._1) }

  /** Gets a param from the body or from the 'params' */
  protected def getParam(name: String): Option[String] = {
    val fromBody: Option[String] = for (body <- bodyOpt(); value <- getString(body, name)) yield value
    fromBody.orElse(params.get(name))
  }

  /** Requires a param from the body or from the 'params' */
  protected def requireParam(name: String): String = getParam(name).getOrElse(missing(name))

  protected def getIriOrUriParam: Option[String] = getParam("iri") orElse getParam("uri")

  protected def requireIriOrUriParam(): String = getIriOrUriParam.getOrElse(missing("iri"))

  before() {
    contentType = formats("json")
  }

  options("/*"){
    response.setHeader("Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers"))
  }
}
