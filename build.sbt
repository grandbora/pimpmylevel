

name := "hello"

version := "1.0"

scalaVersion := "2.10.5"

packageArchetype.java_application

resolvers += "twitter-repo" at "http://maven.twttr.com"

libraryDependencies ++= Seq(
  "com.twitter" %% "finagle-core" % "6.25.0",
  "com.twitter" %% "finagle-http" % "6.25.0",
  "com.twitter" %% "finagle-mysql" % "6.25.0",
  "com.typesafe.play" % "play-json_2.10" % "2.4.0-M3",
  "com.google.api-client" % "google-api-client" % "1.20.0",
  "com.google.http-client" % "google-http-client-jackson2" % "1.20.0"
)