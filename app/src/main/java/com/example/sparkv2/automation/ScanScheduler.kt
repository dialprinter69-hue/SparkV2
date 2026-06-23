package com.example.sparkv2.automation

/**
 * Coalesces accessibility scans so Spark Driver is not hammered on every UI tick.
 */
class ScanScheduler {
    private var lastScanAtMs = 0L
    private var lastContentScanAtMs = 0L

    fun canRunScan(speed: SpeedConfig = SparkAutomationHub.speed(), nowMs: Long = System.currentTimeMillis()): Boolean {
        return nowMs - lastScanAtMs >= ScanTiming.minScanInterval(speed)
    }

    fun canRunContentScan(speed: SpeedConfig = SparkAutomationHub.speed(), nowMs: Long = System.currentTimeMillis()): Boolean {
        return nowMs - lastContentScanAtMs >= ScanTiming.contentScanInterval(speed)
    }

    fun markScan(nowMs: Long = System.currentTimeMillis()) {
        lastScanAtMs = nowMs
        lastContentScanAtMs = nowMs
    }

    fun markContentScan(nowMs: Long = System.currentTimeMillis()) {
        lastContentScanAtMs = nowMs
    }
}
