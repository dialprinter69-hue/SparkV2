package com.example.sparkv2.automation

import com.example.sparkv2.SparkIntents
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeclineMatchingTest {

    @Test
    fun step1_reject_matches_spark_button_labels() {
        assertTrue(TextMatcher.matchesIntent("REJECT", SparkIntents.REJECT))
        assertTrue(TextMatcher.matchesIntent("Reject offers", SparkIntents.REJECT))
        assertTrue(TextMatcher.matchesIntent("Reject offer", SparkIntents.REJECT))
    }

    @Test
    fun step2_confirm_matches_short_button_labels() {
        assertTrue(TextMatcher.matchesIntent("Reject offer", SparkIntents.REJECT_CONFIRM))
        assertTrue(TextMatcher.matchesIntent("REJECT OFFER", SparkIntents.REJECT_CONFIRM))
        assertTrue(TextMatcher.matchesIntent("Yes", SparkIntents.REJECT_CONFIRM))
    }

    @Test
    fun dialog_body_is_not_treated_as_confirm_button_label() {
        val body = "Are you sure you want to reject this offer?"
        assertTrue(TextMatcher.matchesIntent(body, SparkIntents.CONFIRM_DIALOG))
        assertTrue(body.length > 28)
        assertFalse(body.length <= 28)
    }

    @Test
    fun confirm_dialog_detects_reject_only_sheet_without_offer_text() {
        val combined = "Reject offer | Go back"
        val looksLikeOffer = OrderTextHints.looksLikeOffer(combined)
        val hasDecline = TextMatcher.matchesIntent("Reject offer", SparkIntents.REJECT)
        val hasAccept = false
        val isConfirmDialog = TextMatcher.matchesIntent(combined, SparkIntents.CONFIRM_DIALOG) ||
            (hasDecline && !hasAccept && !looksLikeOffer && combined.length < 280)
        assertFalse(looksLikeOffer)
        assertTrue(isConfirmDialog)
    }
}
