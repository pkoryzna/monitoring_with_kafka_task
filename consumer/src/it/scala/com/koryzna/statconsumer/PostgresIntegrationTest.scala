package com.koryzna.statconsumer

import com.koryzna.statproducer.model.stats.StatsRecord
import com.typesafe.config.ConfigFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers
import matchers.should._
import org.flywaydb.core.Flyway
import scalikejdbc._


class PostgresIntegrationTest extends AnyFlatSpec with Matchers {
  val dbConfig = DbConfig("jdbc:postgresql://localhost:5432/postgres","postgres","P@ssw0rd")

  val records = List(
    StatsRecord("hal9001", "fake.cpu", System.currentTimeMillis(), 9999.0),
    StatsRecord("hal9001", "fake.cpu", System.currentTimeMillis() + 1000, 1337.0),
  )

  ConnectionPool.singleton(dbConfig.url, dbConfig.user, dbConfig.password)

  "DB migrations" should "succeed with real PostgreSQL server" in {
    noException should be thrownBy {
      val flyway = Flyway.configure().dataSource(dbConfig.url, dbConfig.user, dbConfig.password).load()
      flyway.migrate()
    }
  }

  "JDBC repository" should "insert records" in {
    noException should be thrownBy { JDBCRepository.insertStatsRecords(records) }

    val results = selectAll()
    results should have length(2)
    results should contain allElementsOf(records)
  }

  it should "not insert duplicated records" in {
    noException should be thrownBy { JDBCRepository.insertStatsRecords(records) }

    val results = selectAll()
    results should have length(2)
    results should contain allElementsOf(records)
  }

  private def selectAll() = {
    DB.readOnly { implicit session =>
      sql"SELECT machine_name, stat_name, machine_timestamp_unix, stat_value FROM stats_app.recorded_stats ORDER BY machine_timestamp_unix".map { rs =>
        StatsRecord(rs.get("machine_name"), rs.get("stat_name"), rs.get("machine_timestamp_unix"), rs.get("stat_value"))
      }.list().apply()
    }
  }
}
