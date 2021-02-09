package com.koryzna.statconsumer

import com.typesafe.config.Config

// using java.time.Duration explicitly since this will deal with JDK-style time
case class ConsumerAppConfig(
                              terminationTimeout: java.time.Duration,
                              pollingPeriod: java.time.Duration,
                              commitTimeout: java.time.Duration,
                            )

object ConsumerAppConfig {
  def fromConfig(config: Config): ConsumerAppConfig = {
    new ConsumerAppConfig(
      terminationTimeout = config.getDuration("statconsumer.termination.timeout"),
      pollingPeriod = config.getDuration("statconsumer.polling.period"),
      commitTimeout = config.getDuration("statconsumer.commit.timeout"),
    )
  }
}


case class DbConfig(url: String, user: String, password: String) {
  override def toString: String = s"DbConfig($url, $user, ***)"
}

object DbConfig {
  def fromConfig(config: Config): DbConfig = {
    new DbConfig(
      url = config.getString("statconsumer.db.url"),
      user = config.getString("statconsumer.db.user"),
      password = config.getString("statconsumer.db.password"),
    )
  }
}