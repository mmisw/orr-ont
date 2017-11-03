package org.mmisw.orr.ont.service

import org.mmisw.orr.ont.Cfg
import org.mmisw.orr.ont.util.IEmailer
import com.typesafe.scalalogging.{StrictLogging ⇒ Logging}

import scala.util.control.NonFatal

class Notifier(cfg: Cfg, emailer: IEmailer) extends Logging {

  // TODO use queue to control frequency of notifications,
  // especially desirable to better handle rapid bulk registrations/events.
  // For now, this is just a refactor of the old code.

  def sendNotificationEmail(subject: String, msg: String): Unit = {
    new Thread(new Runnable {
      def run() {
        doIt()
      }
    }).start()

    def doIt(): Unit = {
      for {
        filename ← recipientsFilename
        emails ← getEmails(filename)
        if emails.nonEmpty
      } {
        val body = msg + "\n\n" +
          s"(You have received this email because your address is included in $filename)"
        emailer.sendEmail(emails.mkString(","), subject, body)
      }
    }
  }

  def destroy(): Unit = ()

  private def getEmails(filename: String): Option[Seq[String]] = {
    try {
      val source = io.Source.fromFile(filename)
      val emails = source.getLines.map(_.trim).filterNot { line ⇒
        line.isEmpty || line.startsWith("#")
      }
      Some(emails.toSeq)
    }
    catch {
      case _:java.io.FileNotFoundException ⇒
        logger.warn(s"sendNotificationEmail: file not found: $filename")
        None
      case NonFatal(e) ⇒
        logger.error("error sending email", e)
        None
    }
  }

  private val recipientsFilename = cfg.notifications.recipientsFilename
}
