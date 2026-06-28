package com.example.sparkv2.automation

import com.example.sparkv2.service.SparkAccessibilityService

object SparkAutomationHub {
    @Volatile
    var service: SparkAccessibilityService? = null

    /** Mirrors [SparkSettings.turboMode] so timing helpers can read it without a Context. */
    @Volatile
    var turboMode: Boolean = false

    fun speed(): SpeedConfig = SpeedConfig(turboMode)

    /** After a Spark alert, scans run faster for this window. */
    @Volatile
    var alertBoostUntilMs: Long = 0L

    fun markAlertBoost(durationMs: Long? = null) {
        val duration = durationMs ?: ScanTiming.alertBoostDuration(speed())
        alertBoostUntilMs = System.currentTimeMillis() + duration
    }

    fun isAlertBoostActive(nowMs: Long = System.currentTimeMillis()): Boolean {
        return nowMs < alertBoostUntilMs
    }

    fun requestScan(reason: String, delayMs: Long? = null) {
        val delay = delayMs ?: ScanTiming.hubRequestScanDelay(speed())
        service?.scheduleScan(reason, delay)
    }
}
