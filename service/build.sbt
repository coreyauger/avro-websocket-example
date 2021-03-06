name := "avro-service"

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
  "com.typesafe.akka"       %% "akka-cluster" % akkaV,
  "com.sksamuel.avro4s" %% "avro4s-core" % "2.0.2",
  "io.surfkit" %% "type-bus" % "0.0.5-SNAPSHOT"
)

mainClass in (Compile, run) := Some("org.squbs.unicomplex.Bootstrap")


