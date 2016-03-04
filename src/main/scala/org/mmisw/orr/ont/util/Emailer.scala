package org.mmisw.orr.ont.util

import com.typesafe.config.Config


trait IEmailer {
  def sendEmail(to: String, subject: String, text: String): Unit
}

class Emailer(emailConfig: Config) extends IEmailer {
  private[this] val username = emailConfig.getString("account.username")
  private[this] val password = emailConfig.getString("account.password")

  private[this] val mailhost = emailConfig.getString("server.host")
  private[this] val mailport = emailConfig.getString("server.port")
  private[this] val prot     = emailConfig.getString("server.prot")
  private[this] val debug    = emailConfig.getBoolean("server.debug")

  private[this] val from     = emailConfig.getString("from")
  private[this] val replyTo  = emailConfig.getString("replyTo")
  private[this] val mailer   = emailConfig.getString("mailer")


  def sendEmail(to: String, subject: String, text: String): Unit = {
    MailSender.sendMessage(mailer, mailhost, mailport, prot, username, password, debug,
      from, to, replyTo, subject, text)
  }
}
