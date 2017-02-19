package org.mmisw.orr.ont.service

import com.typesafe.scalalogging.{StrictLogging ⇒ Logging}

import scala.util.control.NonFatal
import scalaj.http.{Http, HttpOptions, HttpResponse}

object httpUtil extends AnyRef with Logging {

  def downloadUrl(remoteUrl: String, acceptList: List[String] = List.empty)
  : Either[Throwable,String] = {

    logger.debug(s"downloadUrl: $remoteUrl  acceptList=$acceptList")

    try {
      val request = {
        val base = Http(remoteUrl)
          .timeout(connTimeoutMs = 5*1000, readTimeoutMs = 60*1000)
          .option(HttpOptions.followRedirects(true))

        if (acceptList.nonEmpty) base.header("Accept", acceptList.mkString(","))
        else base
      }

      val response: HttpResponse[String] = request.asString

      if (response.code == 200)
        Right(response.body)
      else {
        logger.debug(s"downloadUrl: $remoteUrl  status=${response.code}")
        val maxLen = 4000
        val body = if(response.body.length < maxLen) response.body
        else response.body.substring(0, maxLen) + s"\n...(${response.body.length - maxLen} chars skipped)"
        Left(DownloadRemoteServerError(remoteUrl, response.code, body))
      }
    }
    catch {
      case NonFatal(ex) ⇒ Left(ex)
    }
  }
}
