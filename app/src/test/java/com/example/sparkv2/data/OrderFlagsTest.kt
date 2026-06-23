package com.example.sparkv2.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderFlagsTest {

    @Test
    fun asap_shopping_is_shop_and_deliver() {
        val flags = OrderFlagDetector.detect("ASAP Shopping | Earn \$18.00 | 6 mi")
        assertTrue(flags.shopAndDeliver)
        assertFalse(flags.curbside)
    }

    @Test
    fun curbside_pickup_is_not_shop_and_deliver() {
        val flags = OrderFlagDetector.detect("Curbside pickup | Earn \$18.00 | 6 mi")
        assertTrue(flags.curbside)
        assertFalse(flags.shopAndDeliver)
    }

    @Test
    fun shop_and_deliver_curbside_wins_over_curbside() {
        val flags = OrderFlagDetector.detect("Shop and deliver curbside | \$20 | 5 mi")
        assertTrue(flags.shopDeliverCurbside)
        assertFalse(flags.shopAndDeliver)
        assertFalse(flags.curbside)
    }

    @Test
    fun pharmacy_and_dotcom_detected() {
        val pharmacy = OrderFlagDetector.detect("Pharmacy order | \$12 | 3 mi")
        val dotcom = OrderFlagDetector.detect("Walmart.com order | \$15 | 4 mi")
        assertTrue(pharmacy.pharmacy)
        assertTrue(dotcom.dotcom)
    }

    @Test
    fun customer_return_detected() {
        val flags = OrderFlagDetector.detect("Customer return trip | \$10 | 2 mi")
        assertTrue(flags.customerReturns)
    }

    @Test
    fun generic_shopping_word_does_not_trigger_shop_and_deliver() {
        val flags = OrderFlagDetector.detect("Start shopping | \$18 | 6 mi")
        assertFalse(flags.shopAndDeliver)
    }
}
