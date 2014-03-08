package org.mmisw.orr.ont

case class VersionInfo(
           uri:         String,
           name:        String,
           version:     String,
           date:        String,
           metadata:    Map[String,AnyRef] = Map())

case class Authority(
           shortName:   String,
           ontUri:      Option[String] = None,
           registered:  Option[String] = None)

case class AuthorityResult(
           shortName:   String,
           comment:     String)

case class OntologyResult(
           comment:     String,
           uri:         String,
           name:        Option[String] = None,
           version:     Option[String] = None)

case class UserResult(
           userName:    String,
           comment:     String)
