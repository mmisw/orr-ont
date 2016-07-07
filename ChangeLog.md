## change log ##

* 2016-07-06: 3.0.4-alpha
  - add URL attribute to organization model
  
* 2016-07-05: 3.0.4-alpha
  - template.orront.conf: use env var with name ORR_ONT_BASE_DIR for consistency with other uses
  - createOntologyVersion: if name not given, take the name from previous version
  
* 2016-07-05: 3.0.3-alpha
  - for author attribute in ontology submission, if not in given contact_name or from ontology metadata: 
  	- POST /ont: get author attribute from owner (org's name or user's first+last name)
  	- PUT /ont:  get author attribute from previous version
  - expand list of accepted formats for upload to: "rdf", "n3", "nt", "ttl", "rj", "jsonld", "owx"
  
* 2016-06-26: 3.0.2-alpha
  - to facilitate docker image creation a bit (this will be improved later, perhaps all
    scripted from sbt), add bin/dockerize, and use Dockerfile.in as template to generate actual version.
    	
    	sbt7 package
    	bin/dockerize.sh 3.0.2-alpha
    	docker push mmisw/orr-ont
  
  - accept "_guess" as format in ontFileLoader.loadOntModel.
    The strategy is simple (try some formats in sequence) and could be improved.
  
  - fix #19 "uploading of OWX ontology with unresolvable Imports fails".
    TODO: remove deprecated call with some newer version of the OWL API
  
  - fix #18 "gcoos Parameter.owl".
	Actually the file extension is not a issue at all. But the file was uploaded with the "OWL/XML" 
	format option but the "RDF/XML" would have been more appropriate. 
	This is not explained in the orr-portal; in particular the ".owl" extension for files that are 
	actually in RDF/XML if often used, so, at time of uploading, users may tend to pick the "OWL/XML" option.
	The fix here was just to add "owl" to ontUtil.storedFormats, which is used as a basis 
	for conversion by OntService.getOntologyFile when resolving a new format not yet cached. 


* 2016-06-23: 3.0.2-alpha
  - resolve #10 "capture visibility attribute for ontologies" 
    Only "owner" and "public" now handled.
  - capture registeredBy and updatedBy in Organization model
  
* 2016-06-22: 3.0.1-alpha
  - docker container: deploy as ont3.war
  - bug fix is some DAO delete calls: use removeById(<the-id>)  (instead of remove(<some-query-object>)) 

* 2016-06-21: 
  - v. 3.0.0-alpha
  
* 2016-06-18: 0.3.2:
  - delete ontology entry when un-registering its sole version
  
