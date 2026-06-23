package com.example.sparkv2.automation

/**
 * Prevents duplicate taps and overlapping accept/decline actions on the same offer.
 */
object ActionGate {
    @Volatile private var busyUntilMs = 0L
    @Volatile private var lastHandledFingerprint: String? = null
    @Volatile private var lastHandledAtMs = 0L

    fun isBusy(nowMs: Long = System.currentTimeMillis()): Boolean = nowMs < busyUntilMs

    fun tryLock(
        durationMs: Long? = null,
        speed: SpeedConfig = SparkAutomationHub.speed(),
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val lockMs = durationMs ?: ScanTiming.actionLock(speed)
        if (nowMs < busyUntilMs) return false
        busyUntilMs = nowMs + lockMs
        return true
    }

    fun release() {
        busyUntilMs = 0L
    }

    fun shouldHandleOffer(
        fingerprint: String,
        speed: SpeedConfig = SparkAutomationHub.speed(),
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (fingerprint == lastHandledFingerprint &&
            nowMs - lastHandledAtMs < ScanTiming.offerSuppress(speed)
        ) {
            return false
        }
        return true
    }

    fun markHandled(fingerprint: String, nowMs: Long = System.currentTimeMillis()) {
        lastHandledFingerprint = fingerprint
        lastHandledAtMs = nowMs
    }

    fun resetOfferMemory() {
        lastHandledFingerprint = null
        lastHandledAtMs = 0L
    }
}
