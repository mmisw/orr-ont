## change log ##

* 2016-01-03:
  - add support method getRequestedFormat for content-negotiation: returns requested format with precedence to the "format" 
    parameter if given, otherwise according to the Accept header
    
  
* 2015-11-13:
  - remove ./sbt, which continues to fail locally and on travis with: ```
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
