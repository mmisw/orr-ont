package org.mmisw.orr.ont.app

import java.io.File

import com.typesafe.scalalogging.{StrictLogging => Logging}
import org.mmisw.orr.ont.Setup
import org.scalatra.{CorsSupport, ScalatraServlet}

class ApiDocs(implicit setup: Setup) extends ScalatraServlet with CorsSupport
    with Logging {

  get("/*") {
    response.setHeader("Access-Control-Allow-Headers", "*")
    contentType = "application/x-yaml"
    new File("swagger.yaml")
  }

  options("/*"){
    response.setHeader("Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers"))
  }
}
