package com.koryzna.statconsumer.model

import scala.util.{Failure, Success, Try}

// FIXME encode this more efficiently with protobuf and use in producer
case class StatRecord(machineName: String, statName: String, value: Double)

object StatRecord {
  def parseFromKafka(key: String, value: String): Try[StatRecord] = {
    val decodedKeyTry = key.split("\\$") match {
      case Array(machineName, statName) => Success((machineName, statName))
      case _ => Failure(new IllegalArgumentException(s"Key [${key}] had unexpected format"))
    }

    val decodedValueTry = Try(value.toDouble)
      .recoverWith { _ => Failure(new IllegalArgumentException(s"Value [${value}] could not be parsed as Double for key [${key}]")) }

    for {
      (machineName, statName) <- decodedKeyTry
      doubleValue <- decodedValueTry
    } yield StatRecord(machineName, statName, doubleValue)
  }
}
