package com.example.sparkv2.automation

/**
 * Scan/action delays across normal → turbo → aggressive → super-aggressive profiles.
 * Super-aggressive is experimental: minimum safe floors so Spark Driver does not freeze.
 */
data class SpeedConfig(
    val turbo: Boolean = false,
    val aggressive: Boolean = false,
    val superAggressive: Boolean = false,
) {
    val aggressiveTurbo: Boolean get() = turbo && aggressive && !superAggressive
    val superAggressiveTurbo: Boolean get() = turbo && aggressive && superAggressive
}

private enum class Tier { NORMAL, TURBO, AGGRESSIVE, SUPER }

private fun SpeedConfig.tier(): Tier = when {
    turbo && aggressive && superAggressive -> Tier.SUPER
    turbo && aggressive -> Tier.AGGRESSIVE
    turbo -> Tier.TURBO
    else -> Tier.NORMAL
}

object ScanTiming {
    fun defaultScanDelay(speed: SpeedConfig) = when (speed.tier()) {
        Tier.SUPER -> 8L
        Tier.AGGRESSIVE -> 15L
        Tier.TURBO -> 40L
        Tier.NORMAL -> 120L
    }

    fun windowStateDelay(speed: SpeedConfig) = when (speed.tier()) {
        Tier.SUPER -> 8L
        Tier.AGGRESSIVE -> 15L
        Tier.TURBO -> 35L
        Tier.NORMAL -> 100L
    }

    fun contentChangeDelay(speed: SpeedConfig) = when (speed.tier()) {
        Tier.SUPER -> 12L
        Tier.AGGRESSIVE -> 30L
        Tier.TURBO -> 80L
        Tier.NORMAL -> 200L
    }

    fun declineContentDelay(speed: SpeedConfig) = when (speed.tier()) {
        Tier.SUPER -> 8L
        Tier.AGGRESSIVE -> 15L
        Tier.TURBO -> 35L
        Tier.NORMAL -> 90L
    }

    fun offerOpenDelay(speed: SpeedConfig) = when (speed.tier()) {
        Tier.SUPER -> 35L
        Tier.AGGRESSIVE -> 50L
        Tier.TURBO -> 100L
        Tier.NORMAL -> 260L
    }

    fun acceptRetryDelay(speed: SpeedConfig) = when (speed.tier()) {
        Tier.SUPER -> 20L
        Tier.AGGRESSIVE -> 35L
        Tier.TURBO -> 70L
        Tier.NORMAL -> 180L
    }

    fun heartbeatInterval(speed: SpeedConfig) = when (speed.tier()) {
        Tier.SUPER -> 180L
        Tier.AGGRESSIVE -> 250L
        Tier.TURBO -> 400L
        Tier.NORMAL -> 700L
    }

    fun nextOfferDelay(speed: SpeedConfig) = when (speed.tier()) {
        Tier.SUPER -> 5L
        Tier.AGGRESSIVE -> 10L
        Tier.TURBO -> 25L
        Tier.NORMAL -> 60L
    }

    fun stuckBackoff(speed: SpeedConfig) = when (speed.tier()) {
        Tier.SUPER -> 300L
        Tier.AGGRESSIVE -> 400L
        Tier.TURBO -> 900L
        Tier.NORMAL -> 1_800L
    }

    fun stuckRepeatThreshold(speed: SpeedConfig) = when (speed.tier()) {
        Tier.SUPER -> 16
        Tier.AGGRESSIVE -> 10
        Tier.TURBO -> 6
        Tier.NORMAL -> 4
    }

    fun actionConfirmLock(speed: SpeedConfig) = when (speed.tier()) {
        Tier.SUPER -> 100L
        Tier.AGGRESSIVE -> 180L
        Tier.TURBO -> 300L
        Tier.NORMAL -> 550L
    }

    fun confirmRetryDelay(speed: SpeedConfig) = when (speed.tier()) {
        Tier.SUPER -> 20L
        Tier.AGGRESSIVE -> 35L
        Tier.TURBO -> 70L
        Tier.NORMAL -> 180L
    }

