package com.koryzna.statproducer.utils

import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer

import java.util.Properties
import scala.jdk.CollectionConverters._

object KafkaUtils {
  val logger: Logger = Logger("KafkaUtils")

  def createProducer(config: Config): KafkaProducer[String, Array[Byte]] = {
    val kafkaProps = configToProperties(config.getConfig("kafka"))
    logger.info(s"Starting Kafka producer with bootstrap servers ${kafkaProps.get("bootstrap.servers")}")

    new KafkaProducer[String, Array[Byte]](kafkaProps)
  }

  def createConsumer(config: Config): KafkaConsumer[String, Array[Byte]] = {
    val kafkaProps = configToProperties(config.getConfig("kafka"))

    new KafkaConsumer[String, Array[Byte]](kafkaProps)
  }

  def configToProperties(config: Config): Properties = {
    val properties = new Properties()
    for {
      entry <- config.entrySet().asScala
      key = entry.getKey
      value = entry.getValue.unwrapped()
    } properties.put(key, value)

    properties
  }
}