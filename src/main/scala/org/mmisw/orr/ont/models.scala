package org.mmisw.orr.ont

case class VersionInfo(
           uri:         String,
           name:        String,
           version:     String,
           date:        String,
           metadata:    Map[String,AnyRef] = Map())

case class AuthorityResult(
           comment:     String,
           shortName:   String)

case class OntologyResult(
           comment:     String,
           uri:         String,
           name:        Option[String] = None,
           version:     Option[String] = None)

case class UserResult(
           userName:    String,
           comment:     String)
