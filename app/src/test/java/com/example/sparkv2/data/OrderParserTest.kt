package com.example.sparkv2.data

import com.example.sparkv2.data.CriterionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderParserTest {

    @Test
    fun parse_first_card_from_multi_offer_text() {
        val text = "Earn \$18.00 | 8.5 mi | 1 stop | Earn \$12.00 | 4.2 mi | 2 stops"
        val order = OrderParser.parseFirstCard(text)
        requireNotNull(order)
        assertEquals(18.00, order.price, 0.001)
        assertEquals(8.5f, order.distance!!, 0.01f)
    }

    @Test
    fun parses_basic_price_and_distance() {
        val order = OrderParser.parse("Earn \$18.50 | 8.5 mi | 1 drop-off")
        requireNotNull(order)
        assertEquals(18.50, order.price, 0.001)
        assertEquals(8.5f, order.distance!!, 0.01f)
        assertEquals(1, order.dropoffs)
    }

    @Test
    fun reads_store_name_and_number() {
        val order = OrderParser.parse(
            "\$22.82 estimate | 2 stops | 15.4 miles | ASAP Shopping | " +
                "Walmart LEOMINSTER #2964 | 11 JUNGLE RD",
        )
        requireNotNull(order)
        assertEquals("Walmart Leominster #2964", order.storeName)
    }

    @Test
    fun reads_store_name_without_number() {
        val order = OrderParser.parse("Earn \$18.00 | 8 mi | Walmart Fitchburg, MA")
        requireNotNull(order)
        assertEquals("Walmart Fitchburg", order.storeName)
    }

    @Test
    fun prefers_labeled_earnings_over_larger_cart_total() {
        val order = OrderParser.parse("Est. \$15.00 | Cart total \$142.30 | 6 mi | 24 items")
        requireNotNull(order)
        assertEquals(15.00, order.price, 0.001)
        assertEquals(24, order.itemCount)
    }

    @Test
    fun ignores_cart_total_without_earnings_label() {
        val order = OrderParser.parse("Cart total \$142.30 | 6 mi | 24 items | Earn \$15.00")
        requireNotNull(order)
        assertEquals(15.00, order.price, 0.001)
    }

    @Test
    fun prefers_trip_distance_over_deadhead() {
        val order = OrderParser.parse("Earn \$18.00 | 2.1 mi to pickup | 8.5 mi | 1 stop")
        requireNotNull(order)
        assertEquals(8.5f, order.distance!!, 0.01f)
        assertEquals(2.1f, order.storeDistance!!, 0.01f)
    }

    @Test
    fun unknown_distance_does_not_fail_per_mile_filter() {
        val settings = SparkSettings(dollarsPerMile = 2.5f)
        val order = OrderParser.parse("Earn \$18.00 | 1 stop")!!
        assertNull(order.distance)
        val eval = OfferEvaluator.evaluate(order, settings)
        assertTrue(eval.criteria.first { it.id == CriterionId.DOLLARS_PER_MILE }.passed)
    }

    @Test
    fun parses_tip_and_derives_base() {
        val order = OrderParser.parse("You'll earn \$22.00 includes \$16.00 in tips · 7.2 mi")
        requireNotNull(order)
        assertEquals(22.00, order.price, 0.001)
        assertEquals(16.00, order.tip!!, 0.001)
        assertEquals(6.00, order.effectiveBasePay!!, 0.001)
        assertEquals(0.727, order.tipRatio!!, 0.01)
    }

    @Test
    fun parses_base_and_derives_tip() {
        val order = OrderParser.parse("Pay \$20.00 | Base pay \$8.00 | 5 mi")
        requireNotNull(order)
        assertEquals(8.00, order.basePay!!, 0.001)
        assertEquals(12.00, order.tip!!, 0.001)
    }

    @Test
    fun parses_time_minutes_and_dollars_per_hour() {
        val order = OrderParser.parse("Earn \$20.00 | 30 min | 6 mi")
        requireNotNull(order)
        assertEquals(30, order.estimatedMinutes)
        assertEquals(40.0, order.dollarsPerHour!!, 0.001)
    }

    @Test
    fun parses_hours_and_minutes() {
        val order = OrderParser.parse("Earn \$30.00 | 1 hr 20 min | 12 mi")
        requireNotNull(order)
        assertEquals(80, order.estimatedMinutes)
    }

    @Test
    fun tip_guard_rejects_high_tip_share() {
        val settings = SparkSettings(maxTipRatio = 0.7f)
        val order = OrderParser.parse("Earn \$22.00 includes \$18.00 in tips · 5 mi")!!
        val eval = OfferEvaluator.evaluate(order, settings)
        assertFalse(eval.passes)
        assertTrue(eval.failingCriteria.any { it.id == CriterionId.MAX_TIP_RATIO })
    }

    @Test
    fun per_hour_floor_rejects_slow_offer() {
        val settings = SparkSettings(minDollarsPerHour = 25f)
        val order = OrderParser.parse("Earn \$10.00 | 40 min | 5 mi")!!
        val eval = OfferEvaluator.evaluate(order, settings)
        assertFalse(eval.passes)
        assertTrue(eval.failingCriteria.any { it.id == CriterionId.DOLLARS_PER_HOUR })
    }

    @Test
    fun unknown_field_does_not_reject() {
        val settings = SparkSettings(minDollarsPerHour = 25f)
        val order = OrderParser.parse("Earn \$10.00 | 5 mi")!!
        val eval = OfferEvaluator.evaluate(order, settings)
        assertNull(order.estimatedMinutes)
        assertTrue(eval.criteria.first { it.id == CriterionId.DOLLARS_PER_HOUR }.passed)
    }

    @Test
    fun item_limit_off_by_default_keeps_large_orders() {
        val settings = SparkSettings()
        val order = OrderParser.parse("Est. \$18.00 | 60 items | 5 mi")!!
        val eval = OfferEvaluator.evaluate(order, settings)
        assertTrue(eval.criteria.none { it.id == CriterionId.MAX_ITEMS })
    }

    @Test
    fun item_limit_when_enabled_rejects_over_cap() {
        val settings = SparkSettings(itemLimitEnabled = true, maxItems = 40)
        val order = OrderParser.parse("Est. \$18.00 | 60 items | 5 mi")!!
        val eval = OfferEvaluator.evaluate(order, settings)
        assertFalse(eval.passes)
        assertTrue(eval.failingCriteria.any { it.id == CriterionId.MAX_ITEMS })
    }

    @Test
    fun parses_earnings_without_dollar_sign() {
        val order = OrderParser.parse("You'll make 18.50 | 6.2 mi | 1 stop")
        requireNotNull(order)
        assertEquals(18.50, order.price, 0.001)
        assertEquals(6.2f, order.distance!!, 0.01f)
    }

    @Test
    fun parses_trailing_estimate_price() {
        val order = OrderParser.parse("8.5 mi | 1 stop | 22.82 estimate")
        requireNotNull(order)
        assertEquals(22.82, order.price, 0.001)
        assertEquals(8.5f, order.distance!!, 0.01f)
    }

    @Test
    fun parse_best_segment_from_piped_fields() {
        val order = OrderParser.parseBestSegment(
            "\$22.82 estimate | 2 stops | 15.4 miles | ASAP Shopping | Walmart LEOMINSTER #2964",
        )
        requireNotNull(order)
        assertEquals(22.82, order.price, 0.001)
        assertEquals(15.4f, order.distance!!, 0.01f)
        assertEquals(2, order.dropoffs)
    }

    @Test
    fun parses_miles_leading_distance_label() {
        val order = OrderParser.parse("Earn \$18.00 | miles: 7.3 | 1 stop")
        requireNotNull(order)
        assertEquals(7.3f, order.distance!!, 0.01f)
    }

    @Test
    fun parses_spanish_earnings_label() {
        val order = OrderParser.parse("Ganarás \$19.25 | 5.1 mi | 1 entrega")
        requireNotNull(order)
        assertEquals(19.25, order.price, 0.001)
    }
}
