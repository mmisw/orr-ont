package org.mmisw.orr.ont

import org.scalatra._
import org.scalatra.json.NativeJsonSupport
import org.json4s.{DefaultFormats, Formats}


trait OrrOntStack extends ScalatraServlet with NativeJsonSupport {

  protected implicit val jsonFormats: Formats = DefaultFormats

  before() {
    contentType = formats("json")
  }
}
