package org.mmisw.orr.ont.service

import java.io.File
import java.util.{Timer, TimerTask}

import com.typesafe.scalalogging.{StrictLogging ⇒ Logging}
import org.mmisw.orr.ont.util.IEmailer

import scala.collection.mutable.ListBuffer
import scala.util.control.NonFatal

trait INotifier {
  def sendNotificationEmail(subject: String, msg: String): Unit
  def destroy(): Unit
}

private case class Item(subject: String, msg: String)

class Notifier(configDir: File, emailer: IEmailer) extends INotifier with Logging {

  def sendNotificationEmail(subject: String, msg: String): Unit = {
    logger.debug(s"adding to queue item subject=$subject")
    queue.synchronized {
      queue += Item(subject, msg)
      logger.debug(s"queue size now=${queue.size}")
    }
  }

  def destroy(): Unit = {
    dispatcher.cancel()
  }

  private val SendPeriod: Long = 5*60*1000L // 5 minutes
  private val CheckPeriod: Long = 60*1000L // 1 min
  private val queue = new ListBuffer[Item]()
  private var latestSendTime: Long = 0

  private val timer = new Timer()
  private val dispatcher = new TimerTask {
    def run(): Unit = {
      val itemsOpt = queue.synchronized {
        logger.debug(s"checking for queue items to dispatch: ${queue.size}")
        val currTime = System.currentTimeMillis
        if (queue.nonEmpty && (currTime - latestSendTime) >= SendPeriod) {
          latestSendTime = currTime
          val items = queue.toList
          queue.clear()
          Some(items)
        }
        else None
      }
      itemsOpt foreach dispatchItems
    }
  }

  timer.schedule(dispatcher, CheckPeriod, CheckPeriod)

  private def dispatchItems(items: List[Item]): Unit = {
    logger.debug(s"dispatchItems: ${items.size}")
    val file = new File(configDir, "notifyemails")
    val emails = getEmails(file)
    logger.debug(s"getEmails: ${emails.size} (file: $file)")
    if (emails.nonEmpty) {
      val (subject, msg) = if (items.size == 1) {
        val item = items.head
        (item.subject, item.msg)
      }
      else {
        ("Notifications", items.map(_.msg).mkString("\n\n"))
      }
      logger.debug(s"sending email: subject='$subject' to ${emails.size} emails")
      emailer.sendEmail(emails.mkString(","), subject, text =
        s"""$msg
           |
           |(You have received this email because your address is included in ${file.getAbsolutePath}")"
           |""".stripMargin
      )
    }
    else {
      logger.debug(s"no emails to send notifications")
    }
  }

  private def getEmails(file: File): Seq[String] = {
    try {
      val source = io.Source.fromFile(file)
      val emails = source.getLines
        .map(_.trim)
        .filterNot(line => line.isEmpty || line.startsWith("#"))
        .toSeq
      source.close()
      emails
    }
    catch {
      case _:java.io.FileNotFoundException ⇒
        logger.warn(s"sendNotificationEmail: file not found: $file")
        Seq.empty
      case NonFatal(e) ⇒
        logger.error("error getting emails", e)
        Seq.empty
    }
  }
}
