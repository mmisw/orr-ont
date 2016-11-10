package org.mmisw.orr.ont.service

import com.mongodb.casbah.Imports._
import com.typesafe.scalalogging.{StrictLogging => Logging}
import org.joda.time.DateTime
import org.mmisw.orr.ont.db.Organization
import org.mmisw.orr.ont.{OrgResult, Setup}

import scala.util.{Failure, Success, Try}


class OrgService(implicit setup: Setup) extends BaseService(setup) with Logging {

  def getOrgs(query: MongoDBObject = MongoDBObject()): Iterator[Organization] = {
    orgsDAO.find(query)
  }

  def existsOrg(orgName: String): Boolean = orgsDAO.findOneById(orgName).isDefined

  def getOrg(orgName: String): Organization = orgsDAO.findOneById(orgName).getOrElse(throw NoSuchOrg(orgName))

  def getOrgOpt(orgName: String): Option[Organization] = orgsDAO.findOneById(orgName)

  def getUserOrganizations(userName: String): Option[List[OrgResult]] = {
    val query = MongoDBObject(
      "members" -> MongoDBObject("$elemMatch" -> MongoDBObject("$eq" -> userName)))
    val it: Iterator[Organization] = orgsDAO.find(query)
    val result = if (it.nonEmpty) Some(it.map(o => OrgResult(o.orgName, name = Option(o.name))).toList)
    else None
    println(s"getUserOrganizations: userName=$userName query=$query -> $result")
    result
  }

  def getUserOrganizationNames(userName: String): List[String] = {
    getUserOrganizations(userName) match {
      case Some(userOrgs) => userOrgs map (_.orgName)
      case None           => Nil
    }
  }

  def createOrg(org: Organization) = {
    val orgName = org.orgName
    orgsDAO.findOneById(orgName) match {
      case None =>
        validateOrgName(orgName)

        Try(orgsDAO.insert(org, WriteConcern.Safe)) match {
          case Success(_) =>
            sendNotificationEmail("New organization registered",
              s"""
                 |The following organization has been registered:
                 |
                 | Short name: $orgName
                 | Name: ${org.name}
                 | Members: ${org.members.mkString(", ")}
                 | Registered: ${org.registered}
                 | By: ${org.registeredBy.getOrElse("(undefined)")}
              """.stripMargin
            )
            OrgResult(orgName,
              url = org.url,
              ontUri = org.ontUri,
              registered = Some(org.registered),
              registeredBy = org.registeredBy)

          case Failure(exc) => throw CannotInsertOrg(orgName, exc.getMessage)
              // perhaps duplicate key in concurrent registration
        }

      case Some(_) => throw OrgAlreadyRegistered(orgName)
    }
  }

  def updateOrg(orgName: String,
                membersOpt: Option[Set[String]] = None,
                name: Option[String] = None,
                url: Option[String] = None,
                ontUri: Option[String] = None,
                registered: Option[DateTime] = None,
                updated: Option[DateTime] = None,
                updatedBy: Option[String] = None
               ): OrgResult = {

    var update = getOrg(orgName)

    membersOpt foreach { members =>
      members foreach verifyUser
      update = update.copy(members = members)
    }

    name foreach {d => update = update.copy(name = d)}
    url foreach {d => update = update.copy(url = Some(d))}
    ontUri foreach {d => update = update.copy(ontUri = Some(d))}

    registered foreach {d => update = update.copy(registered = d)}
    updated    foreach {d =>
      update = update.copy(updated = Some(d))
      updatedBy foreach {by => update = update.copy(updatedBy = updatedBy)}
    }

    Try(orgsDAO.update(MongoDBObject("_id" -> orgName), update, upsert = false, multi = false, WriteConcern.Safe)) match {
      case Success(result) =>
        sendNotificationEmail("Organization updated",
          s"""
             |The following organization has been updated:
             |
             | Short name: $orgName
             | Name: ${update.name}
             | Members: ${update.members.mkString(", ")}
             | Updated: ${update.updated.getOrElse("(undefined)")}
             | By: ${update.updatedBy.getOrElse("(undefined)")}
              """.stripMargin
        )
        OrgResult(orgName,
          url = update.url,
          ontUri = update.ontUri,
          name = Option(update.name),
          members = Option(update.members),
          updated = update.updated,
          updatedBy = updatedBy
        )

      case Failure(exc)  => throw CannotUpdateOrg(orgName, exc.getMessage)
    }
  }

  def deleteOrg(orgName: String): OrgResult = {
    getOrg(orgName)
    Try(orgsDAO.removeById(orgName, WriteConcern.Safe)) match {
      case Success(result) =>
        OrgResult(orgName, removed = Some(DateTime.now())) //TODO

      case Failure(exc)  => throw CannotDeleteOrg(orgName, exc.getMessage)
    }
  }

  def deleteAll() = orgsDAO.remove(MongoDBObject())

  ///////////////////////////////////////////////////////////////////////////

  private def validateOrgName(orgName: String) {
    val re = """(\w|-)+""".r
    re.findFirstIn(orgName).getOrElse(InvalidOrgName(orgName))
  }
}
