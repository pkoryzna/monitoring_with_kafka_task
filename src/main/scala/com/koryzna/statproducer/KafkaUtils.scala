package com.koryzna.statproducer

import com.typesafe.scalalogging.Logger
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}

import java.util.Properties


object KafkaUtils {
  val logger: Logger = Logger("KafkaUtils")

  def createProducer(bootstrapServers: String): KafkaProducer[String, Array[Byte]] = {
    logger.info(s"Starting Kafka producer with bootstrap servers $bootstrapServers")

    val props = new Properties()
    props.put("bootstrap.servers", bootstrapServers)
    props.put("acks", "all")
    props.put("retries", Int.box(0))
    props.put("linger.ms", Int.box(1))

    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    props.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer")

    val producer = new KafkaProducer[String, Array[Byte]](props)

    producer
  }

}