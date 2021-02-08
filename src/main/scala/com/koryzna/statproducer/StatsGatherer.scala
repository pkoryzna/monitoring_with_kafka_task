package com.koryzna.statproducer

import oshi.SystemInfo
import com.koryzna.statproducer.model.stats.StatsRecord

class StatsGatherer {
  protected val systemInfo = new SystemInfo()

  val machineName: String = systemInfo.getOperatingSystem.getNetworkParams.getHostName

  /**
   * Collects system stats values that we're interested in.
   * @return list of key, value pairs representing current state of the monitored system
   */
  def getCurrentStats(): List[StatsRecord] = {
    val epochTimestamp = System.currentTimeMillis()
    List(
      StatsRecord(machineName, "memory.available", epochTimestamp, systemInfo.getHardware.getMemory.getAvailable.toDouble),
      StatsRecord(machineName, "processes.count", epochTimestamp, systemInfo.getOperatingSystem.getProcessCount.toDouble),
      // which stats to track could be read from a config file in a real proper monitoring system,
      // but I think these will do for this exercise
    )
  }
}