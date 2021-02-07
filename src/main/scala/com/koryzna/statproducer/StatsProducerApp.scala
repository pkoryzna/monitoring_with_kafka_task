package com.koryzna.statproducer

import java.util.Properties

object StatsProducerApp {

}


object KafkaUtils {

  def createProducer() = {
    val props = new Properties()
    props.put("bootstrap.servers", "localhost:9092")
    props.put("acks", "all")
    props.put("retries", 0)
    props.put("linger.ms", 1)
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")

    ???

  }
}