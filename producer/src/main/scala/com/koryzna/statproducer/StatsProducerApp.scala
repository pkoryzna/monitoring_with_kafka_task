package com.koryzna.statproducer

import com.koryzna.statproducer.model.stats.StatsRecord
import com.koryzna.statproducer.utils.KafkaUtils
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import org.apache.kafka.clients.producer.{Producer, ProducerRecord}

import java.io.File
import java.util.concurrent.Executors
import scala.concurrent.CancellationException
import scala.concurrent.duration._
import scala.jdk.DurationConverters._
import scala.util.control.NonFatal

object StatsProducerApp {
  val logger: Logger = Logger("StatsProducerApp")


  def main(args: Array[String]): Unit = {
    if (args.length != 1) {
      logger.error("This application requires a configuration file path as first argument")
      sys.exit(1)
    }

    val config = ConfigFactory.parseFile(new File(args(0)))
        .withFallback(ConfigFactory.load())

    val timerPeriod: FiniteDuration = config.getDuration("statproducer.timer.period").toScala
    val terminationTimeout: FiniteDuration = config.getDuration("statproducer.termination.timeout").toScala

    val topicName = config.getString("statproducer.topic")

    val statsSource = new OSHIStatsSource()

    val producer = KafkaUtils.createProducer(config)

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