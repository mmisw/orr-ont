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
        val body = msg +
          "\n\n" +
          s"(you have received this email because your address is included in $filename)"

        try {
          val emails = io.Source.fromFile(filename).getLines.mkString(",")
          setup.emailer.sendEmail(emails, subject, body)
        }
        catch {
          case exc:Exception => exc.printStackTrace()
        }
      }
    }
  }
}
