package com.koryzna.statproducer

import com.typesafe.scalalogging.Logger
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}

import java.util.{Properties, Timer, TimerTask}
import scala.concurrent.duration._
import scala.jdk.DurationConverters._

object StatsProducerApp {
  val logger: Logger = Logger("StatsProducerApp")
  val timerPeriod: FiniteDuration = 30.seconds

  val bootstrapServers: String = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")

  def main(args: Array[String]): Unit = {
    val topicName = "stats_strings"

    val statsGatherer = new StatsGatherer()

    logger.info(s"Starting Kafka producer with bootstrap servers $bootstrapServers")
    val producer = KafkaUtils.createProducer(bootstrapServers)

    val timer = new Timer("stats-gather-timer")
    val task: TimerTask = new TimerTask {
      override def run(): Unit = {
        val stats = statsGatherer.getCurrentStats()
        KafkaUtils.statsToRecords(stats, topicName).foreach(producer.send)
      }
    }

    timer.scheduleAtFixedRate(task, 0L, timerPeriod.toMillis)

    sys.addShutdownHook {
      logger.warn("Graceful shutdown started")
      logger.warn("Canceling timer")
      timer.cancel()
      logger.warn("Closing producer")
      producer.close(10.seconds.toJava)
      logger.warn("All shut down, goodbye!")
    }
  }
}


object KafkaUtils {
  val logger: Logger = Logger("KafkaUtils")

  def createProducer(bootstrapServers: String) = {
    logger.info("Creating Kafka producer")

    val props = new Properties()
    props.put("bootstrap.servers", bootstrapServers)
    props.put("acks", "all")
    props.put("retries", Int.box(0))
    props.put("linger.ms", Int.box(1))

    // FIXME: this could use a more efficient encoding in a real system.
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")

    val producer = new KafkaProducer[String, String](props)

    producer
  }

  def statsToRecords(stats: List[(String, Double)], topicName: String): List[ProducerRecord[String, String]] = {
    stats.map{ case (key, statValue) =>
      new ProducerRecord[String, String](topicName, key, statValue.toString)
    }
  }
}