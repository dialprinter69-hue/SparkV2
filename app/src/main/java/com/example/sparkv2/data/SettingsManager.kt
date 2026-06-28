package com.example.sparkv2.data

import android.content.Context

object SettingsManager {
    private const val PREFS = "spark_settings"

    fun saveSettings(context: Context, settings: SparkSettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("enabled", settings.enabled)
            .putBoolean("auto_accept", settings.autoAccept)
            .putBoolean("auto_decline", settings.autoDecline)
            .putFloat("dollars_per_mile", settings.dollarsPerMile)
            .putFloat("max_distance", settings.maxDistance)
            .putFloat("min_pay", settings.minPay)
            .putInt("num_dropoffs", settings.numDropoffs)
            .putFloat("min_per_hour", settings.minDollarsPerHour)
            .putFloat("min_base_pay", settings.minBasePay)
            .putFloat("max_tip_ratio", settings.maxTipRatio)
            .putFloat("max_deadhead", settings.maxDeadhead)
            .putBoolean("item_limit_enabled", settings.itemLimitEnabled)
            .putInt("max_items", settings.maxItems)
            .putFloat("earnings_goal", settings.earningsGoal)
            .putBoolean("operating_hours_enabled", settings.operatingHoursEnabled)
            .putInt("start_hour", settings.startHour)
            .putInt("end_hour", settings.endHour)
            .putBoolean("ocr_fallback", settings.ocrFallbackEnabled)
            .putBoolean("turbo_mode", settings.turboMode)
            .putBoolean("shop_deliver", settings.shopAndDeliver)
            .putBoolean("shop_deliver_curbside", settings.shopDeliverCurbside)
            .putBoolean("curbside", settings.curbside)
            .putBoolean("pharmacy", settings.pharmacy)
            .putBoolean("dotcom", settings.dotcom)
            .putBoolean("customer_returns", settings.customerReturns)
            .putBoolean("bulky", settings.bulkyItem)
            .putBoolean("shopper_bulk", settings.shopperBulk)
            .putBoolean("apartment", settings.apartment)
            .putBoolean("customer_verification", settings.customerVerification)
            .putBoolean("alcohol", settings.alcohol)
            .putBoolean("heavy", settings.heavy)
            .putString("store_filter_mode", settings.storeFilterMode.name)
            .putString("stores", encodeStores(settings.stores))
            .putString("quick_mode", settings.quickMode?.name)
            .putBoolean("debug_dump", settings.debugDump)
            .apply()
        cachedSettings = settings
    }

    fun loadSettings(context: Context): SparkSettings {
        cachedSettings?.let { return it }
        return migrateIfNeeded(context, readSettings(context)).also { cachedSettings = it }
    }

    // One-time migration: enable every offer type/tag once, then never force them again so the
    // driver stays free to turn individual ones off later. Keyed by a version flag in prefs.
    private const val MIGRATION_KEY = "migrated_all_offer_types_v1"

    private fun migrateIfNeeded(context: Context, settings: SparkSettings): SparkSettings {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(MIGRATION_KEY, false)) return settings
        val allOn = settings.copy(
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
        saveSettings(context, allOn)
        prefs.edit().putBoolean(MIGRATION_KEY, true).apply()
        return allOn
    }

    fun invalidateCache() {
        cachedSettings = null
    }

    private fun readSettings(context: Context): SparkSettings {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return SparkSettings(
            enabled = prefs.getBoolean("enabled", false),
            autoAccept = prefs.getBoolean("auto_accept", true),
            autoDecline = prefs.getBoolean("auto_decline", true),
            dollarsPerMile = prefs.getFloat("dollars_per_mile", 0f),
            maxDistance = prefs.getFloat("max_distance", 15f),
            minPay = prefs.getFloat("min_pay", 0f),
            numDropoffs = prefs.getInt("num_dropoffs", 5),
            minDollarsPerHour = prefs.getFloat("min_per_hour", 0f),
            minBasePay = prefs.getFloat("min_base_pay", 0f),
            maxTipRatio = prefs.getFloat("max_tip_ratio", 1f),
            maxDeadhead = prefs.getFloat("max_deadhead", 0f),
            itemLimitEnabled = prefs.getBoolean("item_limit_enabled", false),
            maxItems = prefs.getInt("max_items", 40),
            earningsGoal = prefs.getFloat("earnings_goal", 0f),
            operatingHoursEnabled = prefs.getBoolean("operating_hours_enabled", false),
            startHour = prefs.getInt("start_hour", 8),
            endHour = prefs.getInt("end_hour", 22),
            ocrFallbackEnabled = prefs.getBoolean("ocr_fallback", true),
            turboMode = prefs.getBoolean("turbo_mode", false),
            shopAndDeliver = prefs.getBoolean("shop_deliver", true),
            shopDeliverCurbside = prefs.getBoolean("shop_deliver_curbside", true),
            curbside = prefs.getBoolean("curbside", true),
            pharmacy = prefs.getBoolean("pharmacy", true),
            dotcom = prefs.getBoolean("dotcom", true),
            customerReturns = prefs.getBoolean("customer_returns", true),
            bulkyItem = prefs.getBoolean("bulky", true),
            shopperBulk = prefs.getBoolean("shopper_bulk", true),
            apartment = prefs.getBoolean("apartment", true),
            customerVerification = prefs.getBoolean("customer_verification", true),
            alcohol = prefs.getBoolean("alcohol", true),
            heavy = prefs.getBoolean("heavy", true),
            storeFilterMode = prefs.getString("store_filter_mode", null)?.toStoreMode()
                ?: StoreFilterMode.ANY,
            stores = decodeStores(prefs.getString("stores", null)),
            quickMode = prefs.getString("quick_mode", null)?.toQuickMode(),
            debugDump = prefs.getBoolean("debug_dump", false),
        )
    }

    private fun String.toQuickMode(): QuickMode? {
        return runCatching { QuickMode.valueOf(this) }.getOrNull()
    }

    private fun String.toStoreMode(): StoreFilterMode? {
        return runCatching { StoreFilterMode.valueOf(this) }.getOrNull()
    }

    // Stores are serialized one-per-line as "name\tstoreId\tenabled".
    // Legacy rows with only "name\tenabled" still decode correctly.
    private fun encodeStores(stores: List<StoreFilter>): String {
        return stores.joinToString("\n") { store ->
            val name = store.name.replace("\t", " ").replace("\n", " ").trim()
            val id = store.storeId?.toString().orEmpty()
            "$name\t$id\t${store.enabled}"
        }
    }

    private fun decodeStores(raw: String?): List<StoreFilter> {
        if (raw == null) return emptyList()
        if (raw.isBlank()) return emptyList()
        return raw.split("\n").mapNotNull { line ->
            val parts = line.split("\t")
            val name = parts.getOrNull(0)?.trim().orEmpty()
            if (name.isBlank()) return@mapNotNull null
            val storeId = parts.getOrNull(1)?.trim()?.toIntOrNull()
            val enabledIndex = if (storeId != null || parts.getOrNull(1).isNullOrBlank()) 2 else 1
            val enabled = parts.getOrNull(enabledIndex)?.toBooleanStrictOrNull() ?: true
            StoreFilter(name = name, storeId = storeId, enabled = enabled)
        }
    }

    @Volatile
    private var cachedSettings: SparkSettings? = null
}
