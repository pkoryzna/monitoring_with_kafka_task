package com.koryzna.statconsumer

import com.koryzna.statproducer.model.stats.StatsRecord
import org.apache.kafka.clients.consumer.{ConsumerRecord, MockConsumer, OffsetResetStrategy}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers
import matchers.should._
import org.apache.kafka.common.TopicPartition

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

class ConsumerTaskTest extends AnyFlatSpec with Matchers {
  val testTopic = "test_topic"
  val consumer = new MockConsumer[String, Array[Byte]](OffsetResetStrategy.EARLIEST)

  val mockRepository = new StatsRepository {
    var records: Seq[StatsRecord] = Seq.empty
    override def insertStatsRecords(statsRecords: List[StatsRecord]): Unit = {
      records = records ++ statsRecords
    }
  }
  val task = new ConsumerTask(consumer, pollingPeriod = 1.second, mockRepository)


  val topicPartition = new TopicPartition(testTopic, 0)
  consumer.assign(List(topicPartition).asJava)
  consumer.updateBeginningOffsets(Map(topicPartition -> Long.box(0L)).asJava)

  "ConsumerTask" should "insert valid records into provided repository" in {
    val record = StatsRecord("deep-thought", "fake.stat", System.currentTimeMillis(), 9001.0)

    val recordOffset = 1L
    consumer.schedulePollTask {
      () => consumer.addRecord(new ConsumerRecord[String, Array[Byte]](testTopic, 0, recordOffset, "na", record.toByteArray))
    }

    task.run()

    mockRepository.records.length shouldEqual (1)

    val storedRecord = mockRepository.records.head

    storedRecord shouldEqual (record)
    // the offset that consumer should resume consuming from - hence + 1
    getCommittedOffset shouldEqual recordOffset + 1
  }

  it should "skip over invalid records" in {
    mockRepository.records = Seq.empty

    val recordOffset = 2L
    consumer.schedulePollTask { () =>
      consumer.addRecord(new ConsumerRecord(testTopic, 0, recordOffset, "bad record", Array[Byte](1)))}

    task.run()

    mockRepository.records shouldBe (empty)
    // the offset that consumer should resume consuming from - hence + 1
    getCommittedOffset shouldEqual recordOffset + 1
  }

  private def getCommittedOffset = {
    consumer.committed(Set(topicPartition).asJava).get(topicPartition).offset()
  }

  it should "not insert anything if there's no records to poll" in {
    mockRepository.records = Seq.empty
    val lastCommitedOffset = getCommittedOffset
    consumer.scheduleNopPollTask()

    task.run()
    val offsetAfterEmptyPoll = getCommittedOffset

    mockRepository.records shouldBe (empty)
    offsetAfterEmptyPoll shouldEqual lastCommitedOffset

  }


}
