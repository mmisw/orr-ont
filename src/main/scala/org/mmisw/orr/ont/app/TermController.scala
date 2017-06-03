package org.mmisw.orr.ont.app

import com.typesafe.scalalogging.{StrictLogging ⇒ Logging}
import org.mmisw.orr.ont._

import scalaj.http._

class TermController(implicit setup: Setup) extends BaseController with Logging {
  /*
   * Term search "shortcuts"
   */
  get("/") {
    implicit val limit: Int = {
      try params.get("limit").getOrElse("10").toInt
      catch {
        case e: NumberFormatException ⇒ error(400, e.getMessage)
      }
    }

    params.get("containing") match {
      case Some(containing) ⇒
        queryContaining(containing, params.get("in").getOrElse("s"))

      case None =>
        val keys = params.keySet
        val skosPreds = keys.filter(_.startsWith("skos:"))
        if (skosPreds.nonEmpty) {
          if (skosPreds.size > 1) error(400, "at most one skos:* parameter expected")
          val skosPred = skosPreds.head
          val termIri = params(skosPred)
          querySkosRelation(termIri, skosPred)
        }
        else {
          val termIri = params.getOrElse("sameAs", error(400, "missing recognized parameter"))
          querySameAs(termIri)
        }
    }
  }

  ///////////////////////////////////////////////////////////////////////////

  private def queryContaining(containing: String, in: String)
                             (implicit limit: Int): String = {

    logger.debug(s"queryContaining: $containing in=$in limit=$limit")
    var ors = collection.mutable.ListBuffer[String]()
    if (in.contains("s")) {
      // TODO copied from orr-portal -- what's the restriction about?
      ors += s"""regex(str(?subject), "$containing[^/#]*$$", "i")"""
    }
    if (in.contains("p")) {
      ors += s"""regex(str(?predicate), "$containing", "i")"""
    }
    if (in.contains("o")) {
      ors += s"""regex(str(?object), "$containing", "i")"""
    }
    if (ors.isEmpty) {
      error(400, "'in' parameter must include at least one of 's', 'p', 'o'")
    }

    doQuery(s"""select distinct ?subject ?predicate ?object
               |where {
               | ?subject ?predicate ?object.
               | filter (${ors.mkString("||")})
               |}
               |order by ?subject
               |$limitFragment
      """.stripMargin.trim)
  }

  private def querySkosRelation(termIri: String, skosPred: String)
                               (implicit limit: Int): String = {
    logger.debug(s"querySkosRelation: termIri=$termIri skosPred=$skosPred limit=$limit")
    doQuery(s"""prefix skos: <http://www.w3.org/2004/02/skos/core#>
               |select distinct ?object
               |where {
               | <$termIri> $skosPred ?object.
               |}
               |order by ?object
               |$limitFragment
      """.stripMargin.trim)
  }

  private def querySameAs(termIri: String)
                         (implicit limit: Int): String = {
    logger.debug(s"querySameAs: termIri=$termIri limit=$limit")
    doQuery(s"""prefix owl: <http://www.w3.org/2002/07/owl#>
               |select distinct ?object
               |where {
               | <$termIri> owl:sameAs ?object.
               |}
               |order by ?object
               |$limitFragment
      """.stripMargin.trim)
  }

  private def doQuery(query: String): String = {
    val clientAccepts = acceptHeader.filterNot(_ == "*/*")
    val requestAccepts = if (clientAccepts.nonEmpty) clientAccepts else List("application/json")
    val acceptHeaders = requestAccepts.map(a ⇒ ("Accept", a))

    logger.debug(s"doQuery: acceptHeaders=$acceptHeaders query:\n\t${query.replaceAll("\n", "\n\t")}")

    val response: HttpResponse[String] = Http(sparqlEndpoint).
      method("GET").
      headers(acceptHeaders).
      timeout(connTimeoutMs = 2000, readTimeoutMs = 20*1000).
      param("query", query).
      asString

    logger.debug(s"""response: status=${response.code} body:
                    |\t${response.body.replaceAll("\n", "\n\t")}
                    |""".stripMargin)

    if (response.code == 200) response.body
    else error(response.code, Seq(
      ("error", response.statusLine), ("message", response.body)))
  }

  private def limitFragment(implicit limit: Int): String = s"limit " + limit

  private val sparqlEndpoint = setup.cfg.agraph.sparqlEndpoint
}
