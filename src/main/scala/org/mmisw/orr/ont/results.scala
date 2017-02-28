package org.mmisw.orr.ont

import org.joda.time.DateTime
import org.json4s.JValue


case class UserResult(
            userName:    String,
            firstName:   Option[String] = None,
            lastName:    Option[String] = None,
            email:       Option[String] = None,
            ontUri:      Option[String] = None,
            phone:       Option[String] = None,
            registered:  Option[DateTime] = None,
            updated:     Option[DateTime] = None,
            removed:     Option[DateTime] = None,
            role:        Option[String] = None,  // todo
            organizations: Option[List[OrgResult]] = None
            )

case class AuthToken(token: String)

case class UsernameReminderResult(
            email:       String,
            message:     Option[String] = None)

case class PasswordResetResult(
            userName:    String,
            email:       Option[String] = None,
            message:     Option[String] = None)

case class OrgResult(
            orgName:     String,
            name:        Option[String] = None,
            url:         Option[String] = None,
            ontUri:      Option[String] = None,
            members:     Option[Set[String]] = None,
            registered:  Option[DateTime] = None,
            registeredBy:Option[String] = None,
            updated:     Option[DateTime] = None,
            updatedBy:   Option[String] = None,
            removed:     Option[DateTime] = None,
            removedBy:   Option[String] = None,
            onts:        Option[List[OntologySummaryResult]] = None
            )

case class OntologyRegistrationResult(
            uri:         String,
            version:     Option[String] = None,
            log:         Option[String] = None,
            visibility:  Option[String] = None,
            status:      Option[String] = None,
            registered:  Option[DateTime] = None,
            updated:     Option[DateTime] = None,
            removed:     Option[DateTime] = None
            )

case class OntologySummaryResult(
            uri:            String,
            version:        String,
            name:           String,
            submitter:      Option[String] = None,
            ownerName:      Option[String] = None,  // | <orgName> | ~<userName>
            author:         Option[String] = None,
            status:         Option[String] = None,
            metadata:       Option[Map[String, AnyRef]] = None,
            ontologyType:   Option[String] = None,
            resourceType:   Option[String] = None,
            versions:       Option[List[String]] = None,
            format:         Option[String] = None,
            log:            Option[String] = None,
            visibility:     Option[String] = None
            )

case class TermRegistrationResult(
            vocUri:    String,
            classUri:  String,
            termName:  Option[String],
            termUri:   Option[String],
            attributes: List[JValue]
)

case class OntologySubjectsResult(
            uri:            String,
            version:        String,
            name:           String,
            subjects:       Map[String, Map[String, AnyRef]],
            metadata:       Option[Map[String, AnyRef]] = None
            )

case class ExternalOntologySubjectsResult(
            uri:            String,
            subjects:       Map[String, Map[String, AnyRef]],
            metadata:       Option[Map[String, AnyRef]] = None
            )

case class TripleStoreResult(
            uri:    Option[String] = None,
            size:   Option[Long] = None,
            msg:    Option[String] = None
            )
