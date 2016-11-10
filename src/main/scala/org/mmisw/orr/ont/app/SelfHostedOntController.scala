package org.mmisw.orr.ont.app

import java.io.File
import javax.servlet.http.{HttpServletRequest, HttpServletRequestWrapper}

import com.typesafe.scalalogging.{StrictLogging => Logging}
import org.mmisw.orr.ont.service.{OntService, TripleStoreService}
import org.mmisw.orr.ont.Setup

/**
  * Controller to dispatch "self-resolvable" ontology/terms requests.
  *
  * Includes mechanism to dispatch the UI (ORR Portal) such that the
  * application context is preserved in the browser's location address.
  */
class SelfHostedOntController(implicit setup: Setup,
                              ontService: OntService,
                              tsService: TripleStoreService
                             ) extends BaseOntController
    with Logging {

  get("/(.*)".r) {
    val pathInfo = request.pathInfo
    if (pathInfo.startsWith("/api")) {
      pass()
    }

    val reqFormat = getRequestedFormat

    if (logger.underlying.isDebugEnabled &&
      !List(".html", ".js", ".css", ".map", "woff2").exists(pathInfo.endsWith)) {
      logger.debug(s"SelfHostedOntController: reqFormat=$reqFormat request.pathInfo=$pathInfo")
    }

    if (!portalDispatch(pathInfo, reqFormat)) {
      // skip leading slash if any
      val noSlash = if (pathInfo.length() > 1) pathInfo.substring(1) else pathInfo
      resolve(noSlash, reqFormat)
    }
  }

  ///////////////////////////////////////////////////////////////////////////

  /** returns true only if the dispatch is completed here */
  private def portalDispatch(pathInfo: String, reqFormat: String): Boolean = {
    // do the special HTML dispatch below only if the "/index.html" exists under my context:
    val hasIndexHtml = Option(request.getServletContext.getRealPath("/index.html")) match {
      case Some(realPath) => new File(realPath).isFile
      case _ => false
    }

    if (logger.underlying.isDebugEnabled &&
      !List(".html", ".js", ".css", ".map", "woff2").exists(pathInfo.endsWith)) {
      logger.debug(s"portalDispatch: hasIndexHtml=$hasIndexHtml pathInfo=$pathInfo")
    }

    def adjustedRequest(request: HttpServletRequest): HttpServletRequest = {
      if (pathInfo == "/" || pathInfo == "/sparql" || pathInfo == "/sparql/") {
        val adjustedPath = s"${pathInfo.replaceAll("/+$", "")}/index.html"
        logger.debug(s"adjustedRequest: for request=$pathInfo adjustedPath=$adjustedPath")
        contentType = formats("html") // make sure html is responded
        new HttpServletRequestWrapper(request) {
          override def getPathInfo = adjustedPath
        }
      }
      else {
        contentType = null  // so, "default" servlet sets the content type according to the resource
        request
      }
    }

    if (hasIndexHtml) {
      val isUiResource = List("/vendor", "/js", "/img", "/css") exists pathInfo.startsWith

      if (isUiResource || pathInfo == "/sparql" || pathInfo == "/sparql/") {
        servletContext.getNamedDispatcher("default").forward(adjustedRequest(request), response)
        true
      }
      else if (reqFormat == "html") { // for HTML always dispatch /index.html
        logger.debug(s"serving '/index.html' for request: $pathInfo")
        val indexRequest = new HttpServletRequestWrapper(request) {
          override def getPathInfo = "/index.html"
        }
        contentType = formats("html") // make sure html is responded
        servletContext.getNamedDispatcher("default").forward(indexRequest, response)
        true
      }
      else false
    }
    else false
  }

  private val orgOrUserPattern = "ont/([^/]+)$".r

  private def resolve(pathInfo: String, reqFormat: String) = {
    logger.debug(s"resolve: pathInfo='$pathInfo'")
    orgOrUserPattern.findFirstMatchIn(pathInfo).toList.headOption match {
      case Some(m) => resolveOrgOrUser(m.group(1), reqFormat)
      case None =>
        val uri = request.getRequestURL.toString
        logger.debug(s"self-resolving '$uri' ...")
        resolveOntOrTermUri(uri, Some(reqFormat))
    }
  }

  /*
   * Dispatches organization OR user ontology request (/ont/xyz).
   * This intends to emulate behavior in previous Ont service
   * when xyx corresponds to an existing authority abbreviation
   * (with generation of list of associated ontologies).
   * Here this is very preliminary. Also possible dispatch in the case
   * where xyz corresponds to a userName.
   * TODO review the whole thing.
   */
  private def resolveOrgOrUser(xyz: String, reqFormat: String) = {
    logger.debug(s"resolve: xyz='$xyz'")
    orgsDAO.findOneById(xyz) match {
      case Some(org) =>
        org.ontUri match {
          case Some(ontUri) => redirect(ontUri)
          case None =>
            try selfResolve(reqFormat)
            catch {
              case exc: AnyRef =>
                logger.info(s"EXC in selfResolve: $exc")
                // TODO dispatch some synthetic response as in previous Ont
                error500(s"TODO: generate summary for organization '$xyz'")
            }
        }
      case None =>
        usersDAO.findOneById(xyz) match {
          case Some(user) =>
            user.ontUri match {
              case Some(ontUri) => redirect(ontUri)
              case None =>
                error500(s"TODO: generate summary for user '$xyz'")
            }
          case None => error(404, s"No organization or user by given name: '$xyz'")
        }
    }
  }

  private def selfResolve(reqFormat: String) = {
    val uri = request.getRequestURL.toString
    logger.debug(s"self-resolving '$uri' ...")
    resolveOntOrTermUri(uri, Some(reqFormat))
  }
}
