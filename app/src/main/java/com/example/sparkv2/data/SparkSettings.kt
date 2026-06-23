package com.example.sparkv2.data

data class SparkSettings(
    val enabled: Boolean = false,
    val autoAccept: Boolean = true,
    val autoDecline: Boolean = true,
    val dollarsPerMile: Float = 0f,
    val maxDistance: Float = 15f,
    val minPay: Float = 0f,
    val numDropoffs: Int = 1,
    /** Minimum $/hour using the offer's estimated time. 0 = no minimum. */
    val minDollarsPerHour: Float = 0f,
    /** Minimum guaranteed base pay (excludes tip). 0 = no minimum. Anti tip-bait. */
    val minBasePay: Float = 0f,
    /** Maximum fraction (0..1) of the payout that may come from tip. 1 = no cap. Anti tip-bait. */
    val maxTipRatio: Float = 1f,
    /** Maximum deadhead miles to the pickup/store. 0 = no cap (and usually not shown anyway). */
    val maxDeadhead: Float = 0f,
    /**
     * Item-count cap for Shop & Deliver. OFF by default on purpose: the exact count often lives
     * behind a detail tap, and reading it would slow down offer capture. When on, only offers
     * whose item count is visible AND within [maxItems] pass; offers without a visible count are
     * not rejected for this reason.
     */
    val itemLimitEnabled: Boolean = false,
    val maxItems: Int = 40,
    /** Stop accepting once today's earnings reach this (StatsStore). 0 = no goal. */
    val earningsGoal: Float = 0f,
    /** When on, the bot only acts on offers within [startHour], [endHour). */
    val operatingHoursEnabled: Boolean = false,
    /** Operating window start hour (0-23) and end hour (1-24, exclusive). Wraps past midnight. */
    val startHour: Int = 8,
    val endHour: Int = 22,
    /**
     * OCR fallback: when a Spark alert lands on a canvas-rendered screen the accessibility tree is
     * empty, so we screenshot + OCR it to still read (and accept) the offer. Only fires on opaque
     * screens right after an alert. Auto-accept only — declining via OCR is intentionally not done.
     */
    val ocrFallbackEnabled: Boolean = true,
    /**
     * Turbo mode: shorter scan/action delays for faster accept/decline. May make Spark Driver
     * feel heavier on slower phones — turn off if the app starts lagging.
     */
    val turboMode: Boolean = false,
    /**
     * Aggressive turbo: minimum delays and faster follow-up scans. Requires [turboMode]. May make
     * Spark Driver lag or miss taps on slower phones — turn off if that happens.
     */
    val aggressiveTurbo: Boolean = false,
    /**
     * Super-aggressive turbo: experimental floor delays for maximum speed. Requires [turboMode]
     * and [aggressiveTurbo]. Turn off immediately if Spark Driver lags or taps miss.
     */
    val superAggressiveTurbo: Boolean = false,
    // Order types — all enabled by default so the bot considers every offer type out of the box.
    val shopAndDeliver: Boolean = true,
    val shopDeliverCurbside: Boolean = true,
    val curbside: Boolean = true,
    val pharmacy: Boolean = true,
    val dotcom: Boolean = true,
    val customerReturns: Boolean = true,
    // Offer tags
    val bulkyItem: Boolean = true,
    val shopperBulk: Boolean = true,
    val apartment: Boolean = true,
    val customerVerification: Boolean = true,
    val alcohol: Boolean = true,
    val heavy: Boolean = true,
    // Store / location filter
    val storeFilterMode: StoreFilterMode = StoreFilterMode.ANY,
    val stores: List<StoreFilter> = emptyList(),
    val quickMode: QuickMode? = null,
    /** When on, the service dumps the live accessibility tree to the log for diagnosis. */
    val debugDump: Boolean = false,
) {
    /** Resets every offer filter to defaults while keeping bot/automation toggles. */
    fun resetOfferFilters(): SparkSettings = copy(
        dollarsPerMile = 0f,
        maxDistance = 15f,
        minPay = 0f,
        numDropoffs = 1,
        minDollarsPerHour = 0f,
        minBasePay = 0f,
        maxTipRatio = 1f,
        maxDeadhead = 0f,
        itemLimitEnabled = false,
        maxItems = 40,
        earningsGoal = 0f,
        operatingHoursEnabled = false,
        startHour = 8,
        endHour = 22,
        shopAndDeliver = true,
        shopDeliverCurbside = true,
        curbside = true,
        pharmacy = true,
        dotcom = true,
        customerReturns = true,
        bulkyItem = true,
        shopperBulk = true,
        apartment = true,
        customerVerification = true,
        alcohol = true,
        heavy = true,
        storeFilterMode = StoreFilterMode.ANY,
        stores = emptyList(),
        quickMode = null,
    )
}
