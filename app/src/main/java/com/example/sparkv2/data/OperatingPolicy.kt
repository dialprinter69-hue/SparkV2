package com.example.sparkv2.data

/**
 * Decides whether the bot should pause acting on offers because the driver's earnings goal was
 * reached or the current time is outside the configured operating window. Pure logic so it can be
 * unit-tested without an Android clock.
 */
object OperatingPolicy {

    enum class PauseReason { GOAL_REACHED, OUTSIDE_HOURS }

    fun pauseReason(
        settings: SparkSettings,
        todayEarnings: Double,
        hourOfDay: Int,
    ): PauseReason? {
        if (!withinHours(settings, hourOfDay)) return PauseReason.OUTSIDE_HOURS
        if (settings.earningsGoal > 0f && todayEarnings >= settings.earningsGoal) {
            return PauseReason.GOAL_REACHED
        }
        return null
    }

    /** True when [hourOfDay] (0-23) is inside [SparkSettings.startHour] until end (exclusive). */
    fun withinHours(settings: SparkSettings, hourOfDay: Int): Boolean {
        if (!settings.operatingHoursEnabled) return true
        val start = settings.startHour.coerceIn(0, 23)
        val end = settings.endHour.coerceIn(1, 24)
        if (start == end) return true // treat as 24h to avoid locking the bot out entirely
        return if (start < end) {
            hourOfDay in start until end
        } else {
            // Window wraps past midnight, e.g. 22 → 6.
            hourOfDay >= start || hourOfDay < end
        }
    }
}
