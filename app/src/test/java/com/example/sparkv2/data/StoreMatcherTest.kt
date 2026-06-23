package com.example.sparkv2.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreMatcherTest {

    private val leominsterOffer =
        "\$22.82 estimate | 2 stops | 15.4 miles | ASAP Shopping | " +
            "Walmart LEOMINSTER #2964 | 11 JUNGLE RD"

    @Test
    fun matches_store_number_from_catalog_entry() {
        val store = StoreFilter(name = "Leominster, MA #2964", storeId = 2964)
        assertTrue(StoreMatcher.matches(leominsterOffer, store))
    }

    @Test
    fun matches_legacy_city_keyword_without_store_id() {
        val store = StoreFilter(name = "Leominster")
        assertTrue(StoreMatcher.matches(leominsterOffer, store))
    }

    @Test
    fun store_number_match_does_not_false_positive_on_partial_digits() {
        val store = StoreFilter(name = "Store #296", storeId = 296)
        assertFalse(StoreMatcher.matches(leominsterOffer, store))
    }

    @Test
    fun passes_only_selected_when_catalog_store_matches() {
        val settings = SparkSettings(
            storeFilterMode = StoreFilterMode.ONLY_SELECTED,
            stores = listOf(StoreFilter(name = "Leominster, MA #2964", storeId = 2964)),
        )
        assertTrue(StoreMatcher.passes(leominsterOffer, settings.storeFilterMode, settings.stores))
    }

    @Test
    fun fails_only_selected_for_unlisted_store() {
        val settings = SparkSettings(
            storeFilterMode = StoreFilterMode.ONLY_SELECTED,
            stores = listOf(StoreFilter(name = "Fitchburg, MA #1234", storeId = 1234)),
        )
        assertFalse(StoreMatcher.passes(leominsterOffer, settings.storeFilterMode, settings.stores))
    }

    @Test
    fun first_match_prefers_first_enabled_store() {
        val stores = listOf(
            StoreFilter(name = "Other", storeId = 1, enabled = false),
            StoreFilter(name = "Leominster, MA #2964", storeId = 2964),
        )
        assertEquals("Leominster, MA #2964", StoreMatcher.firstMatch(leominsterOffer, stores)?.name)
    }

    @Test
    fun any_mode_always_passes() {
        val settings = SparkSettings(
            storeFilterMode = StoreFilterMode.ANY,
            stores = listOf(StoreFilter(name = "Nowhere", storeId = 1)),
        )
        assertTrue(StoreMatcher.passes(leominsterOffer, settings.storeFilterMode, settings.stores))
    }

    @Test
    fun only_selected_with_no_stores_does_not_block() {
        val settings = SparkSettings(
            storeFilterMode = StoreFilterMode.ONLY_SELECTED,
            stores = emptyList(),
        )
        assertTrue(StoreMatcher.passes(leominsterOffer, settings.storeFilterMode, settings.stores))
    }

    @Test
    fun no_match_returns_null() {
        assertNull(
            StoreMatcher.firstMatch(
                "Walmart DALLAS #100",
                listOf(StoreFilter(name = "Leominster, MA #2964", storeId = 2964)),
            ),
        )
    }
}
