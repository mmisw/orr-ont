package org.mmisw.orr.ont

import org.scalatra._
import scalate.ScalateSupport

class OntServlet extends OrrOntStack {

  get("/") {
    <html>
      <body>
        <h1>Hello, world!</h1>
        Say <a href="hello-scalate">hello to Scalate</a>.
      </body>
    </html>
  }
  
}
