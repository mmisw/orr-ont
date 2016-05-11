package org.mmisw.orr.ont

import org.joda.time.DateTime


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
            ontUri:      Option[String] = None,
            registered:  Option[DateTime] = None,
            updated:     Option[DateTime] = None,
            removed:     Option[DateTime] = None,
            members:     Option[Set[String]] = None)

case class OntologyRegistrationResult(
            uri:         String,
            version:     Option[String] = None,
            registered:  Option[DateTime] = None,
            updated:     Option[DateTime] = None,
            removed:     Option[DateTime] = None)

case class OntologySummaryResult(
            uri:            String,
            version:        String,
            name:           String,
            submitter:      Option[String] = None,
            orgName:        Option[String] = None,
            author:         Option[String] = None,
            status:         Option[String] = None,
            metadata:       Option[Map[String, AnyRef]] = None,
            ontologyType:   Option[String] = None,
            resourceType:   Option[String] = None,
            versions:       Option[List[String]] = None,
            format:         Option[String] = None
            )
