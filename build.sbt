name := "statproducer"

version := "0.1"

scalaVersion := "2.13.4"

// https://github.com/scalapb/ScalaPB#installing
Compile / PB.targets := Seq(
  scalapb.gen() -> (Compile / sourceManaged).value / "scalapb"
)

lazy val KafkaVersion = "2.7.0"

libraryDependencies += "org.apache.kafka" % "kafka-clients" % KafkaVersion

// for system stats
libraryDependencies += "com.github.oshi" % "oshi-core" % "5.4.1"

// logging
libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2"
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"

// DB stuff
libraryDependencies += "org.postgresql" % "postgresql" % "42.2.18"
libraryDependencies += "org.flywaydb" % "flyway-core" % "7.5.2"
libraryDependencies += "org.scalikejdbc" %% "scalikejdbc" % "3.5.0"