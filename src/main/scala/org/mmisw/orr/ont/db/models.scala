package org.mmisw.orr.ont.db

import org.joda.time.DateTime
import com.novus.salat.annotations._
import scala.util.{Success, Failure, Try}


case class Ontology(
            @Key("_id") uri: String,
            latestVersion:   String,
            orgName:         Option[String],
            owners:          List[String] = List.empty,
            versions:        Map[String, OntologyVersion] = Map.empty)

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
            ontUri:                Option[String] = None,
            registered:            DateTime = DateTime.now(),
            updated:               Option[DateTime] = None)

case class Organization(
            @Key("_id") orgName:   String,
            name:                  String,
            ontUri:                Option[String] = None,
            members:               List[String] = List.empty,
            registered:            DateTime = DateTime.now(),
            updated:               Option[DateTime] = None)
