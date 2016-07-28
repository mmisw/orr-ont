package org.mmisw.orr.ont.app

import dispatch._
import org.json4s._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Promise
import scala.util.{Failure, Success, Try}


object recaptcha {

  def validateResponse(privateKey: String, recaptchaResponse: String): Either[Throwable, Boolean] = {
    val req = recaptchaEndpoint
      .addParameter("secret", privateKey)
      .addParameter("response", recaptchaResponse)

    val prom = Promise[Either[Throwable, Boolean]]()
    Http(req.POST > as.json4s.Json) onComplete {
      case Success(json) =>
        json \ "success" match {
          case JBool(success) => prom.complete(Try(Right(success)))
          case _              => prom.complete(Try(Right(false)))
        }
      case Failure(exception) => prom.complete(Try(Left(exception)))
    }
    prom.future()
  }

  private val recaptchaEndpoint = url("https://www.google.com/recaptcha/api/siteverify")
}
