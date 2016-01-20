## change log ##

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
