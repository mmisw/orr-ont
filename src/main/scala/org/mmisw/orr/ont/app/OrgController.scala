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
    val authUser = verifyIsAdminOrExtra()
    val map = body()

    logger.info(s"POST body = $map")
    val orgName = require(map, "orgName")
    val name = require(map, "name")
    val url = getString(map, "url")
    val ontUri = getString(map, "ontUri")
    val members = getSeq(map, "members").toSet

    val org = Organization(
      orgName,
      name,
      url = url,
      ontUri = ontUri,
      members = members,
      registeredBy = Some(authUser.userName)
    )
    Created(createOrg(org))
  }

  put("/:orgName") {
    val orgName = require(params, "orgName")
    val org = getOrg(orgName)

    val authUser = verifyIsUserOrAdminOrExtra(org.members)

    val map = body()

    val nameOpt = getString(map, "name")
    val urlOpt  = getString(map, "url")
    val ontUriOpt = getString(map, "ontUri")

    val membersOpt = if (map.contains("members")) {
      val members = getSet(map, "members")
      members foreach verifyUser
      Some(members)
    }
    else None

    Try(orgService.updateOrg(orgName,
      membersOpt = membersOpt,
      name = nameOpt,
      url = urlOpt,
      ontUri = ontUriOpt,
      updated = Some(DateTime.now()),
      updatedBy = Some(authUser.userName)
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

  def createOrg(org: Organization) = {

    org.members foreach verifyUser

    Try(orgService.createOrg(org)) match {
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
      url         = org.url,
      ontUri      = org.ontUri
    )
    if (checkIsUserOrAdminOrExtra(org.members)) {
      res = res.copy(
        registered  = Some(org.registered),
        registeredBy= org.registeredBy,
        updated     = org.updated,
        updatedBy   = org.updatedBy,
        members     = Some(org.members)
      )
    }
    grater[OrgResult].toCompactJSON(res)
  }
}
