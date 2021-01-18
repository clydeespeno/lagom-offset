name := "lagom-offset"

version := "0.1"

scalaVersion in ThisBuild := "2.12.10"

val CassandraDriverVersion = "3.10.2"
val MacwireVersion = "2.3.1"

val cassandraDriverExtras = "com.datastax.cassandra" % "cassandra-driver-extras" % CassandraDriverVersion
val cassandraDriverCore = "com.datastax.cassandra" % "cassandra-driver-core" % CassandraDriverVersion
val macwire = "com.softwaremill.macwire" %% "macros" % MacwireVersion % Provided

lazy val root = (project in file("."))
  .aggregate(`lagom-offset`)

lazy val `lagom-offset` = (project in file("lagom-offset"))
  .enablePlugins(LagomScala)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi,
      lagomScaladslAkkaDiscovery,
      lagomScaladslPersistence,
      lagomScaladslPersistenceCassandra,
      lagomScaladslServer,
      macwire
    )
  )

