package org.mmisw.orr.ont.service

import com.mongodb.casbah.Imports._
import com.typesafe.scalalogging.slf4j.Logging
import org.joda.time.DateTime
import org.mmisw.orr.ont.db.Organization
import org.mmisw.orr.ont.{PendOrgResult, Setup, OrgResult}

import scala.util.{Failure, Success, Try}


/**
 * Org service
 */
class OrgService(implicit setup: Setup) extends BaseService(setup) with Logging {

  /**
   * Gets the orgs satisfying the given query.
   * @param query  Query
   * @return       iterator
   */
  def getOrgs(query: MongoDBObject): Iterator[PendOrgResult] = {
    orgsDAO.find(query) map { org =>
        PendOrgResult(org.orgName, org.name, org.ontUri, Some(org.registered))
    }
  }

  def existsOrg(orgName: String): Boolean = orgsDAO.findOneById(orgName).isDefined

  def getOrg(orgName: String): Organization = orgsDAO.findOneById(orgName).getOrElse(throw NoSuchOrg(orgName))

  def getOrgOpt(orgName: String): Option[Organization] = orgsDAO.findOneById(orgName)

  /**
   * Creates a new org.
   */
  def createOrg(orgName: String, name: String,
                members: Set[String] = Set.empty,
                ontUri: Option[String] = None) = {

    orgsDAO.findOneById(orgName) match {
      case None =>
        validateOrgName(orgName)

        val org = Organization(orgName, name, ontUri, members)

        Try(orgsDAO.insert(org, WriteConcern.Safe)) match {
          case Success(_) =>
            OrgResult(orgName, registered = Some(org.registered))

          case Failure(exc) => throw CannotInsertOrg(orgName, exc.getMessage)
              // perhaps duplicate key in concurrent registration
        }

      case Some(_) => throw OrgAlreadyRegistered(orgName)
    }
  }

  /**
   * Updates an org.
   */
  def updateOrg(orgName: String,
                membersOpt: Option[Set[String]] = None,
                map: Map[String,String] = Map.empty,
                registered: Option[DateTime] = None,
                updated: Option[DateTime] = None) = {

    var update = getOrg(orgName)

    membersOpt foreach { members =>
      members foreach verifyUser
      update = update.copy(members = members)
    }
    if (map.contains("name")) {
      update = update.copy(name = map.get("name").get)
    }
    if (map.contains("ontUri")) {
      update = update.copy(ontUri = map.get("ontUri"))
    }

    registered foreach {d => update = update.copy(registered = d)}
    updated    foreach {d => update = update.copy(updated = Some(d))}

    Try(orgsDAO.update(MongoDBObject("_id" -> orgName), update, false, false, WriteConcern.Safe)) match {
      case Success(result) =>
        OrgResult(orgName, updated = update.updated)

      case Failure(exc)  => throw CannotUpdateOrg(orgName, exc.getMessage)
    }
  }

  /**
   * Deletes an org.
   */
  def deleteOrg(orgName: String) = {
    val org = getOrg(orgName)

    Try(orgsDAO.remove(org, WriteConcern.Safe)) match {
      case Success(result) =>
        OrgResult(orgName, removed = Some(DateTime.now())) //TODO

      case Failure(exc)  => throw CannotDeleteOrg(orgName, exc.getMessage)
    }
  }

  /**
   * Deletes the whole orgs collection
   */
  def deleteAll() = orgsDAO.remove(MongoDBObject())

  ///////////////////////////////////////////////////////////////////////////

  private def validateOrgName(orgName: String) {
    val re = """(\w|-)+""".r
    re.findFirstIn(orgName).headOption.getOrElse(InvalidOrgName(orgName))
  }

}
