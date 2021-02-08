package com.koryzna.statconsumer

import com.typesafe.scalalogging.Logger
import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords, KafkaConsumer}

import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.jdk.DurationConverters._
import scala.util.{Failure, Success, Try}

object ConsumerApp {
  val logger: Logger = Logger("ConsumerApp")


  def handleKafkaRecords(records: ConsumerRecords[String, String]): List[StatRecord] = {
    val buf = ListBuffer[StatRecord]()
    records.forEach { record =>
      convertKafkaRecord(record) match {
        case Success(sr) => buf.append(sr)
        case Failure(ex) => logger.error("Failed to convert record", ex)
      }
    }
    buf.toList
  }

  def convertKafkaRecord(record: ConsumerRecord[String, String]): Try[StatRecord] = {
    val decodedKeyTry = record.key().split("\\$") match {
      case Array(machineName, statName) => Success((machineName, statName))
      case _ => Failure(new IllegalArgumentException(s"Key [${record.key()}] had unexpected format"))
    }

    val decodedValueTry = Try(record.value().toDouble)
      .recoverWith { _ => Failure(new IllegalArgumentException(s"Value [${record.value()}] could not be parsed as Double for key [${record.key()}]")) }

    for {
      (machineName, statName) <- decodedKeyTry
      doubleValue <- decodedValueTry
    } yield StatRecord(machineName, statName, doubleValue)
  }

  private def createConsumer(bootstrapServers: String, groupId: String, topicName: String): KafkaConsumer[String, String] = {
    val props = new Properties()
    props.setProperty("bootstrap.servers", bootstrapServers)
    props.setProperty("group.id", groupId)
    props.setProperty("enable.auto.commit", "false")
    props.setProperty("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
    props.setProperty("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")

    val consumer = new KafkaConsumer[String, String](props)

    consumer.subscribe(List(topicName).asJava)
    consumer
  }

  def main(args: Array[String]): Unit = {
    val bootstrapServers: String = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    val groupId: String = sys.env.getOrElse("KAFKA_CONSUMER_GROUP_ID", "localhost:9092")

    val topicName: String = "stats_strings"

    val consumer: KafkaConsumer[String, String] = createConsumer(bootstrapServers, groupId, topicName)

    val shouldConsume = new AtomicBoolean(true)

    sys.addShutdownHook {
      logger.warn("Shutting down consumer loop")
      shouldConsume.set(false)
    }

    while(shouldConsume.get()) {
      val records = consumer.poll(1.second.toJava)
      val converted = handleKafkaRecords(records)

      println(converted) // FIXME JDBC insert here ;-)

      consumer.commitSync(5.seconds.toJava)
    }

    logger.warn("Shutting down consumer")
    consumer.close(30.seconds.toJava)
    logger.info("Consumer shut down. Goodbye!")
  }


}


case class StatRecord(machineName: String, statName: String, value: Double)