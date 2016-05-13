package org.mmisw.orr.ont.service


abstract class OntError(val details: Seq[(String,String)]) extends Error(Map(details: _*).toString())

abstract class NoSuch(d: (String,String)*) extends OntError(d)

case class NoSuchOntUri(uri: String)
  extends NoSuch("uri" -> uri, "error" -> "No such ontology")

case class NoSuchOntVersion(uri: String, version: String)
  extends NoSuch("uri" -> uri, "version" -> version, "error" -> "No such ontology version")

case class NoSuchOntFormat(uri: String, version: String, format: String)
  extends NoSuch("uri" -> uri, "version" -> version, "format" -> format, "error" -> "No such ontology format")

case class NoSuchUser(userName: String)
  extends NoSuch("userName" -> userName, "error" -> "No such user")

case class NoSuchOrg(orgName: String)
  extends NoSuch("orgName" -> orgName, "error" -> "No such organization")

abstract class Invalid(d: (String,String)*) extends OntError(d)

case class InvalidUri(uri: String, error: String)
  extends Invalid("uri" -> uri, "error" -> error)

case class OntologyAlreadyRegistered(uri: String)
  extends Invalid("uri" -> uri, "error" -> "Ontology URI already registered")

case class NotAMember(userName: String, orgName: String)
  extends Invalid("userName" -> userName, "orgName" -> orgName, "error" -> "User is not a member of the organization")

case class NotOntOwner(userName: String)
  extends Invalid("userName" -> userName, "error" -> "User is not the owner of the ontology")

case class InvalidUserName(userName: String)
  extends Invalid("userName" -> userName, "error" -> "Invalid userName")

case class InvalidOrgName(orgName: String)
  extends Invalid("orgName" -> orgName, "error" -> "Invalid orgName")

case class UserAlreadyRegistered(userName: String)
  extends Invalid("userName" -> userName, "error" -> "User already registered")

case class OrgAlreadyRegistered(orgName: String)
  extends Invalid("orgName" -> orgName, "error" -> "Organization already registered")

abstract class Problem(d: (String,String)*) extends OntError(d)

case class CannotCreateFormat(uri: String, version: String, format: String, msg: String)
  extends Problem("uri" -> uri, "version" -> version, "format" -> format,
    "error" -> s"Cannot create requested ontology format: $msg")

case class CannotCreateDirectory(directory: String)
  extends Problem("directory" -> directory, "error" -> "Cannot create directory")

case class CannotInsertOntology(uri: String, error: String)
  extends Problem("uri" -> uri, "error" -> error)

case class CannotInsertOntologyVersion(uri: String, version: String, error: String)
  extends Problem("uri" -> uri, "version" -> version, "error" -> error)

case class CannotUpdateOntologyVersion(uri: String, version: String, error: String)
  extends Problem("uri" -> uri, "version" -> version, "error" -> error)

case class CannotDeleteOntologyVersion(uri: String, version: String, error: String)
  extends Problem("uri" -> uri, "version" -> version, "error" -> error)

case class CannotDeleteOntology(uri: String, error: String)
  extends Problem("uri" -> uri, "error" -> error)

case class CannotInsertUser(userName: String, error: String)
  extends Problem("userName" -> userName, "error" -> error)

case class CannotUpdateUser(userName: String, error: String)
  extends Problem("userName" -> userName, "error" -> error)

case class CannotDeleteUser(userName: String, error: String)
  extends Problem("userName" -> userName, "error" -> error)

case class CannotInsertOrg(orgName: String, error: String)
  extends Problem("orgName" -> orgName, "error" -> error)

case class CannotUpdateOrg(orgName: String, error: String)
  extends Problem("orgName" -> orgName, "error" -> error)

case class CannotDeleteOrg(orgName: String, error: String)
  extends Problem("orgName" -> orgName, "error" -> error)

case class Bug(msg: String) extends Problem("error" -> msg)
