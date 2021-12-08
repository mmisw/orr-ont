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
  private[this] val prot = emailConfig.server.prot
  private[this] val debug = emailConfig.server.debug

  private[this] val from = emailConfig.from
  private[this] val replyTo = emailConfig.replyTo
  private[this] val mailer = emailConfig.mailer


  def sendEmail(to: String, subject: String, text: String): Unit = {
    logger.debug(s"sendMail: to='$to' subject='$subject'")
    sendMessage(mailer, mailhost, mailport, prot, username, password, debug,
      from, to, replyTo, subject, text)
  }

  import scala.util.control.NonFatal

  // the following copied (almost) verbatim from previous mmiorr: org.mmisw.orrclient.core.util.MailSender;
  // then translated to Scala automatically by Intellij IDEA while pasting the java code here,
  // then slightly adjusted to fix compile errors.
  import com.sun.mail.smtp.SMTPTransport
  import java.util.Date
  import javax.mail.Address
  import javax.mail.internet.{InternetAddress, MimeMessage}
  import javax.mail.{Authenticator, Message, PasswordAuthentication, Session}

  private def addresses(s: String): Array[Address] = s
    .split("\\s*,\\s*")
    .map(new InternetAddress(_))

  private def sendMessage(mailer: String,
                          mailHost: String,
                          mailPort: String,
                          protocol: String,
                          user: String,
                          password: String,
                          debug: Boolean,
                          from: String,
                          to: String,
                          replyTo: String,
                          subject: String,
                          text: String
                         ): Unit = {
    val props = System.getProperties
    props.put("mail." + protocol + ".host", mailHost)
    props.put("mail." + protocol + ".auth", "true")
    if (mailPort != null) {
      props.put("mail." + protocol + ".port", mailPort)
    }
    val session = Session.getInstance(props, new Authenticator() {
      override protected def getPasswordAuthentication = new PasswordAuthentication(user, password)
    })
    if (debug) session.setDebug(true)
    val msg = new MimeMessage(session)
    msg.setFrom(new InternetAddress(from))

    msg.setRecipients(Message.RecipientType.TO, addresses(to))

    if (replyTo != null) {
      msg.setReplyTo(addresses(replyTo))
    }

    msg.setSubject(subject)
    msg.setText(text)
    msg.setHeader("X-Mailer", mailer)
    msg.setSentDate(new Date)
    val t = session.getTransport(protocol).asInstanceOf[SMTPTransport]
    try {
      t.connect(mailHost, user, password)
      t.sendMessage(msg, msg.getAllRecipients)
      logger.debug("Response: " + t.getLastServerResponse)
    }
    catch {
      case NonFatal(e) =>
        logger.warn(s"problem sending email: ${e.getMessage}", e)
    }
    finally {
      t.close()
    }
  }
}
