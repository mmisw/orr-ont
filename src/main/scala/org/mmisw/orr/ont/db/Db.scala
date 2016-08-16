package org.mmisw.orr.ont.db

import com.typesafe.scalalogging.{StrictLogging => Logging}
import com.mongodb.casbah.Imports._
import com.mongodb.ServerAddress
import com.novus.salat.dao.SalatDAO
import com.novus.salat.global._
import org.mmisw.orr.ont.Cfg
import org.mmisw.orr.ont.auth.userAuth


/**
 *
 */
class Db(mongoConfig: Cfg.Mongo) extends AnyRef with Logging {

  private[this] var mcOpt: Option[MongoClient] = None

  logger.info(s"mongoConfig = $mongoConfig")

  val host = mongoConfig.host
  val port = mongoConfig.port
  val db   = mongoConfig.db

  val serverAddress = new ServerAddress(host, port)

  private[this] val mongoClient: MongoClient = mongoConfig.user match {
    case Some(user) =>
      val pw = mongoConfig.pw.get
      logger.info(s"connecting to $host:$port/$db using credentials ...")
      val credential = MongoCredential.createMongoCRCredential(user, db, pw.toCharArray)
      MongoClient(serverAddress, List(credential))

    case None =>
      logger.info(s"connecting to $host:$port/$db with no credentials ...")
      MongoClient(serverAddress)
  }

  private[this] val mongoClientDb = mongoClient(db)

  private[this] val ontologiesColl  = mongoClientDb(mongoConfig.ontologies)
  private[this] val usersColl       = mongoClientDb(mongoConfig.users)
  private[this] val pwrColl         = mongoClientDb("pwr")
  private[this] val orgsColl        = mongoClientDb(mongoConfig.organizations)

  val ontDAO      = new SalatDAO[Ontology,     String](ontologiesColl) {}
  val usersDAO    = new SalatDAO[User,         String](usersColl) {}
  val pwrDAO      = new SalatDAO[PwReset,      String](pwrColl) {}
  val orgsDAO     = new SalatDAO[Organization, String](orgsColl) {}

  val authenticator = userAuth(usersDAO)

  mcOpt = Some(mongoClient)

  def destroy() {
    logger.info("Closing MongoClient ...")
    mcOpt foreach { _.close() }
  }
}
