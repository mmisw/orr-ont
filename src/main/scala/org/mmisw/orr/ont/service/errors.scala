package org.mmisw.orr.ont.service


abstract class OntError(val details: Seq[(String,String)]) extends Error(Map(details: _*).toString())

abstract class NoSuch(d: (String,String)*) extends OntError(d)

case class NoSuchOntUri(uri: String)
  extends NoSuch("iri" -> uri, "error" -> "No such ontology")

case class NoSuchOntVersion(uri: String, version: String)
  extends NoSuch("iri" -> uri, "version" -> version, "error" -> "No such ontology version")

case class NoSuchOntFormat(uri: String, version: String, format: String)
  extends NoSuch("iri" -> uri, "version" -> version, "format" -> format, "error" -> "No such ontology format")

case class NoSuchTermFormat(uri: String, format: String)
  extends NoSuch("iri" -> uri, "format" -> format, "error" -> "No such term format")

case class CannotQueryTerm(uri: String, error: String)
  extends Problem("iri" -> uri, "error" -> error)

case class NoSuchUser(userName: String)
  extends NoSuch("userName" -> userName, "error" -> "No such user")

case class NoSuchOrg(orgName: String)
  extends NoSuch("orgName" -> orgName, "error" -> "No such organization")

abstract class Invalid(d: (String,String)*) extends OntError(d)

case class InvalidIri(iri: String, error: String)
  extends Invalid("iri" -> iri, "error" -> error)

case class OntologyAlreadyRegistered(uri: String)
  extends Invalid("iri" -> uri, "error" -> "Ontology IRI already registered")

case class NotAnOrrVocabulary(uri: String, version: String)
  extends NoSuch("iri" -> uri, "version" -> version,
    "error" -> "Not an ORR vocabulary")

case class MissingClassUri(uri: String, version: String)
  extends Invalid("iri" -> uri, "version" -> version,
    "error" -> "Class IRI is required because vocabulary contains multiple classes")

case class NoSuchVocabClassUri(uri: String, version: String, classUri: String)
  extends NoSuch("iri" -> uri, "version" -> version, "classIri" → classUri,
    "error" -> "No such class in the given vocabulary")

case class TermUriAlreadyRegistered(uri: String, version: String, classUri: String, termUri: String)
  extends Invalid("iri" -> uri, "version" -> version, "classIri" -> classUri,
    "termIri" -> termUri,
    "error" -> "Term IRI already registered")

case class TermNameAlreadyRegistered(uri: String, version: String, classUri: String, termName: String)
  extends Invalid("iri" -> uri, "version" -> version, "classIri" -> classUri,
    "termName" -> termName,
    "error" -> "Term name already registered")

case class TermAttributesError(uri: String, version: String, classUri: String,
                               numProperties: Int,
                               numAttributes: Int)
  extends Invalid("iri" -> uri, "version" -> version, "classIri" -> classUri,
    "attributesExpected" → numProperties.toString,
    "attributesGiven" → numAttributes.toString,
    "error" -> "Mismatch in number of given attributes")

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

class Problem(d: (String,String)*) extends OntError(d)

case class CannotCreateFormat(uri: String, version: String, format: String, msg: String)
  extends Problem("iri" -> uri, "version" -> version, "format" -> format,
    "error" -> s"Cannot create requested ontology format: $msg")

case class CannotCreateDirectory(directory: String)
  extends Problem("directory" -> directory, "error" -> "Cannot create directory")

case class CannotInsertOntology(uri: String, error: String)
  extends Problem("iri" -> uri, "error" -> error)

case class CannotInsertOntologyVersion(uri: String, version: String, error: String)
  extends Problem("iri" -> uri, "version" -> version, "error" -> error)

case class CannotUpdateOntologyVersion(uri: String, version: String, error: String)
  extends Problem("iri" -> uri, "version" -> version, "error" -> error)

case class CannotDeleteOntologyVersion(uri: String, version: String, error: String)
  extends Problem("iri" -> uri, "version" -> version, "error" -> error)

case class CannotDeleteOntology(uri: String, error: String)
  extends Problem("iri" -> uri, "error" -> error)

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

object CannotLoadExternalOntology {
    def apply(uri: String, t: Throwable): Problem = {
        new Problem("iri" -> uri, "error" -> "cannot load external ontology",
            "detail" -> t.getMessage, "exceptionClassName" -> t.getClass.getName)
    }
}

case class DownloadRemoteServerError(remoteUrl: String, status: Int, body: String) extends Problem(
  "remoteUrl" → remoteUrl,
  "status"    → status.toString,
  "body"      → body
)
case class CannotRecognizeOntologyFormat(languagesAttempted: List[String])
  extends Problem(
    "error" -> "Cannot recognize ontology format",
    "languagesAttempted" → languagesAttempted.mkString(", "))

case class Bug(msg: String) extends Problem("error" -> msg)
