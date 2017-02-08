package org.mmisw.orr.ont.util

import com.typesafe.scalalogging.{StrictLogging ⇒ Logging}
import org.mmisw.orr.ont.Cfg


trait IEmailer {
  def sendEmail(to: String, subject: String, text: String): Unit
}

class Emailer(emailConfig: Cfg.Email) extends IEmailer with Logging {
  private[this] val username = emailConfig.account.username
  private[this] val password = emailConfig.account.password

  private[this] val mailhost = emailConfig.server.host
  private[this] val mailport = emailConfig.server.port.toString
  private[this] val prot     = emailConfig.server.prot
  private[this] val debug    = emailConfig.server.debug

  private[this] val from     = emailConfig.from
  private[this] val replyTo  = emailConfig.replyTo
  private[this] val mailer   = emailConfig.mailer


  def sendEmail(to: String, subject: String, text: String): Unit = {
    logger.debug(s"sendMail: to='$to' subject='$subject'")
    MailSender.sendMessage(mailer, mailhost, mailport, prot, username, password, debug,
      from, to, replyTo, subject, text)
  }
}
