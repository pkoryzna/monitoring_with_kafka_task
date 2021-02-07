name := "statproducer"

version := "0.1"

scalaVersion := "2.13.4"

lazy val KafkaVersion = "2.7.0"

libraryDependencies += "org.apache.kafka" % "kafka-clients" % KafkaVersion

// for system stats
libraryDependencies += "com.github.oshi" % "oshi-core" % "5.4.1"