package com.example.sparkv2.automation

/**
 * Scan/action delays across two profiles: normal and turbo.
 * Turbo uses the lowest safe floor delays for the fastest possible accept/decline — the limit is
 * what Spark Driver can take before it freezes or starts missing taps.
 */
data class SpeedConfig(
    val turbo: Boolean = false,
)

private enum class Tier { NORMAL, TURBO }

private fun SpeedConfig.tier(): Tier = if (turbo) Tier.TURBO else Tier.NORMAL

object ScanTiming {
    fun defaultScanDelay(speed: SpeedConfig) = when (speed.tier()) {
        Tier.TURBO -> 8L
        Tier.NORMAL -> 120L
    }

    fun windowStateDelay(speed: SpeedConfig) = when (speed.tier()) {
        Tier.TURBO -> 8L
        Tier.NORMAL -> 100L
    }

    fun contentChangeDelay(speed: SpeedConfig) = when (speed.tier()) {
        Tier.TURBO -> 12L
        Tier.NORMAL -> 200L
    }

    fun declineContentDelay(speed: SpeedConfig) = when (speed.tier()) {
        Tier.TURBO -> 8L
        Tier.NORMAL -> 90L
    }

    fun offerOpenDelay(speed: SpeedConfig) = when (speed.tier()) {
        Tier.TURBO -> 35L
        Tier.NORMAL -> 260L
    }

    fun acceptRetryDelay(speed: SpeedConfig) = when (speed.tier()) {
        Tier.TURBO -> 20L
        Tier.NORMAL -> 180L
    }

    fun heartbeatInterval(speed: SpeedConfig) = when (speed.tier()) {
        Tier.TURBO -> 180L
        Tier.NORMAL -> 700L
    }

    fun nextOfferDelay(speed: SpeedConfig) = when (speed.tier()) {
        Tier.TURBO -> 5L
        Tier.NORMAL -> 60L
    }

    fun stuckBackoff(speed: SpeedConfig) = when (speed.tier()) {
        Tier.TURBO -> 300L
        Tier.NORMAL -> 1_800L
    }

    fun stuckRepeatThreshold(speed: SpeedConfig) = when (speed.tier()) {
        Tier.TURBO -> 16
        Tier.NORMAL -> 4
    }

    fun actionConfirmLock(speed: SpeedConfig) = when (speed.tier()) {
        Tier.TURBO -> 100L
        Tier.NORMAL -> 550L
    }

    fun confirmRetryDelay(speed: SpeedConfig) = when (speed.tier()) {
        Tier.TURBO -> 20L
        Tier.NORMAL -> 180L
    }

    fun declinePollInterval(speed: SpeedConfig) = when (speed.tier()) {
        Tier.TURBO -> 40L
        Tier.NORMAL -> 280L
    }

    fun notificationScanDelay(speed: SpeedConfig) = when (speed.tier()) {
        Tier.TURBO -> 12L
        Tier.NORMAL -> 280L
    }

    fun notificationDedup(speed: SpeedConfig) = when (speed.tier()) {
        Tier.TURBO -> 900L
        Tier.NORMAL -> 3_000L
    }

    fun notificationFollowUps(speed: SpeedConfig): LongArray = when (speed.tier()) {
        Tier.TURBO -> longArrayOf(40L, 100L, 180L, 320L, 520L)
        Tier.NORMAL -> longArrayOf(450L, 1_000L, 2_000L)
    }

    fun minScanInterval(speed: SpeedConfig) = when (speed.tier()) {
        Tier.TURBO -> 12L
        Tier.NORMAL -> 120L
    }

    fun contentScanInterval(speed: SpeedConfig) = when (speed.tier()) {
        Tier.TURBO -> 18L
        Tier.NORMAL -> 250L
    }

    fun actionLock(speed: SpeedConfig) = when (speed.tier()) {
        Tier.TURBO -> 140L
        Tier.NORMAL -> 900L
    }

    fun offerSuppress(speed: SpeedConfig) = when (speed.tier()) {
        Tier.TURBO -> 1_000L
        Tier.NORMAL -> 4_000L
    }

    fun alertBoostDuration(speed: SpeedConfig) = when (speed.tier()) {
        Tier.TURBO -> 18_000L
        Tier.NORMAL -> 8_000L
    }

    fun gestureCooldown(speed: SpeedConfig) = when (speed.tier()) {
        Tier.TURBO -> 120L
        Tier.NORMAL -> 900L
    }

    fun gestureStrokeMs(speed: SpeedConfig) = when (speed.tier()) {
        Tier.TURBO -> 20L
        Tier.NORMAL -> 45L
    }

    fun ocrMinInterval(speed: SpeedConfig) = when (speed.tier()) {
        Tier.TURBO -> 350L
        Tier.NORMAL -> 1_100L
    }

    fun hubRequestScanDelay(speed: SpeedConfig) = when (speed.tier()) {
        Tier.TURBO -> 25L
        Tier.NORMAL -> 400L
    }

    fun openOfferLock(speed: SpeedConfig) = when (speed.tier()) {
        Tier.TURBO -> 70L
        Tier.NORMAL -> 400L
    }

    /** Caps accessibility-tree walks — lower in turbo for snappier scans. */
    fun maxTreeNodes(speed: SpeedConfig) = when (speed.tier()) {
        Tier.TURBO -> 900
        Tier.NORMAL -> 1_200
    }
}
