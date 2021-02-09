package com.koryzna.statproducer

import com.koryzna.statproducer.model.stats.StatsRecord
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers
import matchers.should._
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringSerializer}
import scala.jdk.CollectionConverters._

class ProducerTaskTest extends AnyFlatSpec with Matchers {
  val collectedStats = List(
    StatsRecord("testMachine", "fake.cpu", System.currentTimeMillis(), 123.0),
    StatsRecord("testMachine", "fake.memory", System.currentTimeMillis(), 9999999.0),
  )

  val mockSource: StatsSource = () => collectedStats

  val testTopic = "testTopic"
  val producer = new MockProducer[String, Array[Byte]](true, new StringSerializer, new ByteArraySerializer)

  val task = new ProducerTask(producer, mockSource, testTopic)

  "ProducerTask" should "send collected statistics as separate records" in {
    producer.clear()
    task.run()

    producer.history().size() shouldEqual (collectedStats.length)
  }

  it should "send records with key containing machine name and stat name" in {
    producer.clear()
    task.run()

    producer.history().asScala.zip(collectedStats) foreach { case (kRecord, sRecord) =>
      kRecord.key() should (include(sRecord.machineName) and include(sRecord.statName))
    }
  }

  it should "send records with values that can be deserialized back to original protobuf message" in {
    producer.clear()
    task.run()

    producer.history().asScala.zip(collectedStats) foreach { case (kRecord, sRecord) =>
      StatsRecord.parseFrom(kRecord.value()) shouldEqual (sRecord)
    }
  }

  it should "send records with the topic specified" in {
    producer.clear()
    task.run()

    producer.history().asScala.foreach { kRecord =>
      kRecord.topic() shouldEqual testTopic
    }
  }

  it should "let the exceptions from source bubble up and not send anything" in {
    producer.clear()
    val expectedException = new Exception("No keyboard detected, press F1 to continue...")
    val failingStatsSource: StatsSource = { () => throw expectedException }

    val task = new ProducerTask(producer, failingStatsSource, testTopic)

    the [Exception] thrownBy task.run() shouldEqual expectedException
    producer.history().toArray shouldBe empty
  }
}
