package org.mmisw.orr.ont.service

import java.io.File

import com.typesafe.scalalogging.{StrictLogging ⇒ Logging}

import scala.util.control.NonFatal
import scalaj.http.{Http, HttpOptions, HttpResponse}

object httpUtil extends AnyRef with Logging {

  case class DownloadResult(body:String,
                            contentType: Option[String]
                           )

  def downloadUrl(remoteUrl: String,
                  acceptList: List[String] = List.empty,
                  saveInFile: Option[File] = None
                 ): Either[Throwable, DownloadResult] = {

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
      if (response.code == 200) {
        saveInFile foreach { file ⇒
          java.nio.file.Files.write(file.toPath,
            response.body.getBytes(java.nio.charset.StandardCharsets.UTF_8))
        }
        Right(DownloadResult(response.body, response.contentType))
      }
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
