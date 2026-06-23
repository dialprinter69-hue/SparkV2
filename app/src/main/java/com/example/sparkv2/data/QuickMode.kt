package com.example.sparkv2.data

enum class QuickMode {
    PREMIUM,
    PICKY,
    BALANCED,
    SLOW_SHIFT,
    ;

    /**
     * Presets filter on ONE thing only: dollars-per-mile. Everything else (pay floor, distance,
     * drop-offs, offer types/tags) is left wide open so the preset never silently rejects an
     * offer for a reason other than $/mile.
     */
    val preset: Preset
        get() = when (this) {
            PREMIUM -> Preset(dollarsPerMile = 4.00f)
            PICKY -> Preset(dollarsPerMile = 3.50f)
            BALANCED -> Preset(dollarsPerMile = 2.75f)
            SLOW_SHIFT -> Preset(dollarsPerMile = 2.25f)
        }

    fun toSettings(base: SparkSettings): SparkSettings {
        return base.copy(
            quickMode = this,
            dollarsPerMile = preset.dollarsPerMile,
            // Neutralize every other filter — the preset judges by $/mile alone.
            minPay = 0f,
            maxDistance = NO_DISTANCE_LIMIT,
            numDropoffs = NO_DROPOFF_LIMIT,
            minDollarsPerHour = 0f,
            minBasePay = 0f,
            maxTipRatio = 1f,
            maxDeadhead = 0f,
            itemLimitEnabled = false,
            // All offer types and tags on.
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
        )
    }

    data class Preset(
        val dollarsPerMile: Float,
    )

    companion object {
        /** Effectively "no cap" sentinels for filters the presets deliberately leave open. */
        const val NO_DISTANCE_LIMIT = 1000f
        const val NO_DROPOFF_LIMIT = 99
    }
}
