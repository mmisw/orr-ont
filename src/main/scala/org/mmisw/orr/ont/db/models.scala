package org.mmisw.orr.ont.db

import org.joda.time.DateTime
import com.novus.salat.annotations._
import scala.util.{Success, Failure, Try}


case class Ontology(
            @Key("_id") uri: String,
            orgName:         Option[String],
            versions:        Map[String, OntologyVersion] = Map.empty) {

  lazy val sortedVersionKeys = versions.keys.toList.sorted(Ordering[String].reverse)
}

case class OntologyVersion(
            name:            String,
            userName:        String,
            format:          String,
            date:            DateTime)

case class User(
            @Key("_id") userName:  String,
            firstName:             String,
            lastName:              String,
            password:              String,
            email:                 Option[String] = None, // TODO make email required
            ontUri:                Option[String] = None,
            phone:                 Option[String] = None,
            registered:            DateTime = DateTime.now(),
            updated:               Option[DateTime] = None)

case class Organization(
            @Key("_id") orgName:   String,
            name:                  String,
            ontUri:                Option[String] = None,
            members:               List[String] = List.empty,
            registered:            DateTime = DateTime.now(),
            updated:               Option[DateTime] = None)
