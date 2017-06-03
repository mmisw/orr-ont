package org.mmisw.orr.ont.app

import com.typesafe.scalalogging.{StrictLogging ⇒ Logging}
import org.apache.jena.vocabulary.{OWL, RDF, RDFS, SKOS}
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

      case None ⇒
        val predicate = params.getOrElse("predicate", error(400, "missing recognized parameter"))
        implicit val subObOpts = (params.get("subject"), params.get("object"))
        subObOpts._1 orElse subObOpts._2 orElse error(400, "one of subject or object must be provided")

        if (predicate.startsWith("skos:")) {
          queryPrefixedPredicate("skos", SKOS.uri, predicate)
        }
        else if (predicate.startsWith("owl:")) {
          queryPrefixedPredicate("owl", OWL.NS, predicate)
        }
        else if (predicate.startsWith("rdfs:")) {
          queryPrefixedPredicate("rdfs", RDFS.uri, predicate)
        }
        else if (predicate.startsWith("rdf:")) {
          queryPrefixedPredicate("rdf", RDF.uri, predicate)
        }
        else {
          queryPredicate(predicate)
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

  private def queryPrefixedPredicate(prefix: String, prefixValue: String, prefixedPredicate: String)
                                    (implicit subObOpts: (Option[String], Option[String]),
                                     limit: Int): String = {

    val subjectOpt = subObOpts._1
    val objectOpt = subObOpts._2
    logger.debug(s"queryPrefixedPredicate: prefix=$prefix prefixValue=$prefixValue" +
      s" subjectOpt=$subjectOpt prefixedPredicate=$prefixedPredicate objectOpt=$objectOpt limit=$limit")

    val (select, where, order) = subjectOpt match {
      case Some(subject) ⇒ ("?object",  s"<$subject> $prefixedPredicate ?object.",            "?subject")
      case None          ⇒ ("?subject", s"?subject   $prefixedPredicate <${objectOpt.get}>.", "?object")
    }
    doQuery(s"""prefix $prefix: <$prefixValue>
               |select distinct $select
               |where { $where }
               |order by $order
               |$limitFragment
      """.stripMargin.trim)
  }

  private def queryPredicate(predicate: String)
                            (implicit subObOpts: (Option[String], Option[String]),
                             limit: Int): String = {

    val subjectOpt = subObOpts._1
    val objectOpt = subObOpts._2
    logger.debug(s"queryPredicate:" +
      s" subjectOpt=$subjectOpt predicate=$predicate objectOpt=$objectOpt limit=$limit")

    val (select, where, order) = subjectOpt match {
      case Some(subject) ⇒ ("?object",  s"<$subject> <$predicate> ?object.",            "?subject")
      case None          ⇒ ("?subject", s"?subject   <$predicate> <${objectOpt.get}>.", "?object")
    }
    doQuery(s"""select distinct $select
               |where { $where }
               |order by $order
               |$limitFragment
      """.stripMargin.trim)
  }

  private def doQuery(query: String): String = {
    val clientAccepts = acceptHeader.filterNot(h ⇒ h == "*/*" || h.contains("html"))
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
