package com.koryzna.statconsumer

import com.koryzna.statproducer.model.stats.StatsRecord
import com.koryzna.statproducer.utils.KafkaUtils
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import org.apache.kafka.clients.consumer.{Consumer, ConsumerRecords, KafkaConsumer}
import org.flywaydb.core.Flyway
import scalikejdbc.ConnectionPool

import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

object ConsumerApp {
  val logger: Logger = Logger("ConsumerApp")

  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load()

    val consumerConfig = ConsumerAppConfig.fromConfig(config)
    val dbConfig = DbConfig.fromConfig(config)

    val flyway = Flyway.configure().dataSource(dbConfig.url, dbConfig.user, dbConfig.password).load()
    flyway.migrate()

    // Configure simple 1-connection pool for scalikejdbc
    ConnectionPool.singleton(dbConfig.url, dbConfig.user, dbConfig.password)

    val topicName: String = config.getString("statconsumer.topic")

    val consumer: KafkaConsumer[String, Array[Byte]] = KafkaUtils.createConsumer(config)
    consumer.subscribe(Seq(topicName).asJava)


    val consumerTask = new ConsumerTask(
      consumer,
      consumerConfig.pollingPeriod,
      JDBCRepository,
      consumerConfig.commitTimeout,
    )

    val canceled = new AtomicBoolean(false)

    sys.addShutdownHook {
      logger.warn("Shutting down consumer loop")
      canceled.set(true)
    }

    while (!canceled.get()) {
      consumerTask.run()
    }

    logger.warn("Shutting down Kafka consumer")
    consumer.close(consumerConfig.terminationTimeout)
    logger.info("Consumer shut down. Goodbye!")
  }
}


class ConsumerTask(
  kafkaConsumer: Consumer[String, Array[Byte]],
  pollingPeriod: java.time.Duration,
  repository: StatsRepository,
  commitTimeout: java.time.Duration,
) {
  private val logger: Logger = Logger("ConsumerTask")
  def run(): Unit = {
    val kafkaRecords = kafkaConsumer.poll(pollingPeriod)

    val startTime = System.currentTimeMillis()

    val parsed = parseProtoRecords(kafkaRecords)

    repository.insertStatsRecords(parsed)
    kafkaConsumer.commitSync(commitTimeout)

    logger.debug(s"Inserted ${parsed.length} records in ${System.currentTimeMillis() - startTime} ms")
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

