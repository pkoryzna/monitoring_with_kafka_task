package com.koryzna.statconsumer

import com.koryzna.statproducer.model.stats.StatsRecord
import com.typesafe.scalalogging.Logger
import org.apache.kafka.clients.consumer.{ConsumerRecords, KafkaConsumer}

import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.jdk.DurationConverters._
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object ConsumerApp {
  val logger: Logger = Logger("ConsumerApp")

  def handleKafkaRecordsBatch(kafkaRecords: ConsumerRecords[String, Array[Byte]]): List[StatsRecord] = {
    val buf = ListBuffer[StatsRecord]()
    kafkaRecords.forEach { kafkaRecord =>
      try {
        val parsed = StatsRecord.parseFrom(kafkaRecord.value())
        buf.append(parsed)
      } catch {
        case NonFatal(ex) => logger.error(s"Failed to parse record with offset [${kafkaRecord.offset()}]", ex)
      }
    }
    buf.toList
  }

  private def createConsumer(bootstrapServers: String, groupId: String, topicName: String): KafkaConsumer[String, Array[Byte]] = {
    val props = new Properties()
    props.setProperty("bootstrap.servers", bootstrapServers)
    props.setProperty("group.id", groupId)
    props.setProperty("enable.auto.commit", "false")
    props.setProperty("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
    props.setProperty("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer")

    val consumer = new KafkaConsumer[String, Array[Byte]](props)

    consumer.subscribe(List(topicName).asJava)
    consumer
  }

  def main(args: Array[String]): Unit = {
    val bootstrapServers: String = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    val groupId: String = sys.env.getOrElse("KAFKA_CONSUMER_GROUP_ID", "localhost:9092")

    val topicName: String = "stats_proto"

    val consumer: KafkaConsumer[String, Array[Byte]] = createConsumer(bootstrapServers, groupId, topicName)

    val shouldConsume = new AtomicBoolean(true)

    sys.addShutdownHook {
      logger.warn("Shutting down consumer loop")
      shouldConsume.set(false)
    }

    while(shouldConsume.get()) {
      val records = consumer.poll(1.second.toJava)
      val converted = handleKafkaRecordsBatch(records)

      println(converted) // FIXME JDBC insert here ;-)

      consumer.commitSync(5.seconds.toJava)
    }

    logger.warn("Shutting down consumer")
    consumer.close(30.seconds.toJava)
    logger.info("Consumer shut down. Goodbye!")
  }


}


