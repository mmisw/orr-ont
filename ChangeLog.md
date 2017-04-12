## change log ##

* 2017-03-11:  3.4.2
  - resolve #45: "upgrade Apache Jena to 3.2.0"
    - not anymore dependency on jsonld-java-jena
    - update some tests thanks to support in new jena version
  
* 2017-03-07:  3.4.1
  - return OntologySummaryResult for ontology existence check
  - GET /ont?ouri=.. now also accepts onlyExistence=yes to only check for existence
    of the ontology URI in the database. This facilitates orr-portal operations.
  - GET /user/{userName} now with optional withOnts=yes to include the list of
    registered ontologies by ~userName as owner (note: not as submitter) 
  - get ontologyType from format if not already an "orr-" one in the db 
  - minor code clean-up 
  
* 2017-03-04:  3.4.0
  - docker/deployment stuff moved to 'orr' project
  
* 2017-03-03:  3.3.2
  - fix #44 "failing to load external ontology for mapping"
  - ontUtil.loadExternalModel: use a combination of httpUtil.downloadUrl (to make sure
    followRedirects is in place) and ontFileLoader.loadOntModel to guess the format.
    TODO: for efficiency, the contentType reported by httpUtil.downloadUrl could be 
    used to try the specific corresponding format.
    
  
* 2017-03-02:  3.3.1
  - align version with orr-portal
  
* 2017-02-28:  3.3.0
  - OntologySummaryResult.versions is now a list of OntologyVersionSummary 
    and sorted by decreasing version (first in list is the latest).
    OntologyVersionSummary introduced in particular to expose associated 
    log message, but other attributes can be added as convenient.

* 2017-02-28:  3.2.5
  - align with orr-portal version
  - resolve #43 "capture log message for ontology version registration"
  
* 2017-02-24:  3.2.4
  - SelfHostedOntController: adjustments in resolution to dispatch request for 
    organization or user
  - GET /org/{orgName} now with optional withOnts=yes to include the list of
    registered ontologies by the given organization.
  
* 2017-02-21:  3.2.3
  - AquaImporter.getVersionVisibility: based on propagated version_status as the 
    version are created (in increasing time order). Also, "public" is the actual 
    default when visibility cannot be derived from status or authority abbreviation.
  - ontUtil.extractSomeProps: specially as it's called from AquaImporter,
    set ontologyType to "orr-vocabulary", "orr-mapping", or "other"
    depending on Omv.usedOntologyEngineeringTool
  - Revisiting AquaImporter as preparation for migration of MMI ORR to v3.
  	- version visibility set according to explicit status ("stable" or "testing")
  	  if given, or on the traditional logic based on authority abbreviation
  	- do not create the interim "-" organization. Instead, assign the user as the 
  	  owner when there's no organization extracted from ontology uri
  	- user's registered attribute obtained from date_created but adjusted to 
  	  reflect earliest ontology submission because changes in user account 
  	  changes the date_created field in the old system.
  
* 2017-02-18:  3.2.2
  - report DownloadRemoteServerError with status=502 (Bad gateway) as
    indication that the problem was with the remote server.
  - include accept header when "uploading" a remoteUrl; 
  - report CannotRecognizeOntologyFormat with status=406
  - resolve #39 "Allow to register a remote URL."
    The various submission operations (POST /ont/upload, POST /ont, PUT /ont) 
    now also accept a remoteUrl parameter.
  - for convenience starting to use scalaj-http for http client requests 
    (maybe to replace dispatch depending on future orr development plans)

* 2017-02-16:  3.2.1
  - tests: mock\[TripleStoreService\] seems to have stopped working well with the
    introduction of TripleStoreResult in some operations, so commented out some test lines.
    This is not critical at all.
  - adjustments in triplestore operations; introduce generic TripleStoreResult
  - BaseController: now, to see if we have an authenticated user, try:
      1. JWT in authorization header
      2. JWT in parameter or in body
      3. basic-auth

