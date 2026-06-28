package com.example.sparkv2.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SparkSettingsTest {

    @Test
    fun resetOfferFilters_preservesAutomationToggles() {
        val settings = SparkSettings(
            enabled = true,
            autoAccept = false,
            autoDecline = false,
            ocrFallbackEnabled = false,
            turboMode = true,
            debugDump = true,
            dollarsPerMile = 2.5f,
            minPay = 15f,
            stores = listOf(StoreFilter(name = "Test #123", storeId = 123)),
            quickMode = QuickMode.BALANCED,
        )

        val reset = settings.resetOfferFilters()

        assertTrue(reset.enabled)
        assertFalse(reset.autoAccept)
        assertFalse(reset.autoDecline)
        assertFalse(reset.ocrFallbackEnabled)
        assertTrue(reset.turboMode)
        assertTrue(reset.debugDump)
        assertEquals(0f, reset.dollarsPerMile)
        assertEquals(0f, reset.minPay)
        assertTrue(reset.stores.isEmpty())
        assertEquals(null, reset.quickMode)
        assertEquals(StoreFilterMode.ANY, reset.storeFilterMode)
    }

    @Test
    fun resetOfferFilters_restoresOrderTypeAndTagDefaults() {
        val settings = SparkSettings(
            shopAndDeliver = false,
            alcohol = false,
            apartment = false,
        )

        val reset = settings.resetOfferFilters()

        assertTrue(reset.shopAndDeliver)
        assertTrue(reset.alcohol)
        assertTrue(reset.apartment)
    }
}
