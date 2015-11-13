import sbt._
import Keys._
import org.scalatra.sbt._
import com.earldouglas.xsbtwebplugin.PluginKeys.port
import com.earldouglas.xsbtwebplugin.WebPlugin.container

object build extends Build {
  val Organization = "org.mmisw"
  val Name = "ORR Ont"
  val Version = "0.1.0-SNAPSHOT"

  val ScalaVersion      = "2.11.6"
  val ScalatraVersion   = "2.3.0"
  val json4Version      = "3.2.10"
  val casbahVersion     = "2.7.1"
  val salatVersion      = "1.9.9"
  val jenaVersion       = "2.11.1"

  private val graphSettings = net.virtualvoid.sbt.graph.Plugin.graphSettings

  lazy val project = Project (
    "orr-ont",
    file("."),
    settings = Defaults.defaultSettings ++ ScalatraPlugin.scalatraWithJRebel ++ graphSettings ++
      Seq(
      organization := Organization,
      name := Name,
      version := Version,
      scalaVersion := ScalaVersion,
      resolvers += Classpaths.typesafeReleases,
      libraryDependencies ++= Seq(
        "org.mongodb"               %% "casbah"               % casbahVersion,
        "com.novus"                 %% "salat"                % salatVersion,

        "org.scalatra"              %% "scalatra"             % ScalatraVersion,
        "org.scalatra"              %% "scalatra-specs2"      % ScalatraVersion % "test",
        "org.scalatra"              %% "scalatra-json"        % ScalatraVersion,
        "org.scalatra"              %% "scalatra-auth"        % ScalatraVersion,

        "org.json4s"                %% "json4s-native"        % json4Version,
        "org.json4s"                %% "json4s-ext"           % json4Version,
        "org.json4s"                %% "json4s-jackson"       % json4Version,
        "org.json4s"                %% "json4s-mongo"         % json4Version,

        "org.apache.jena"            % "jena-core"            % jenaVersion,
        "com.github.jsonld-java"     % "jsonld-java-jena"     % "0.3",

        "com.typesafe"               % "config"               % "1.2.1",
        "com.typesafe.scala-logging"%% "scala-logging"        % "3.1.0",

        "org.jasypt"                 % "jasypt"               % "1.9.2",

        "net.databinder.dispatch"   %% "dispatch-core"        % "0.11.2",

        "ch.qos.logback"             % "logback-classic"      % "1.0.6" % "runtime",

        // if creating standalone, uncomment code in JettyLauncher and set "container;compile" here:
        "org.eclipse.jetty"          % "jetty-webapp"         % "8.1.8.v20121106" % "container",

        "org.eclipse.jetty.orbit"    % "javax.servlet"        % "3.0.0.v201112011016" % "container;provided;test" artifacts (Artifact("javax.servlet", "jar", "jar"))
      ),
      scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-language:_"),
      port in container.Configuration := 8081
    )
  )
}
