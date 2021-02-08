package com.koryzna.statconsumer

import com.koryzna.statproducer.model.stats.StatsRecord
import com.typesafe.scalalogging.Logger
import org.apache.kafka.clients.consumer.{ConsumerRecords, KafkaConsumer}
import org.flywaydb.core.Flyway
import scalikejdbc.ConnectionPool

import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.jdk.DurationConverters._
import scala.util.control.NonFatal

object ConsumerApp {
  val logger: Logger = Logger("ConsumerApp")

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
    val groupId: String = sys.env.getOrElse("KAFKA_CONSUMER_GROUP_ID", "stats_consumer")

    // FIXME refactor, make a proper case class for config
    val dbUrl = sys.env.getOrElse("DB_URL", "jdbc:postgresql://localhost/postgres")
    val dbUser = sys.env.getOrElse("DB_USER", "postgres")
    val dbPassword = sys.env("DB_PASSWORD")

    val flyway = Flyway.configure().dataSource(dbUrl, dbUser, dbPassword).load()
    flyway.migrate()

    // Configure simple 1-connection pool for scalikejdbc
    ConnectionPool.singleton(dbUrl, dbUser, dbPassword)

    val topicName: String = "stats_proto"

    val consumer: KafkaConsumer[String, Array[Byte]] = createConsumer(bootstrapServers, groupId, topicName)

    val consumerTask = new ConsumerTask(consumer, 5.seconds, JDBCRepository)
    sys.addShutdownHook {
      logger.warn("Shutting down consumer loop")
      consumerTask.cancel()
    }

    consumerTask.run()

    logger.warn("Shutting down Kafka consumer")
    consumer.close(30.seconds.toJava)

    logger.info("Consumer shut down. Goodbye!")
  }
}


class ConsumerTask(
  kafkaConsumer: KafkaConsumer[String, Array[Byte]],
  pollingPeriod: FiniteDuration,
  repository: StatsRepository,
) {
  private val logger: Logger = Logger("ConsumerTask")
  private val canceled = new AtomicBoolean(false)

  /**
   * Polls Kafka consumer for new records and inserts them into repository.
   * This will block until cancel() is called (from a shutdown hook).
   */
  def run(): Unit = {
    while (!canceled.get()) {
      val kafkaRecords = kafkaConsumer.poll(pollingPeriod.toJava)
      val parsed = parseProtoRecords(kafkaRecords)

      repository.insertStatsRecords(parsed)
      kafkaConsumer.commitSync(5.seconds.toJava)
    }
  }

  /** Cancels the consumer task started by run(). */
  def cancel(): Unit = {
    canceled.set(false)
  }

  def parseProtoRecords(kafkaRecords: ConsumerRecords[String, Array[Byte]]): List[StatsRecord] = {
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
}

