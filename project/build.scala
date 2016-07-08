import sbt._
import Keys._
import org.scalatra.sbt._
import com.earldouglas.xsbtwebplugin.PluginKeys.port
import com.earldouglas.xsbtwebplugin.WebPlugin.container
import sbtassembly.AssemblyPlugin.autoImport._
import scoverage.ScoverageKeys._

object build extends Build {
  val Organization = "org.mmisw"
  val Name = "orr-ont"
  val Version = "3.0.5-alpha"

  val ScalaVersion      = "2.11.6"
  val ScalatraVersion   = "2.3.0"
  val json4Version      = "3.2.10"
  val casbahVersion     = "2.7.1"
  val salatVersion      = "1.9.9"
  val jenaVersion       = "2.11.1"

  //[S]
  lazy val assemblySettings = Seq(
    // disable tests because they will fail with a weird problem just loading the SequenceSpec class
    test in assembly := {},
    mainClass in assembly := Some("org.mmisw.orr.ont.JettyLauncher")
  )

  lazy val coverageSettings = Seq(
    coverageExcludedPackages := ".*\\.x;ScalatraBootstrap;.*\\.util.MailSender;.*\\.Emailer;" +
      ".*\\.ApiAuthenticationSupport;.*\\.apiAuthenticator;.*\\.HmacUtils;" +
      ".*\\.FirebaseController;" +
      ".*\\.TripleStoreServiceAgRest;" +
      ".*\\.SelfHostedOntController;" +
      ".*\\.Skos",
    coverageMinimum := 70,
    coverageFailOnMinimum := false,
    coverageHighlighting := { scalaBinaryVersion.value == "2.11" }
  )

  lazy val project = Project (
    "orr-ont",
    file("."),
    settings = Defaults.defaultSettings ++ ScalatraPlugin.scalatraWithJRebel ++ assemblySettings ++ coverageSettings ++
      Seq(
      organization := Organization,
      name := Name,
      version := Version,
      scalaVersion := ScalaVersion,
      resolvers += Classpaths.typesafeReleases,
      libraryDependencies ++= Seq(
        "org.mongodb"               %% "casbah"               % casbahVersion,
        "com.novus"                 %% "salat"                % salatVersion,

        // [S]: exclude's below needed if using the assembly plugin; otherwise deduplication errors would occur
        // these exclude's also work ok for other tasks so no need to remove them

        ("org.scalatra"              %% "scalatra"             % ScalatraVersion)
          .exclude("org.slf4j", "slf4j-log4j12")
        ,

        "org.scalatra"              %% "scalatra-specs2"      % ScalatraVersion % "test",
        "org.scalatra"              %% "scalatra-json"        % ScalatraVersion,
        "org.scalatra"              %% "scalatra-auth"        % ScalatraVersion,

        "org.json4s"                %% "json4s-native"        % json4Version,
        "org.json4s"                %% "json4s-ext"           % json4Version,
        "org.json4s"                %% "json4s-jackson"       % json4Version,
        "org.json4s"                %% "json4s-mongo"         % json4Version,

        ("org.apache.jena"            % "jena-core"            % jenaVersion)
          .exclude("org.slf4j", "slf4j-log4j12")
        ,

        ("com.github.jsonld-java"     % "jsonld-java-jena"     % "0.3")
          .exclude("commons-logging", "commons-logging")
          .exclude("org.slf4j", "slf4j-log4j12")
        ,

        "net.sourceforge.owlapi"     % "owlapi-distribution"  % "3.4.5",

        "com.typesafe"               % "config"               % "1.2.1",
        "com.typesafe.scala-logging"%% "scala-logging"        % "3.1.0",

        "org.jasypt"                 % "jasypt"               % "1.9.2",

        "net.databinder.dispatch"   %% "dispatch-core"        % "0.11.2",

        "com.firebase"               % "firebase-token-generator" % "2.0.0",
        "com.auth0"                  % "java-jwt"                 % "2.1.0",

        "javax.mail"                 % "mail"                 % "1.4.7",

        "ch.qos.logback"             % "logback-classic"      % "1.0.6" % "runtime",

        // [S] to create standalone uncomment code in JettyLauncher and use "container;compile" here:
      //"org.eclipse.jetty"          % "jetty-webapp"         % "8.1.8.v20121106" % "container;compile", // standalone
        "org.eclipse.jetty"          % "jetty-webapp"         % "8.1.8.v20121106" % "container",         // *no* standalone

        "org.eclipse.jetty.orbit"    % "javax.servlet"        % "3.0.0.v201112011016" % "container;provided;test" artifacts (Artifact("javax.servlet", "jar", "jar"))
      ),
      scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-language:_"),
      port in container.Configuration := 8081
    )
  )
}