* 2017-02-15:  3.2.1
  - fix #38 "triple duplications in metadata of vocabulary ontologies"
    Change below also provides an acceptable behavior for this one.
    Marked "revisit_later" as well.
  - fix #37 "triple duplications in metadata of mapping ontologies"
    NOTE: Not a complete fix but somewhat acceptable for the time being. Marking issue as "revisit_later."
    TODO explicit value type information for m2r and v2r models!
    While explicit type information is captured, implemented a temporary hack
    to distinguish between an literal string and a "uri" (resource)

* 2017-02-14:  3.2.1
  - m2r.saveM2RModel: remove duplicates in metadata and each mapping group
  - encode `#` -> `%23` in "Resolve with" email upon ontology registration
  
  - \#35: "API operation to insert new terms in vocabulary"
  	- preliminary `POST /ont/term`.
  	 Adds a single new term. Parameters:
  	  
  	  	- vocUri:    URI of ontology
  	  	- version:   ontology version (optional)
  	  	- classUri:  class of specific vocabulary (optional). If not given,
  	  	             and there's only one class in the ontology, then uses it.
  	  	             Otherwise, error.
  	  	- one of termName or termUri to indicate the URI for the new term (required) 
  	  	- attributes: array of array of strings for the property values (required)
  	  	
  	  Example request for a vocabulary with only a class having one property:
  	  
  	  ```
  	  $ http -a username:password post http://localhost:8081/api/v0/ont/term \
  	         vocUri=http://localhost:9001/src/app/~carueda/vocab1 \
  	         termName=baz attributes:='[ ["some prop value", "other value for same prop] ]'
  	  ```
  
* 2017-02-08:  3.2.0
  - some logging to debug email sending based on cfg.notifications.recipientsFilename
  
* 2017-02-07:  3.2.0
  - POST/PUT /ont: format parameter no longer required, with "_guess" as default value
  
* 2017-02-04:  3.1.9
  - fix orr-portal#76: "iceOfLandOrigin ontology: RDF/XML failing to upload".
    Two key aspects:
      - although the `owlapi.xrdf` file does contain an Ontology, such 
        resource is a _blank node_, so no URI is this case (which gets reported as null).
      - post("/upload") was returning a UploadedFileInfo directly; now this is done
        with explicit serialization to JSON.
  
* 2017-02-03:  3.1.8
  - improve reset-password and password-reset html pages
  - regenerate Cfg with tscfg 0.8.0.  Use json4s to log out the configuration.
  
* 2017-02-01:  3.1.7
  - fix #34 "cannot reset password".
    Instead of using HttpServletRequest methods to "get my base URL" (which are 
    rather tricky depending on intermediate servers, etc), simply use the 
    already introduced cfg.deployment.url config setting.

* 2016-11-09:  3.1.6
  - Re #31 "https == http...", actually the scheme change is tried in
    all ontology requests, not only from self-hosted controller.
    In particular, the portal may make a request (for example) for 
    a "https:..." URI, but with actual entry being "http:..."; 
    this requests goes through the /api/v?/ont/ route, so the scheme
    change should also be performed there.
  
* 2016-10-30:  3.1.5
  - resolve #31 "https == http for purposes of IRI identification"
    - additional adjustment in BaseOntController.resolveOntOrTermUri
* 2016-10-29:  3.1.5
  - Re #31 "https == http for purposes of IRI identification"
  	- preparations with ontology request. The special https==http handling only
  	  to be performed in self-resolution.
  	- note that HttpServletRequest.getRequestURL may return "http:..." even when
  	  the actual request was with "https:...". This may happen because of
  	  intermediate servers involved (eg., load balancer).
  	- note: if self-resolution request is from a typical browser (ie., with "html"
  	  as expected response), no need for any special handling as orr-ont dispatches
  	  the orr-portal in such cases -- eventually the portal will make a direct
  	  request via the API (not with self-resolution).
  
  - pass down the obtained getRequestedFormat in self-hosted dispatch
  
