package org.mmisw.orr.ont

case class Ontology(uri:         String,
                    name:        String,
                    version:     Option[String],
                    users:       Option[List[UserRef]] = None,
                    versions:    Option[List[String]] = None)

case class UserRef(userName:    String,
                   perms:       String)

case class VersionInfo(uri:         String,
                       name:        String,
                       version:  String,
                       date:     String,
                       metadata: Map[String,AnyRef] = Map())

case class User(userName:    String,
                firstName:   String,
                lastName:    String,
                registered:  Option[String] = None,
                password:    Option[String] = None)

case class Authority(
                      shortName:   String,
                      ontUri:      Option[String] = None,
                      registered:  Option[String] = None)

case class AuthorityResult(
                            shortName:  String,
                            comment:   String)

case class OntologyResult(
                  uri:      String,
                  version:  Option[String] = None,
                  comment:  String)

case class UserResult(
                  userName:  String,
                  comment:   String)
