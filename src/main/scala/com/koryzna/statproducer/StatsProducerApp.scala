package com.koryzna.statproducer

import com.koryzna.statproducer.model.stats.StatsRecord
import com.typesafe.scalalogging.Logger
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}

import java.util.{Properties, Timer, TimerTask}
import scala.concurrent.duration._
import scala.jdk.DurationConverters._

object StatsProducerApp {
  val logger: Logger = Logger("StatsProducerApp")
  val timerPeriod: FiniteDuration = 1.second

  val bootstrapServers: String = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")

  def main(args: Array[String]): Unit = {
    val topicName = "stats_proto"

    val statsGatherer = new StatsGatherer()

    val producer = KafkaUtils.createProducer(bootstrapServers)

    val timer = new Timer("stats-gather-timer")
    val task: TimerTask = new TimerTask {
      override def run(): Unit = {
        for {
          stat <- statsGatherer.getCurrentStats()
          serialized = stat.toByteArray
          record = statsRecordToKafka(topicName, stat, serialized)
        } producer.send(record)
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

  def statsRecordToKafka(topicName: String, stat: StatsRecord, serialized: Array[Byte]): ProducerRecord[String, Array[Byte]] = {
    new ProducerRecord(topicName, s"${stat.machineName}/${stat.statName}", serialized)
  }
}
