package com.koryzna.statproducer

import com.koryzna.statproducer.model.stats.StatsRecord
import com.typesafe.scalalogging.Logger
import org.apache.kafka.clients.producer.{Producer, ProducerRecord}

import java.util.concurrent.Executors
import scala.concurrent.CancellationException
import scala.concurrent.duration._
import scala.jdk.DurationConverters._
import scala.util.control.NonFatal

object StatsProducerApp {
  val logger: Logger = Logger("StatsProducerApp")
  val timerPeriod: FiniteDuration = 1.second
  val terminationTimeout: FiniteDuration = 5.seconds
  val bootstrapServers: String = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")

  def main(args: Array[String]): Unit = {
    val topicName = "stats_proto"

    val statsSource = new OSHIStatsSource()

    val producer = KafkaUtils.createProducer(bootstrapServers)

    // Unless we're doing a lot of I/O, like scanning log files or something like that,
    // we can keep this pool small.
    val timerPool = Executors.newScheduledThreadPool(1)
    val task = new ProducerTask(producer, statsSource, topicName)

    logger.info("Starting ProducerTask")
    // If collecting system statistics takes a longer than the period,
    // then this should be replaced with scheduleWithFixedDelay.
    val taskFuture = timerPool.scheduleAtFixedRate(task, 0L, timerPeriod.length, timerPeriod.unit)

    sys.addShutdownHook {
      logger.warn("Graceful shutdown started")
      logger.warn("Canceling timer")
      timerPool.shutdown()
      timerPool.awaitTermination(terminationTimeout.length, terminationTimeout.unit)

      logger.warn("Closing producer")
      producer.close(terminationTimeout.toJava)

      logger.warn("All shut down, goodbye!")
    }

    try {
      taskFuture.get()
    }
    catch {
      case _: CancellationException => () // do nothing on task cancellation, this is fine
      case NonFatal(ex) =>
        logger.error("Producer task failed with exception, shutting down.", ex)
        sys.exit(1)

    }
  }
}

/**
 * Polls stats from `statsSource`, converts them them to Kafka records and sends them to specified topic.
 */
class ProducerTask(producer: Producer[String, Array[Byte]], statsSource: StatsSource, topicName: String) extends Runnable {

  import ProducerTask._

  override def run(): Unit = {
    for {
      stat <- statsSource.getCurrentStats()
      serialized = stat.toByteArray
      record = statsRecordToKafka(topicName, stat, serialized)
    } producer.send(record)
  }
}

object ProducerTask {
  def statsRecordToKafka(topicName: String, stat: StatsRecord, serialized: Array[Byte]): ProducerRecord[String, Array[Byte]] = {
    new ProducerRecord(topicName, s"${stat.machineName}/${stat.statName}", serialized)
  }
}