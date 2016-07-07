package org.mmisw.orr.ont.db

import org.joda.time.DateTime
import com.novus.salat.annotations._


case class Ontology(
            @Key("_id") uri: String,
            ownerName:       String,  // <orgName> | ~<userName>
            versions:        Map[String, OntologyVersion] = Map.empty) {

  lazy val sortedVersionKeys = versions.keys.toList.sorted(Ordering[String].reverse)

  lazy val latestVersion: Option[OntologyVersion] = sortedVersionKeys.headOption.map(latest => versions(latest))
}

object OntVisibility extends Enumeration {
  val owner, public = Value
}

case class OntologyVersion(
            name:            String,
            userName:        String, // submitter
            format:          String,
            date:            DateTime,
            visibility:      Option[OntVisibility.Value] = Some(OntVisibility.owner), // TODO No default, nor optional
            status:          Option[String] = None,
            author:          Option[String] = None,    // content creator
            metadata:        List[Map[String, AnyRef]] = List.empty,
            ontologyType:    Option[String] = None,
            resourceType:    Option[String] = None
            )

case class User(
            @Key("_id") userName:  String,
            firstName:             String,
            lastName:              String,
            password:              String,
            email:                 String,
            ontUri:                Option[String] = None,
            phone:                 Option[String] = None,
            registered:            DateTime = DateTime.now(),
            updated:               Option[DateTime] = None)

case class PwReset(
            @Key("_id") token:     String,
            userName:              String,
            expiration:            DateTime)

case class Organization(
            @Key("_id") orgName:   String,
            name:                  String,
            url:                   Option[String] = None,
            ontUri:                Option[String] = None,
            members:               Set[String] = Set.empty,
            registered:            DateTime = DateTime.now(),
            registeredBy:          Option[String] = None,    // userName
            updated:               Option[DateTime] = None,
            updatedBy:             Option[String] = None     // userName
           )