* 2016-06-16: 0.3.2:
  - \#14 "dispatch term request"
    - based on SPARQL query for the properties of the term.
    - added agraph.sparqlEndpoint config property to capture the complete URL of the SPARQL endpoint. 
      Although most of the associated config pieces are already captured in other properties, the 
      associated request without protocol (eg. http:) causes AG to respond with a 301 redirect.
      Although Dispatch could be configured to follow redirects, decided to captured the final location
      to avoid the extra round trip.
    - As in previous system, the term query will be either a "select" or a "construct" depending on the desired format. 
      This format can be specified via explicit "format" parameter:
        `http 'http://mmisw.org/ont/api/v0/ont?uri=https://mmisw.org/ont/test/avoc/term1&format=json'`
      or via accept header:
        `http 'http://mmisw.org/ont/api/v0/ont?uri=https://mmisw.org/ont/test/avoc/term1' Accept:text/rdf+n3`
      There's an ad hoc mapping from the format to corresponding mime-type for the request
    - GET /ont: parameters considered and dispatched with the following precedence:
      - "uri": tries ontology first, then term. In this case, 404 will never occur as the term attempt will always 
        succeed (unless there's an unexpected exception) because the sparql query can have an "empty" response   
      - "ouri": only tries ontology, with 404 response if not found 
      - "turi": performs term query, no 404 can occur as already explained
    - SelfHostedController also adjusted ... but I'm noticing what seems to be a Scala compiler bug :(
    
* 2016-06-11: 0.3.2:
  - \#10: "capture visibility attribute for ontologies"
    - for convenience at the moment, optional in OntologyVersion model (but to be required, and with no default value)
  	- aquaImporter: for convenience, set to "public" as the most typical case while assuming
      any particular adjustments are to be done externally, but could be done during import
      (based on status) if deemed necessary.
  
* 2016-06-01: 0.3.1:
  - for "!md" response, include 'versions' even when a particular version is requested  
  
* 2016-05-31: 0.3.1:
  - SelfHostedOntController: allow to dispatch sparql UI 
  
* 2016-05-20: 0.3.1:
  - At ontology registration, if 'author' not explicitly given (which is the case only 
    via contact_name from AquaImporter), extract it from the first defined metadata property
    in this order:
    	  OmvMmi.hasContentCreator, 
          Omv.hasCreator, 
          DCTerms.creator, 
          DC_11.creator, 
          DC_10.creator 
    
* 2016-05-19: 0.3.1:
  - add `GET /ont/sbjs/external` (for now mainly to support loading of external ontology in m2r editing)
    TODO some refact to support similar functionality for registration of external ontology
  - ontUtil.extractAttributes: do not add possible null to the list
    (issue exposed while extracting subjects for http://semanticscience.org/ontology/sio.owl) 
    
* 2016-05-18: 0.3.1:
  - add `GET /ont/sbjs` to retrieve subjects in a given ontology
  
* 2016-05-16: 0.3.1:
  - initial version of m2r format for ORR mapping ontologies
  
* 2016-05-12: 0.3.1:
  - Ontology model now with required ownerName entry instead of optional orgName.
    The syntax for this string: `ownerName ::= <orgName> | ~<userName>`.
    So now we accept submissions to be owned by the submitting user when no
    organization is specified in the POST /ont request.
    
  - for OntologyVersion.author get the first value obtained in this order:
    - contact_name from AquaImporter
    - omvmmi.hasContentCreator
    - omv.hasCreator
  - use similar writeModel logic also in format conversion
  
* 2016-05-11: 0.3.1:
  - use official docker tomcat image as a basis for our orr-ont image
  
* 2016-05-09: 0.3.1:
  - add getParam/requireParam helpers to retrieve request parameters from either 
    the body or the regular params. Used in OntController to facilitate requests 
    with either mechanism (note that regular "form" parameters should be used 
    when uploading a file --multipart/form-data)
  - allow an admin to update/delete ontology (even if not a member of corresponding organization) 
  
* 2016-05-04: 0.3.1:
  - v2r: metadata is now an object indexed by the predicate URI:
  
      ```      
      "metadata": {
          "http://purl.org/dc/elements/1.1/description": [
              "some description (dc:description)"
          ],
          ...
      }
      ```
      
      It's only in the mongo database that the metadata is stored as an array
      
      ```
		"metadata" : [ 
		  {
			  "uri" : "http://purl.org/dc/elements/1.1/description",
			  "values" : [ 
				  "some description (dc:description)"
			  ]
		  },
		  ...
		]
      ```
      (this because characters like dot cannot be used as field names)
      
* 2016-05-03: 0.3.1:
  - v2r: set empty prefix for namespace associated with ontology uri in the jena model
    (this way the resulting serializations (n3, rdf/xml..) look nicer)
  - PUT /ont: 
    - accept optional 'metadata' parameter to create new version only with changes in metadata.
    - creation of new version: requires specification of new contents. Options:
      - file upload (in same request, or previously uploaded file) for the new full ontology contents
      - full contents in embedded 'contents' parameter
      - 'metadata' parameter for new version with given metadata on top of a base version

  - accept embedded contents in ontology submission
  
* 2016-05-01: 0.3.1:
  - report metadata in OntologySummaryResult but only for specific URI (not for list of ontologies)
  - capture all ontology metadata in OntologyVersion so it gets saved in the db
  - minor refactor: writeOntologyFile now returns all the ontology metadata, while the interim
    extractSomeProps uses that metadata to extract the special values that are going in the 
    OntologyVersion object (these special values will be removed later on)
  
* 2016-04-30: 0.3.1:
  - v2r: specify URI at first level (namespace is always uri + "/")
  - v2r: add metadata
  
* 2016-04-22-27: 0.3.1:
  - report format in OntologySummaryResult as a possible mechanism to help UI decide on corresponding dispatch
    (eg., for v2r at this moment)
  - introduce ontUtil.storedFormats intended to capture all formats used to store ontology serializations
    TODO This is basically working but needs overall unification
  - SelfHostedOntController.portalDispatch: adjustments in contentType so dispatch of orr-portal also
    works well while exercising it with `sbt container:start`
  
* 2016-04-22-25,26: 0.3.1:
  - v2r: vocab terms is now List[Term], with term's attributes as List[JValue] to allow,
    per property, to either associate an individual value or an array of such values.
  - uploaded "owx" (OWL/XML) file is now stored in that format as the original.
  - general adjustments related with OWL/XML: "owx" is the associated request format and also
    the extension of stored file. Mime type is "application/owl+xml". Extension and mime type 
    according to https://www.w3.org/TR/owl-xml-serialization/
  - uploaded "v2r" file is stored in that format as the original in final 
    version directory destination (that is not its conversion to rdf as the original)
  - initial support for .v2r format in upload operation
  - make namespace optional in V2RModel
  - preliminaries for v2r format

* 2016-04-22-24: 0.3.1:
  - include role (only when "admin") in GET /user/:userName response for admin or same user
  - written fully-hosted file: include xml:base and remove unused namespace prefixes
  - files involved fully-hosted registration starting from upload:
      ```
      .../uploads/<username>/1461535308734.rdf
      .../onts/http:||example.net|ont|<org>|<short-name>|<version>|file_orig.rdf
      .../onts/http:||example.net|ont|<org>|<short-name>|<version>|file.rdf
      ```  
  - for "fully-hosted" registration, new `originalUri` parameter to perform "transfer" 
    of associated namespace to given `uri`.
  - first consider owl:Ontology for possibleOntologyUris. If none, then consider the other options.
  - also consider skos:Collection for possibleOntologyUris in uploaded file
  - add tests for `POST /ts/_init`
  
* 2016-04-21: 0.3.1:
  - new bin/docker-run to facilitate launching on the whole ORR system. README updated.
  - triple store: new loadUriFromLocal method to load the contents of an ontology
    in the triple store. Assumes the AG and orr-ont servers share the 
    `/opt/orr-ont-base-directory` directory, which is the case under the defined Docker scheme.
  - use `--volumes-from orr-ont` for agraph container to share `/opt/orr-ont-base-directory`
    so we can have AG to load files directly from the share

  - triple store initialization: 
    - if missing, create AG repository (with name agraph.repoName in given configuration) 
    - if missing, create AG anonymous user (and give it read access to the agraph.repoName)
    - add `POST /ts/_init` to explicit execute triple store initialization (which will help 
      in case the call at orr-ont init time fails possibly due to AG server not running)

* 2016-04-20: 0.3.1:
  - allow {template.}orront.conf overrides via environment variables
  
* 2016-04-19: 0.3.1:
  - upload file: report metadata for each possible ontology URI:

    ```json
    {
        "filename": "1461089333462.rdf",
        "format": "rdf",
        "possibleOntologyUris": {
            "http://example.org/ont1": {
                "explanations": [
                    "Value of xml:base attribute"
                ],
                "metadata": {
                    "<propURI>": [
                        "<propValue>", ...
                    ],
                }
            }
        },
        "userName": "carueda"
    }
    ```
    
    Possible ontology names now handled by the client

* 2016-04-18: 0.3.1:
  - upload file: use the following properties for possible ontology names:
    RDFS.label, Omv.name, DCTerms.title, DC_11.title, DC_10.title
  
* 2016-04-16: 0.3.1:
  - also handle previously uploaded file in PUT /ont
  
* 2016-04-11/12: 0.3.1:
  - initial version of POST /ont/upload to perform first step toward registering an ontology
    from an uploaded file.
    This operation returns the location of such local file as well as ontology basic 
    information/metadata for the client (orr-portal) to continue registration sequence. 
    The uploaded file is stored under baseDirectory/uploads/.
    Various supporting elements copied from previous mmiorr project; TODO: clean up when time permits.
    These elements include support for accepting file in OWL/XML (using OWL API library)
    
  - POST /ont now also accepts info about previously uploaded file to perform the registration:
    - if "file" is given, then uploaded file is included in the request (as in previous version)
    - else if "uploadedFilename", then gets the file from previously uploaded file
    
  - GET /user/username: include the organizations the requested user is member of
    
  - actually use a local copy of original http://purl.org/wmo/seaice/iceOfLandOrigin (which currently 
    redirects to http://ssiii.googlecode.com/svn/trunk/ontology/ice-of-land-origin.owl) as an OWL/XML
    file for testing purposes (I was initially using one with some changes made by ORR). 
  
* 2016-04-10: 0.3.1:
  - include 'extra' list in verification of admin user
    - use verifyIsAdminOrExtra() instead of verifyIsAuthenticatedUser("admin")
    - use verifyIsUserOrAdminOrExtra(Set(userName)) instead of verifyIsAuthenticatedUser(userName, "admin")
  
* 2016-04-07: 0.3.1:
  - use input stream when reading in uploaded ontology and in ontology format conversion.
    Now, the associated Jena warnings are gone, and, as a good test, loading/converting the 
    CF standard name ontology now succeeds
    (the error was: ERROR org.apache.jena.riot - {E213} Input length = 1)
  - refactor model read as preparation for more appropriate mechanism using input stream and not reader
  
  - consistently use similar user verification (try basic auth then JWT) in various operations
  - add requireAuthenticatedUser to be called wherever an authenticated user is expected
  - increase upload max size to 10MB
  - some debugging enabled for ontology upload operation
  - print stacktrace for 500 error code
  
* 2016-04-05: 0.3.1:
  - dockerizing ...
  
* 2016-03-06: 0.3.1:
  - skip testing of TripleStoreServiceAgRest, SelfHostedOntController
  - more org tests
  - some refactoring to facilitate triple store route testing
  - add basic test for "self-hosted" request
  - add tests for requested formats
  
* 2016-03-03: 0.3.1:
  - add scoverage
  - default warn log level
   
* 2016-01-19: 0.3.0:
  - re-enable test with use of JWT based authentication
  - before(): after basic auth, tries to get JWT from the params, then from the (json) body
  
* 2016-01-18: 0.3.0:
  - add route to send email with reminder of username(s)
  - adjustment in registration of new user
  - first version of password reset, basic but functional
  
* 2016-01-14: 0.2.0:
  - try basic auth, then JWT for user authentication
  - add route for JWT generation for firebase-based auth

* 2016-01-04: 0.1.0:
  - add sbt-assembly plugin (0.14.1) to create standalone version. Generation (excluding tests)
    and execution of resulting standalone worked OK locally but only the addition of the plugin is committed.
    Actual generation will require some manual steps; see comments in JettyLauncher.scala and notes in build.scala.
      
  - update sbt-dependency-graph plugin to 0.8.0
  
* 2016-01-03: 0.1.0: SelfHostedOntController: preliminaries to dispatch ORR Portal interface. 
  - this dispatch is done according to content negotiation (or explicit "format" parameter) indicating that
    HTML is to be responded (well, and also associated resources like css, js, etc)
  - dispatch only done if the `/index.html` file exists under my orr-ont's application context.
    Otherwise, usual dispatch (with preference for json response) is done.
  - The existence of `/index.html` is assumed to indicate that all other orrportal application's resources 
    are accessible under the orr-ont application context.
    See orr-portal's gulp "install" target (along with `--base` and `--dest`), which installs these resources.
  - In summary: "self-resolution" of the ORR Portal interface when the ORR Portal is installed
    under orr-ont application context. For HTML always dispatches /index.html for any requested path.
    
  - add support method getRequestedFormat for content-negotiation: returns requested format with precedence to the "format" 
    parameter if given, otherwise according to the Accept header
    
  
* 2015-11-13:
  - remove ./sbt, which continues to fail locally and on travis with: 
  ```
        [info] Loading global plugins from /Users/carueda/.sbt/0.13/plugins
        [info] Loading project definition from /Users/carueda/github/orr-ont/project
        [info] Set current project to ORR Ont (in build file:/Users/carueda/github/orr-ont/)
        [error] Not a valid command: warn
        [error] Not a valid project ID: warn
        [error] Expected ':' (if selecting a configuration)
        [error] Not a valid key: warn (similar: run, clean, watch)
        [error] warn
   ```
   But with my global sbt (sbt launcher version 0.13.7) all works just fine.
   (./sbt came from the g8 template and it used to work, but not even recent versions fix the problem)
  - update some dependencies
  - application.conf

* 2015-03:
  - auth
  - ont metadata
  
* 2015-02:
  - api authenticator
  - org members
  
* 2015-01:
  - service classes 
  - self-hosted dispatch
  - triple store controller
  - AquaImporter
  - store un-versioned form of all versions of imported ontology
  
* 2014: 
  - first prototype
