package org.mmisw.orr.ont.app

import com.novus.salat._
import com.novus.salat.global._
import com.typesafe.scalalogging.{StrictLogging => Logging}
import org.joda.time.DateTime
import org.mmisw.orr.ont.db.Organization
import org.mmisw.orr.ont.service._
import org.mmisw.orr.ont.{OrgResult, Setup, db}
import org.scalatra.Created

import scala.util.{Failure, Success, Try}


class OrgController(implicit setup: Setup) extends BaseController
    with Logging {

  get("/") {
    orgService.getOrgs() map getOrgJson
  }

  get("/:orgName") {
    val orgName = require(params, "orgName")
    val org = getOrg(orgName)
    getOrgJson(org)
  }

  post("/") {
    verifyIsAdminOrExtra()
    val map = body()

    logger.info(s"POST body = $map")
    val orgName = require(map, "orgName")
    val name = require(map, "name")
    val ontUri = getString(map, "ontUri")
    val members = getSeq(map, "members").toSet

    Created(createOrg(orgName, name, members, ontUri))
  }

  put("/:orgName") {
    val orgName = require(params, "orgName")
    val org = getOrg(orgName)

    verifyIsUserOrAdminOrExtra(org.members)

    val map = body()

    val nameOpt = getString(map, "name")
    val ontUriOpt = getString(map, "ontUri")

    val membersOpt = if (map.contains("members")) {
      val members = getSet(map, "members")
      members foreach verifyUser
      Some(members)
    }
    else None

    Try(orgService.updateOrg(orgName, membersOpt = membersOpt,
      name = nameOpt, ontUri = ontUriOpt,
      updated = Some(DateTime.now())
    )) match {
      case Success(res)  => res
      case Failure(exc)  => error500(exc)
    }
  }

  delete("/:orgName") {
    verifyIsAdminOrExtra()
    deleteOrg(require(params, "orgName"))
  }

  delete("/!/all") {
    verifyIsAdminOrExtra()
    orgService.deleteAll()
  }

  ///////////////////////////////////////////////////////////////////////////

  def createOrg(orgName: String, name: String,
                members: Set[String],
                ontUri: Option[String] = None) = {

    members foreach verifyUser

    Try(orgService.createOrg(orgName, name, members, ontUri)) match {
      case Success(res)                       => res
      case Failure(exc: OrgAlreadyRegistered) => error(409, exc.details)
      case Failure(exc: CannotInsertOrg)      => error500(exc)
      case Failure(exc)                       => error500(exc)
    }
  }

  def getOrg(orgName: String): db.Organization = {
    Try(orgService.getOrg(orgName)) match {
      case Success(res)            => res
      case Failure(exc: NoSuchOrg) => error(404, s"'$orgName' organization is not registered")
      case Failure(exc)            => error500(exc)
    }
  }

  def deleteOrg(orgName: String) = {
    Try(orgService.deleteOrg(orgName)) match {
      case Success(res)            => res
      case Failure(exc: NoSuchOrg) => error(404, s"'$orgName' organization is not registered")
      case Failure(exc)            => error500(exc)
    }
  }

  def getOrgJson(org: Organization) = {
    var res = OrgResult(
      orgName     = org.orgName,
      name        = Some(org.name),
      ontUri      = org.ontUri
    )
    if (checkIsUserOrAdminOrExtra(org.members)) {
      res = res.copy(
        registered  = Some(org.registered),
        updated     = org.updated,
        members     = Some(org.members)
      )
    }
    grater[OrgResult].toCompactJSON(res)

  }
}
