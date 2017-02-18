package org.mmisw.orr.ont.service

import com.typesafe.scalalogging.{StrictLogging ⇒ Logging}

import scala.util.control.NonFatal
import scalaj.http.{Http, HttpResponse}

object httpUtil extends AnyRef with Logging {

  def downloadUrl(remoteUrl: String): Either[Throwable,String] = {
    logger.debug(s"downloadUrl: $remoteUrl")

    try {
      val response: HttpResponse[String] = Http(remoteUrl)
        .timeout(connTimeoutMs = 5*1000, readTimeoutMs = 60*1000)
        .asString

      if (response.code == 200)
        Right(response.body)
      else
        Left(new Exception(s"error downloading remoteUrl=$remoteUrl: response=" + response))
    }
    catch {
      case NonFatal(ex) ⇒ Left(ex)
    }
  }
}
