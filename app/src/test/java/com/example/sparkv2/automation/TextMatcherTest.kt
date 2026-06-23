package com.example.sparkv2.automation

import com.example.sparkv2.SparkIntents
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextMatcherTest {

    @Test
    fun accept_matches_timer_and_accents() {
        assertTrue(TextMatcher.matchesIntent("Accept 0:45", SparkIntents.ACCEPT))
        assertTrue(TextMatcher.matchesIntent("Aceptar", SparkIntents.ACCEPT))
        assertFalse(TextMatcher.matchesIntent("ACCEPTING", SparkIntents.ACCEPT))
        assertFalse(TextMatcher.matchesIntent("Acceptance rate 92%", SparkIntents.ACCEPT))
    }

    @Test
    fun reject_matches_variants() {
        assertTrue(TextMatcher.matchesIntent("REJECT", SparkIntents.REJECT))
        assertTrue(TextMatcher.matchesIntent("Reject offers", SparkIntents.REJECT))
        assertTrue(TextMatcher.matchesIntent("Not now", SparkIntents.REJECT))
        assertTrue(TextMatcher.matchesIntent("Rechazar", SparkIntents.REJECT))
        assertFalse(TextMatcher.matchesIntent("Declining trip", SparkIntents.REJECT))
    }

    @Test
    fun offer_card_matches_plural_phrases() {
        assertTrue(TextMatcher.matchesIntent("View offers", SparkIntents.OFFER_CARD))
        assertTrue(TextMatcher.matchesIntent("New trips available", SparkIntents.OFFER_CARD))
        assertTrue(TextMatcher.containsFuzzy("incoming delivery offer", "delivery offer"))
    }

    @Test
    fun normalize_strips_accents_and_case() {
        assertTrue(TextMatcher.containsFuzzy("Sí, rechazar", "si rechazar"))
    }
}
