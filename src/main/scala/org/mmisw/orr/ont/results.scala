package org.mmisw.orr.ont

import org.joda.time.DateTime


case class UserResult(
            userName:    String,
            registered:  Option[DateTime] = None,
            updated:     Option[DateTime] = None,
            removed:     Option[DateTime] = None)

case class AuthorityResult(
            authName:    String,
            registered:  Option[DateTime] = None,
            updated:     Option[DateTime] = None,
            removed:     Option[DateTime] = None)

case class VersionInfo(
           uri:         String,
           name:        String,
           version:     String,
           date:        String,
           metadata:    Map[String,AnyRef] = Map())

case class OntologyResult(
           comment:     String,
           uri:         String,
           name:        Option[String] = None,
           version:     Option[String] = None)
