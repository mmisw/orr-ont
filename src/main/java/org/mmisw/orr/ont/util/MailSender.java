package org.mmisw.orr.ont.util;

import java.util.Date;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.PasswordAuthentication;
import javax.mail.Authenticator;

import com.sun.mail.smtp.SMTPTransport;

// copied (almost) verbatim from previous mmiorr: org.mmisw.orrclient.core.util.MailSender
/**
 * Helper to send email.
 */
public class MailSender {

  public static void sendMessage(
      final String mailer   ,
      final String mailhost ,
      final String mailport ,
      final String prot     ,
      final String user, final String password,
      boolean debug,
      String from, String to, String replyTo,
      String subject,
      String text
  ) throws Exception {


    String cc = null, bcc = null;

    Properties props = System.getProperties();

    props.put("mail." + prot + ".host", mailhost);
    props.put("mail." + prot + ".auth", "true");
    if ( mailport != null ) {
      props.put("mail." + prot + ".port", mailport);
    }

    Session session = Session.getInstance(props,
        new Authenticator() {
          protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(user, password);
          }
        });

    if (debug) {
      session.setDebug(true);
    }

    Message msg = new MimeMessage(session);
    msg.setFrom(new InternetAddress(from));

    msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, false));
    if (cc != null) {
      msg.setRecipients(Message.RecipientType.CC, InternetAddress.parse(cc, false));
    }
    if (bcc != null) {
      msg.setRecipients(Message.RecipientType.BCC, InternetAddress.parse(bcc, false));
    }

    if ( replyTo != null ) {
      msg.setReplyTo(InternetAddress.parse(replyTo, false));
    }

    msg.setSubject(subject);

    msg.setText(text);

    msg.setHeader("X-Mailer", mailer);
    msg.setSentDate(new Date());


    SMTPTransport t = (SMTPTransport) session.getTransport(prot);
    try {
      t.connect(mailhost, user, password);
      t.sendMessage(msg, msg.getAllRecipients());
    }
    finally {
      System.out.println("Response: " + t.getLastServerResponse());
      t.close();
    }
  }
}
