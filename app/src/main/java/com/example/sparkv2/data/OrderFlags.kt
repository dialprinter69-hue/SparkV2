package com.example.sparkv2.data

import com.example.sparkv2.automation.TextIntent
import com.example.sparkv2.automation.TextMatcher

data class OrderFlags(
    val shopDeliverCurbside: Boolean = false,
    val shopAndDeliver: Boolean = false,
    val curbside: Boolean = false,
    val pharmacy: Boolean = false,
    val dotcom: Boolean = false,
    val customerReturns: Boolean = false,
    val bulkyItem: Boolean = false,
    val shopperBulk: Boolean = false,
    val apartment: Boolean = false,
    val customerVerification: Boolean = false,
    val alcohol: Boolean = false,
    val heavy: Boolean = false,
)

data class ParsedOrder(
    /** Total estimated earnings shown on the offer (base + tip). */
    val price: Double,
    /** Trip distance in miles, or null when the screen didn't expose a parseable distance. */
    val distance: Float?,
    val dropoffs: Int,
    val flags: OrderFlags,
    /** Normalized offer text, kept for store/location matching and diagnostics. */
    val text: String = "",
    /** Guaranteed base pay (excludes tip), or null when not shown. */
    val basePay: Double? = null,
    /** Estimated/customer tip portion of the payout, or null when not shown. */
    val tip: Double? = null,
    /** Estimated trip duration in minutes, or null when not shown. */
    val estimatedMinutes: Int? = null,
    /**
     * Number of items to shop (Shop & Deliver). Best-effort from the offer card text only —
     * the precise count usually lives behind a detail tap, which we deliberately avoid so we
     * don't slow down offer capture. Null when not visible on the card.
     */
    val itemCount: Int? = null,
    /** "Deadhead" distance to the pickup/store in miles, or null when not shown. */
    val storeDistance: Float? = null,
    /** Pickup store as read from the offer, e.g. "Walmart Leominster #2964". Null when not shown. */
    val storeName: String? = null,
) {
    /** Estimated $/hour using the total payout and the trip duration, when both are known. */
    val dollarsPerHour: Double?
        get() = estimatedMinutes?.takeIf { it > 0 }?.let { price / (it / 60.0) }

    /** Tip as a fraction of the total payout (0..1), when both tip and a positive total exist. */
    val tipRatio: Double?
        get() = tip?.takeIf { price > 0.0 }?.let { (it / price).coerceIn(0.0, 1.0) }

    /** Base pay, falling back to total − tip when the base isn't shown directly. */
    val effectiveBasePay: Double?
        get() = basePay ?: tip?.let { (price - it).takeIf { v -> v >= 0.0 } }
}

object OrderFlagDetector {
    private val SHOP_DELIVER_CURBSIDE = TextIntent(
        phrases = listOf(
            "shop and deliver curbside",
            "shop deliver curbside",
            "shop & deliver curbside",
            "shopper deliver curbside",
            "comprar y entregar curbside",
        ),
        minRootLength = 4,
    )

    private val SHOP_AND_DELIVER = TextIntent(
        phrases = listOf(
            "shop and deliver",
            "shop & deliver",
            "shop deliver",
            "asap shopping",
            "asap shop",
            "in-store shopping",
            "in store shopping",
            "shop in store",
            "shopping trip",
            "shopper trip",
            "comprar y entregar",
        ),
        roots = listOf("shopper"),
        excludes = listOf("curbside pickup only", "curbside only"),
        minRootLength = 5,
    )

    private val CURBSIDE = TextIntent(
        roots = listOf("curbside", "acera"),
        phrases = listOf(
            "curbside pickup",
            "curbside delivery",
            "curbside order",
            "en la acera",
        ),
        excludes = listOf("shop and deliver curbside", "shop deliver curbside"),
        minRootLength = 4,
    )

    private val PHARMACY = TextIntent(
        roots = listOf("pharm", "farmac", "rx"),
        phrases = listOf("pharmacy order", "pharmacy pickup", "prescription"),
        minRootLength = 3,
    )