* 2016-10-27:  3.1.4
  - point to http://mmisw.org/orrdoc/install/ for the installation documentation
  
* 2016-10-26:  3.1.4
  - decided to capture the swagger spec in the mmiorr-docs repo:
    https://github.com/mmisw/mmiorr-docs/blob/master/docs/swagger.yaml
    This has the advantage that pushes there get automatically reflected
    at http://mmisw.org/orrdoc/api/ (thanks to the documentation webhook).
  
* 2016-10-21:  3.1.4
  - re #24 "generate API documentation", add /api-docs to respond with swagger.yaml,
    edited in http://editor.swagger.io/
    Note that editor.swagger.io seems to have some glitches (not always reflect changes, trouble to
    perform operations requiring authentication..), but the dispatch is OK in http://petstore.swagger.io/
   
* 2016-09-02:  3.1.3
  - update readme and add development.md
  - introduce optional cfg.agraph.initDelay; if given, the triple store initialization sequence
    will include a 2nd re-attempt after the given delay
  - resolve #25 "remove firebase"

* 2016-09-01:  3.1.2
  - set cfg.files.baseDirectory as limit in use of FileUtils.makeReadableByAnyone
  - introduce cfg.deployment.url to support better notification emails about ontology registrations
    (and possibly other uses later on). Ontology registration email will now include a
    `Resolve with: <url>?uri=<uri>` line when the ontology URI is not self-resolvable.
    NOTE: determining the deployment URL in the code itself (e.g., from the servlet context) risks
    being inaccurate depending on external factors (http proxies and the like).
  - regenerate Cfg with tscfg 0.3.3 - no external impact at all
  
* 2016-08-31:  3.1.1
  - add cfg.branding.footer as a mechanism to add a footer to the portal index.html files
  
  - actual fix #29 "triple store not being updated"
    The reason was that the AG container runs under the "agraph" user, but this user doesn't have permission to
    read the files. An example of the response to the orr-ont container from the request to AG:
  
  			response:
             status=400
             body=MALFORMED DATA: File
            /opt/orr-ont-base-directory/onts/https:||xdomes.tamucc.edu|ont|testorg|moretest/20160831T175824/file.rdf
            does not exist

		The actual error is "permission denied" as seen by trying the read diorectly as "agraph" under the AG container:
    
    		[agraph@2f47a7bdfd97 ~]$ ls -l '/opt/orr-ont-base-directory/onts/https:||xdomes.tamucc.edu|ont|testorg|moretest/20160831T175824/file.rdf'
            ls: cannot access /opt/orr-ont-base-directory/onts/https:||xdomes.tamucc.edu|ont|testorg|moretest/20160831T175824/file.rdf: Permission denied
            
        But the file is there as can be seen as "root":
            
            [root@2f47a7bdfd97 /]# ls -l '/opt/orr-ont-base-directory/onts/https:||xdomes.tamucc.edu|ont|testorg|moretest/20160831T175824/file.rdf'
            -rw-r-----. 1 root root 1927 Aug 31 17:58 /opt/orr-ont-base-directory/onts/https:||xdomes.tamucc.edu|ont|testorg|moretest/20160831T175824/file.rdf
            
        The simple fix is to make sure the file is readable by anyone prior to submitting the load-to-triple-store request.
        Alternative approaches (eg, making AG run under "root", or adding the "root" group to "agraph", and similar) would
        involve additional settings external to the orr-ont code itself.
  
  
