package org.mmisw.orr.ont

/*
   To actually create a working standalone:

   1) uncomment code in the object below
   2) set "container:compile" for "jetty-webapp" in build.scala
   3) build with:
      $ sbt assembly
   4) run with:
      $ configFile=/etc/mmiorr.conf PORT=8081 java -jar target/scala-2.11/orr-ont-assembly-0.1.0.jar
*/

/**
  * Main class for standalone executable.
  */
object JettyLauncher {
//  import org.eclipse.jetty.server.Server
//  import org.eclipse.jetty.servlet.DefaultServlet
//  import org.eclipse.jetty.webapp.WebAppContext
//  import org.scalatra.servlet.ScalatraListener
//
//  def main(args: Array[String]) {
//    val configFile   = sys.env.getOrElse("configFile", throw new RuntimeException("configFile undefined"))
//    val port         = sys.env.getOrElse("PORT", "8080").toInt
//    val resourceBase = sys.env.getOrElse("resourceBase", "src/main/webapp")
//
//    val context = new WebAppContext()
//    context.setInitParameter("configFile", configFile)
//    val server = new Server(port)
//    context setContextPath "/"
//    context.setResourceBase(resourceBase)
//    context.addEventListener(new ScalatraListener)
//    context.addServlet(classOf[DefaultServlet], "/")
//
//    server.setHandler(context)
//
//    println(s"\nJettyLauncher: running server on port=$port resourceBase=$resourceBase\n")
//    server.start()
//    server.join()
//  }
}
