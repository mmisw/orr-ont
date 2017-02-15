package org.mmisw.orr.ont.service

import org.mmisw.orr.ont.Setup
import org.mmisw.orr.ont.db.User

abstract class BaseService(setup: Setup) {

  protected val orgsDAO     = setup.db.orgsDAO
  protected val usersDAO    = setup.db.usersDAO
  protected val ontDAO      = setup.db.ontDAO
  protected val userAuth    = setup.db.authenticator

  protected def verifyUser(userName: String): User = {
    usersDAO.findOneById(userName).getOrElse(throw NoSuchUser(userName))
  }

  protected def sendNotificationEmail(subject: String, msg: String): Unit = {
    new Thread(new Runnable {
      def run() {
        doIt()
      }
    }).start()

    def doIt(): Unit = {
      setup.cfg.notifications.recipientsFilename foreach { filename =>
        val source = try io.Source.fromFile(filename)
        catch {
          case ex: java.io.FileNotFoundException ⇒
            println(s"WARN: sendNotificationEmail: FileNotFoundException: ${ex.getMessage}")
            return
        }
        try {
          val emails = source.getLines.map(_.trim).filterNot { line =>
            line.isEmpty || line.startsWith("#")
          }
          if (emails.nonEmpty) {
            val body = msg +
              "\n\n" +
              s"(You have received this email because your address is included in $filename)"

            setup.emailer.sendEmail(emails.mkString(","), subject, body)
          }
        }
        catch {
          case exc:Exception => exc.printStackTrace()
        }
      }
    }
  }
}
