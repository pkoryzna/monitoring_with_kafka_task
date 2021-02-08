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
