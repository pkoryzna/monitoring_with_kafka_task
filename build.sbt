name := "statproducer"

ThisBuild / version := "0.1"
ThisBuild / scalaVersion := "2.13.4"


lazy val root = (project in file("."))
  .aggregate(models, producer, consumer)

// `in file ...` can be omitted, leaving it in for clarity
lazy val models = (project in file("models")).settings(
  // https://github.com/scalapb/ScalaPB#installing
  Compile / PB.targets := Seq(
    scalapb.gen() -> (Compile / sourceManaged).value / "scalapb"
  )
)

lazy val producer = (project in file("producer")).settings(
  libraryDependencies ++= CommonDependencies,
  libraryDependencies ++= ProducerDependencies
).dependsOn(models)

lazy val consumer = (project in file("consumer")).settings(
  libraryDependencies ++= CommonDependencies,
  libraryDependencies ++= ConsumerDependencies
).dependsOn(models)


lazy val CommonDependencies = Seq(
  // the most important thing ;-)
  "org.apache.kafka" % "kafka-clients" % "2.7.0",
  // logging
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  // testing
  "org.scalactic" %% "scalactic" % "3.2.2",
  "org.scalatest" %% "scalatest" % "3.2.2" % "test")

lazy val ProducerDependencies = Seq(
  // for system stats
  "com.github.oshi" % "oshi-core" % "5.4.1",

)

lazy val ConsumerDependencies = Seq(
  // DB stuff
  "org.postgresql" % "postgresql" % "42.2.18",
  "org.flywaydb" % "flyway-core" % "7.5.2",
  "org.scalikejdbc" %% "scalikejdbc" % "3.5.0",
)
