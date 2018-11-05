name := "avro-root"

organization in ThisBuild := "io.surfkit"

version in ThisBuild := "0.0.1-SNAPSHOT"

publishArtifact := false

lazy val root =
  (project in file("."))
  .aggregate(`avro-server`)
  .aggregate(`avro-service`)

lazy val `avro-server` = ProjectRef(uri("server"), "avro-server")

lazy val `avro-service` = ProjectRef(uri("service"),"avro-service")

fork in ThisBuild := true

resolvers in ThisBuild ++= Seq(
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots")
)

