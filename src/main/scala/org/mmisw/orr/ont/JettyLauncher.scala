package org.mmisw.orr.ont


// if creating standalone, set "container:compile" for "jetty-webapp" in the build
// and uncomment code:

//import org.eclipse.jetty.server.Server
//import org.eclipse.jetty.servlet.DefaultServlet
//import org.eclipse.jetty.webapp.WebAppContext
//import org.scalatra.servlet.ScalatraListener

/**
 *
 */
object JettyLauncher {

  //  def main(args: Array[String]) {
  //    val port = if (System.getenv("PORT") != null) System.getenv("PORT").toInt else 8080
  //
  //    val configFile = System.getenv("configFile")
  //    if (configFile == null) {
  //      throw new RuntimeException("configFile undefined")
  //    }
  //
  //    val context = new WebAppContext()
  //    context.setInitParameter("configFile", configFile)
  //    val server = new Server(port)
  //    context setContextPath "/"
  //    context.setResourceBase("src/main/webapp")
  //    context.addEventListener(new ScalatraListener)
  //    context.addServlet(classOf[DefaultServlet], "/")
  //
  //    server.setHandler(context)
  //
  //    server.start()
  //    server.join()
  //  }
}