    private val DOTCOM = TextIntent(
        roots = listOf("dotcom"),
        phrases = listOf(
            "dot com",
            "dot-com",
            "dotcom order",
            "walmart com",
            "walmart.com",
            "online order",
            ".com order",
        ),
        minRootLength = 4,
    )

    private val CUSTOMER_RETURNS = TextIntent(
        roots = listOf("return", "devoluc"),
        phrases = listOf(
            "customer return",
            "customer returns",
            "return trip",
            "return order",
            "returns trip",
        ),
        excludes = listOf("return to store", "return to walmart"),
        minRootLength = 4,
    )

    private val BULKY_ITEM = TextIntent(
        roots = listOf("bulky", "volumin"),
        phrases = listOf("bulky item", "bulky items", "large item"),
        minRootLength = 4,
    )

    private val SHOPPER_BULK = TextIntent(
        roots = listOf("bulk"),
        phrases = listOf("shopper bulk", "bulk shop", "bulk order", "compra bulk"),
        minRootLength = 4,
    )

    private val APARTMENT = TextIntent(
        roots = listOf("apartment", "apartamento", "apt"),
        phrases = listOf("unit number", "unit #"),
        minRootLength = 3,
    )

    private val CUSTOMER_VERIFICATION = TextIntent(
        roots = listOf("verif", "verify"),
        phrases = listOf(
            "customer verification",
            "id verification",
            "id check",
            "verify customer",
            "verificacion de cliente",
        ),
        minRootLength = 4,
    )

    private val ALCOHOL = TextIntent(
        roots = listOf("alcohol", "liquor", "wine", "beer"),
        phrases = listOf("21+", "21 and over", "bebidas alcoholicas"),
        minRootLength = 4,
    )

    private val HEAVY = TextIntent(
        roots = listOf("heavy", "pesad"),
        phrases = listOf("heavy item", "heavy items", "articulo pesado"),
        minRootLength = 4,
    )

    fun detect(text: String): OrderFlags {
        val shopDeliverCurbside = TextMatcher.matchesIntent(text, SHOP_DELIVER_CURBSIDE)

        val shopAndDeliver = !shopDeliverCurbside &&
            TextMatcher.matchesIntent(text, SHOP_AND_DELIVER)

        val curbside = !shopDeliverCurbside &&
            TextMatcher.matchesIntent(text, CURBSIDE)

        return OrderFlags(
            shopDeliverCurbside = shopDeliverCurbside,
            shopAndDeliver = shopAndDeliver,
            curbside = curbside,
            pharmacy = TextMatcher.matchesIntent(text, PHARMACY),
            dotcom = TextMatcher.matchesIntent(text, DOTCOM),
            customerReturns = TextMatcher.matchesIntent(text, CUSTOMER_RETURNS),
            bulkyItem = TextMatcher.matchesIntent(text, BULKY_ITEM),
            shopperBulk = TextMatcher.matchesIntent(text, SHOPPER_BULK),
            apartment = TextMatcher.matchesIntent(text, APARTMENT),
            customerVerification = TextMatcher.matchesIntent(text, CUSTOMER_VERIFICATION),
            alcohol = TextMatcher.matchesIntent(text, ALCOHOL),
            heavy = TextMatcher.matchesIntent(text, HEAVY),
        )
    }
}

object OrderFilterRules {
    fun passes(flags: OrderFlags, settings: SparkSettings): Boolean {
        val rules = listOf(
            flags.shopDeliverCurbside to settings.shopDeliverCurbside,
            flags.shopAndDeliver to settings.shopAndDeliver,
            flags.curbside to settings.curbside,
            flags.pharmacy to settings.pharmacy,
            flags.dotcom to settings.dotcom,
            flags.customerReturns to settings.customerReturns,
            flags.bulkyItem to settings.bulkyItem,
            flags.shopperBulk to settings.shopperBulk,
            flags.apartment to settings.apartment,
            flags.customerVerification to settings.customerVerification,
            flags.alcohol to settings.alcohol,
            flags.heavy to settings.heavy,
        )
        return rules.all { (present, allowed) -> !present || allowed }
    }
}
