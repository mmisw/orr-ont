package org.mmisw.orr.ont.service

import com.typesafe.scalalogging.{StrictLogging ⇒ Logging}

import scala.util.control.NonFatal
import scalaj.http.{Http, HttpResponse}

object httpUtil extends AnyRef with Logging {

  def downloadUrl(remoteUrl: String, acceptList: List[String] = List.empty)
  : Either[Throwable,String] = {

    logger.debug(s"downloadUrl: $remoteUrl  acceptList=$acceptList")

    try {
      val request = {
        val base = Http(remoteUrl)
          .timeout(connTimeoutMs = 5*1000, readTimeoutMs = 60*1000)

        if (acceptList.nonEmpty) base.header("Accept", acceptList.mkString(","))
        else base
      }

      val response: HttpResponse[String] = request.asString

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
