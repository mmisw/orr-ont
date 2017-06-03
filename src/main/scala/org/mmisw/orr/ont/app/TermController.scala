package org.mmisw.orr.ont.app

import java.util.regex.Pattern

import com.typesafe.scalalogging.{StrictLogging ⇒ Logging}
import org.mmisw.orr.ont._

import scalaj.http._

class TermController(implicit setup: Setup) extends BaseController with Logging {
  /*
   * Term search "shortcuts"
   */
  get("/") {
    implicit val limitOpt: Option[Int] = {
      try params.get("limit").map(_.toInt)
      catch {
        case e: NumberFormatException ⇒ error(400, e.getMessage)
      }
    }

    params.get("containing") match {
      case Some(containing) ⇒ queryContaining(containing)

      case None => params.get("skosMatch") match {
        case Some(termIri) ⇒ querySkosRelation(termIri, require(params, "relation"))

        case None ⇒ params.get("sameAs") match {
          case Some(termIri) ⇒ querySameAs(termIri)

          case None ⇒ error(400, "missing recognized parameter")
        }
      }
    }
  }

  ///////////////////////////////////////////////////////////////////////////

  private def queryContaining(containing: String)
                             (implicit limitOpt: Option[Int] = None): String = {
    val escaped = escape(containing)
    doQuery(s"""select distinct ?subject ?predicate ?object
               |where {
               | ?subject ?predicate ?object.
               | filter (regex(str(?subject), "$escaped[^/#]*$$", "i")
               |      || regex(str(?object), "$escaped", "i"))
               |}
               |order by ?subject
               |${limitOpt.map(lim ⇒ s"limit " + lim)}
      """.stripMargin.trim)
  }

  private def querySkosRelation(termIri: String, relation: String)
                               (implicit limitOpt: Option[Int] = None): String =
    doQuery(s"""prefix skos: <http://www.w3.org/2004/02/skos/core#>
               |select distinct ?object
               |where {
               | ?<$termIri> $relation ?object.
               |}
               |order by ?object
               |${limitOpt.map(lim ⇒ s"limit " + lim)}
      """.stripMargin.trim)

  private def querySameAs(termIri: String)
                         (implicit limitOpt: Option[Int] = None): String =
    doQuery(s"""prefix owl: <http://www.w3.org/2002/07/owl#>
               |select distinct ?object
               |where {
               | ?<$termIri> owl:sameAs ?object.
               |}
               |order by ?object
               |${limitOpt.map(lim ⇒ s"limit " + lim)}
      """.stripMargin.trim)

  private def doQuery(query: String): String = {
    val sparqlEndpoint = setup.cfg.agraph.sparqlEndpoint
    val response: HttpResponse[String] = Http(sparqlEndpoint).param("query", query).asString
    if (logger.underlying.isDebugEnabled()) {
      logger.debug(s"""doQuery: query=$query
                      | response:
                      |   status=${response.code}
                      |   body=${response.body}
                         """.stripMargin)
    }
    if (response.code == 200) response.body
    else error(response.code, response.statusLine)
  }

  private def escape(string: String): String = Pattern.quote(string)
}
