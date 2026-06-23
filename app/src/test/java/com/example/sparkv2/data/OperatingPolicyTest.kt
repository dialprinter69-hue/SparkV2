package com.example.sparkv2.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OperatingPolicyTest {

    @Test
    fun no_policy_means_never_paused() {
        val s = SparkSettings()
        assertNull(OperatingPolicy.pauseReason(s, todayEarnings = 999.0, hourOfDay = 3))
    }

    @Test
    fun goal_pauses_when_reached() {
        val s = SparkSettings(earningsGoal = 150f)
        assertNull(OperatingPolicy.pauseReason(s, todayEarnings = 149.99, hourOfDay = 12))
        assertEquals(
            OperatingPolicy.PauseReason.GOAL_REACHED,
            OperatingPolicy.pauseReason(s, todayEarnings = 150.0, hourOfDay = 12),
        )
    }

    @Test
    fun outside_hours_pauses() {
        val s = SparkSettings(operatingHoursEnabled = true, startHour = 8, endHour = 22)
        assertTrue(OperatingPolicy.withinHours(s, 8))
        assertTrue(OperatingPolicy.withinHours(s, 21))
        assertFalse(OperatingPolicy.withinHours(s, 22)) // end is exclusive
        assertFalse(OperatingPolicy.withinHours(s, 7))
        assertEquals(
            OperatingPolicy.PauseReason.OUTSIDE_HOURS,
            OperatingPolicy.pauseReason(s, todayEarnings = 0.0, hourOfDay = 23),
        )
    }

    @Test
    fun window_wraps_past_midnight() {
        val s = SparkSettings(operatingHoursEnabled = true, startHour = 22, endHour = 6)
        assertTrue(OperatingPolicy.withinHours(s, 23))
        assertTrue(OperatingPolicy.withinHours(s, 2))
        assertFalse(OperatingPolicy.withinHours(s, 12))
    }

    @Test
    fun equal_start_end_is_24h() {
        val s = SparkSettings(operatingHoursEnabled = true, startHour = 9, endHour = 9)
        assertTrue(OperatingPolicy.withinHours(s, 3))
    }
}