* 2016-08-30:  3.1.1
  - re #28 "simplify docker-based deployment procedure"
  	- deployment now based on Docker Compose
  	
  			$ docker/build/dockerize.sh 3.1.1
  			
  	- TODO complete tuning up the instructions
  	
  - fix #29 "triple store not being updated"
    - `cfg.import.aquaUploadsDir` was used by TripleStoreServiceAgRest in condition to perform load
      of local file in AllegroGraph. But this was incorrect (seems like it was like this from an old
      version of the AquaImporter that also performed the load of the file in the triple store).
      TripleStoreServiceAgRest now *always* load from local file (as before, assuming that both AG and the
      ORR are deployed on the same server/filesystem)
      
    - remove the `import` section (which includes `aquaUploadsDir`) from the regular specified configuration.
      This section is only used by AquaImporter (which will go away once we migrate the MMI ORR to the new version)
      with direct editing of the runtime configuration.
      
  - allow leading `#` to indicate comment in email file for sending notifications
  - some logging adjustments in triple store service
  
* 2016-08-16:  3.1.0
  - some deployment related adjustments
  
* 2016-08-16:  3.1.0
  - add google analytics processing for index.html files.
    This was captured in orr-portal#67, but decided to do the handling here (at least for the time being) as
    it is more convenient to adjust those index.html files.
  - re #28 "simplify docker-based deployment procedure" - update DEPLOYMENT.md reflecting also adjustments
    in orr-portal
  - at startup, "deploy" local.config.js for the UI if this file is found under `cfg.files.baseDirectory`
    and the js/ directory exists under the application context (indicating that the orr-portal has
    been included in the installable ont.war).
  - simplify `docker-run`: in particular, no use of the preliminary "httpd" container as the most
    flexible strategy is to have a host mechanism (e.g., Apache Httpd) to proxy/expose the ORR
    (as it's currently done in the COR instance).
  - some simplification and additional documentation in `template.orront.conf`
  - use java 8 (in docker image)
  - adjustments in ontology registration email notifications
  - adjustment in docker-run to specify "notify emails" file for orr-ont

* 2016-08-15:  3.1.0
  - resolve #13 "email notifications"
  - use tscfg-generated configuration (from orront.spec.conf) for type-safe access
  - remove "beta" tag per today's discussion
  
* 2016-08-06:
  - add swagger.json generated from http://editor.swagger.io/.
    This, along with an installation of swagger-ui, is deployed at http://mmisw.org/orrdoc/api/,
    which is linked from http://mmisw.org/orrdoc/rest/
    
    NOTE: Unfortunately upgrading the relevant scalatra libs for automatic generation of the spec
    from the code itself is tricky at this point.
    
   
* 2016-07-27: 3.0.4-beta
  - resolve #21 "recaptcha"
  
* 2016-07-17: 3.0.2-beta
  - resolve #20: "v2r: for a property definition allow to indicate vocabulary from which to select values"
    Add `valueClassUri: Option[String] = None` to IdL case class.
    This piece is not yet transferred to any ontology representation as it's more an internal mechanism
    at least for now.
  - also, define property as generic RDF.Property (and not overly restrictive and unnecessary OWL.DatatypeProperty)
  
* 2016-07-15: 3.0.1-beta
  - align version with orr-portal
  
* 2016-07-13: 3.0.0-beta
  - introduce branding.instanceName config to adjust email messages
  
* 2016-07-12: 3.0.0-beta
  - dockerize with deployable named as ont.war. 
    This mainly to avoiding issues with different external context path. Internally orr-ont uses the 
    servlet-based reported context path to differentiate fully- and re-hosted registrations.  
    (this could alternatively be handled with some extra config parameter, but I'm not doing that right now.)
  
* 2016-07-12: 3.0.0-beta
  - start beta status
  
* 2016-07-12: 3.0.6-alpha
  - bump version to align with orr-portal
  
* 2016-07-07: 3.0.5-alpha
  - internal: for simplification, changed OntVisibility from an Enumeration to a helper object.
    So, OntologyVersion.visibility is now an Option[String].
    The visibility parameter is optional in POST and PUT /ont, with default OntVisibility.owner.
  - resolve #11 "capture status" - POST/PUT /ont: process status parameter.
    Note: the status attribute is an Option[String] in the model with not forced set of possible values.
  
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
