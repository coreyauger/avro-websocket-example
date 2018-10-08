name := "avro-websocket-example"

version := "0.0.1-SNAPSHOT"

organization in ThisBuild := "io.surfkit"

scalaVersion := "2.12.5"

resolvers += Resolver.sonatypeRepo("snapshots")

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-encoding", "utf8", "-language:postfixOps")

lazy val `avro-websocket-example` =
  (project in file("."))

val akkaV = "2.5.14"

val squbsV = "0.11.0"

libraryDependencies ++= Seq(
  "org.squbs" %% "squbs-unicomplex" % squbsV,
  "com.sksamuel.avro4s" %% "avro4s-core" % "1.9.0",
  "io.surfkit" %% "type-bus" % "0.0.5-SNAPSHOT"
)