    fun declinePollInterval(speed: SpeedConfig) = when (speed.tier()) {
        Tier.SUPER -> 40L
        Tier.AGGRESSIVE -> 60L
        Tier.TURBO -> 120L
        Tier.NORMAL -> 280L
    }

    fun notificationScanDelay(speed: SpeedConfig) = when (speed.tier()) {
        Tier.SUPER -> 12L
        Tier.AGGRESSIVE -> 30L
        Tier.TURBO -> 100L
        Tier.NORMAL -> 280L
    }

    fun notificationDedup(speed: SpeedConfig) = when (speed.tier()) {
        Tier.SUPER -> 900L
        Tier.AGGRESSIVE -> 1_200L
        Tier.TURBO -> 2_000L
        Tier.NORMAL -> 3_000L
    }

    fun notificationFollowUps(speed: SpeedConfig): LongArray = when (speed.tier()) {
        Tier.SUPER -> longArrayOf(40L, 100L, 180L, 320L, 520L)
        Tier.AGGRESSIVE -> longArrayOf(80L, 180L, 350L, 600L)
        Tier.TURBO -> longArrayOf(200L, 450L, 900L)
        Tier.NORMAL -> longArrayOf(450L, 1_000L, 2_000L)
    }

    fun minScanInterval(speed: SpeedConfig) = when (speed.tier()) {
        Tier.SUPER -> 12L
        Tier.AGGRESSIVE -> 20L
        Tier.TURBO -> 50L
        Tier.NORMAL -> 120L
    }

    fun contentScanInterval(speed: SpeedConfig) = when (speed.tier()) {
        Tier.SUPER -> 18L
        Tier.AGGRESSIVE -> 40L
        Tier.TURBO -> 100L
        Tier.NORMAL -> 250L
    }

    fun actionLock(speed: SpeedConfig) = when (speed.tier()) {
        Tier.SUPER -> 140L
        Tier.AGGRESSIVE -> 220L
        Tier.TURBO -> 500L
        Tier.NORMAL -> 900L
    }

    fun offerSuppress(speed: SpeedConfig) = when (speed.tier()) {
        Tier.SUPER -> 1_000L
        Tier.AGGRESSIVE -> 1_500L
        Tier.TURBO -> 2_500L
        Tier.NORMAL -> 4_000L
    }

    fun alertBoostDuration(speed: SpeedConfig) = when (speed.tier()) {
        Tier.SUPER -> 18_000L
        Tier.AGGRESSIVE -> 15_000L
        Tier.TURBO -> 12_000L
        Tier.NORMAL -> 8_000L
    }

    fun gestureCooldown(speed: SpeedConfig) = when (speed.tier()) {
        Tier.SUPER -> 120L
        Tier.AGGRESSIVE -> 200L
        Tier.TURBO -> 500L
        Tier.NORMAL -> 900L
    }

    fun gestureStrokeMs(speed: SpeedConfig) = when (speed.tier()) {
        Tier.SUPER -> 20L
        Tier.AGGRESSIVE -> 25L
        Tier.TURBO -> 35L
        Tier.NORMAL -> 45L
    }

    fun ocrMinInterval(speed: SpeedConfig) = when (speed.tier()) {
        Tier.SUPER -> 350L
        Tier.AGGRESSIVE -> 500L
        Tier.TURBO -> 800L
        Tier.NORMAL -> 1_100L
    }

    fun hubRequestScanDelay(speed: SpeedConfig) = when (speed.tier()) {
        Tier.SUPER -> 25L
        Tier.AGGRESSIVE -> 50L
        Tier.TURBO -> 150L
        Tier.NORMAL -> 400L
    }

    fun openOfferLock(speed: SpeedConfig) = when (speed.tier()) {
        Tier.SUPER -> 70L
        Tier.AGGRESSIVE -> 120L
        Tier.TURBO -> 250L
        Tier.NORMAL -> 400L
    }

    /** Caps accessibility-tree walks — lower in super mode for snappier scans. */
    fun maxTreeNodes(speed: SpeedConfig) = when (speed.tier()) {
        Tier.SUPER -> 900
        Tier.AGGRESSIVE -> 1_000
        Tier.TURBO, Tier.NORMAL -> 1_200
    }
}
