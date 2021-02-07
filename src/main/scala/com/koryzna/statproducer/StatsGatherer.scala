package com.koryzna.statproducer

import com.koryzna.statproducer.StatsGatherer.key
import oshi.SystemInfo


class StatsGatherer {
  protected val systemInfo = new SystemInfo()

  val machineName: String = systemInfo.getOperatingSystem.getNetworkParams.getHostName

  /**
   * Collects system stats values that we're interested in.
   * @return list of key, value pairs representing current state of the monitored system
   */
  def getCurrentStats(): List[(String, Double)] = {
    List(
      key(machineName, "memory.available") -> systemInfo.getHardware.getMemory.getAvailable.toDouble,
      key(machineName, "processes.count") -> systemInfo.getOperatingSystem.getProcessCount.toDouble,
      // which stats to track could be read from a config file in a real proper monitoring system,
      // but I think these will do for this exercise
    )
  }
}

object StatsGatherer {
  def key(machineName: String, statName: String): String = s"$statName$$$machineName"
}
