package com.koryzna.statconsumer

import com.koryzna.statproducer.model.stats.StatsRecord

import java.time.{Instant, ZoneId, ZonedDateTime}

trait StatsRepository {
  def insertStatsRecords(statsRecords: List[StatsRecord]): Unit
}


object JDBCRepository extends StatsRepository {

  override def insertStatsRecords(statsRecords: List[StatsRecord]): Unit = {
    import scalikejdbc._

    val insertTimestamp = Instant.now().atZone(ZoneId.of("UTC"))
    DB.autoCommit { implicit session =>
      val recordBatch = statsRecordsToBatch(statsRecords, insertTimestamp)
      val res = sql"""
         INSERT INTO stats_app.recorded_stats VALUES
         (
          ?,
          ?,
          ?,
          ?,
          ?
         )
         ON CONFLICT DO NOTHING
   """.batch(recordBatch : _*).apply()
    }
  }

  private def statsRecordsToBatch(statsRecords: List[StatsRecord], insertTimestamp: ZonedDateTime): Seq[Seq[Any]] = {
    for {
      record <- statsRecords
    } yield List(
      record.machineName,
      record.statName,
      record.machineEpochTimestamp,
      record.value,
      insertTimestamp
    )
  }
}
