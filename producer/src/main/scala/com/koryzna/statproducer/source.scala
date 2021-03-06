package com.koryzna.statproducer

import oshi.SystemInfo
import com.koryzna.statproducer.model.stats.StatsRecord

/** Interface for collecting current system statistics. */
trait StatsSource {
  def getCurrentStats(): List[StatsRecord]
}


/** Simple system statistics source based on OSHI library */
class OSHIStatsSource extends StatsSource {

  // This will be eventually called in context a scheduled task, but should be fine:
  // https://github.com/oshi/oshi/blob/master/FAQ.md#is-oshi-thread-safe
  protected val systemInfo = new SystemInfo()

  protected val machineName: String = systemInfo.getOperatingSystem.getNetworkParams.getHostName

  /**
   * Collects system stats values that we're interested in.
   * @return list of key, value pairs representing current state of the monitored system
   */
  def getCurrentStats(): List[StatsRecord] = {
    val epochTimestamp = System.currentTimeMillis()
    List(
      StatsRecord(machineName, "memory.available", epochTimestamp, systemInfo.getHardware.getMemory.getAvailable.toDouble),
      StatsRecord(machineName, "processes.count", epochTimestamp, systemInfo.getOperatingSystem.getProcessCount.toDouble),
      // In this exercise I'll be collection only those two.
      // Which stats to track could be read from a config file in a real proper monitoring system.
    )
  }
}