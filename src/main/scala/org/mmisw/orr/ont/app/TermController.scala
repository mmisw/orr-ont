package org.mmisw.orr.ont.app

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
    doQuery(s"""select distinct ?subject ?predicate ?object
               |where {
               | ?subject ?predicate ?object.
               | filter (regex(str(?subject), "$containing[^/#]*$$", "i")
               |      || regex(str(?object), "$containing", "i"))
               |}
               |order by ?subject
               |$limitFragment
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
               |$limitFragment
      """.stripMargin.trim)

  private def querySameAs(termIri: String)
                         (implicit limitOpt: Option[Int] = None): String =
    doQuery(s"""prefix owl: <http://www.w3.org/2002/07/owl#>
               |select distinct ?object
               |where {
               | ?<$termIri> owl:sameAs ?object.
               |}
               |order by ?object
               |$limitFragment
      """.stripMargin.trim)

  private def doQuery(query: String): String = {
    logger.debug(s"doQuery: query:\n\t${query.replaceAll("\n", "\n\t")}")

    val response: HttpResponse[String] = Http(sparqlEndpoint).
      method("GET").
      timeout(connTimeoutMs = 2000, readTimeoutMs = 10*1000).
      param("query", query).
      asString

    logger.debug(s"""response: status=${response.code} body:
                    |\t${response.body.replaceAll("\n", "\n\t")}
                    |""".stripMargin)

    if (response.code == 200) response.body
    else error(response.code, response.statusLine)
  }

  private def limitFragment(implicit limitOpt: Option[Int] = None): String =
    limitOpt.map(lim ⇒ s"limit " + lim).getOrElse("")

  private val sparqlEndpoint = setup.cfg.agraph.sparqlEndpoint
}
