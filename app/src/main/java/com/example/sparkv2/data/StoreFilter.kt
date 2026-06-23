package com.example.sparkv2.data

/**
 * How the store/location filter behaves.
 *
 * - [ANY]: accept offers from any store (filter disabled).
 * - [ONLY_SELECTED]: accept only offers whose text mentions one of the enabled stores.
 */
enum class StoreFilterMode {
    ANY,
    ONLY_SELECTED,
}

/** A saved Walmart store or location keyword the driver wants to accept orders from. */
data class StoreFilter(
    val name: String,
    /** Official Walmart store number when picked from the catalog (e.g. 2964). */
    val storeId: Int? = null,
    val enabled: Boolean = true,
)

object StoreMatcher {
    private val offerStoreNumberRegex = Regex("""#\s*(\d+)\b""", RegexOption.IGNORE_CASE)

    /**
     * Returns the first enabled store that matches [offerText], or null if none match.
     *
     * Matching priority:
     * 1. Store number (`#2964`) when [StoreFilter.storeId] is set
     * 2. City / label keywords parsed from [StoreFilter.name]
     * 3. Legacy substring on the full display name
     */
    fun firstMatch(offerText: String, stores: List<StoreFilter>): StoreFilter? {
        if (offerText.isBlank()) return null
        return stores.firstOrNull { it.enabled && matches(offerText, it) }
    }

    /**
     * Whether [offerText] satisfies the store filter for the given [mode] and [stores].
     *
     * In [StoreFilterMode.ONLY_SELECTED] with no enabled stores the filter is treated as
     * satisfied so the driver is never accidentally locked out of every offer.
     */
    fun passes(offerText: String, mode: StoreFilterMode, stores: List<StoreFilter>): Boolean {
        if (mode == StoreFilterMode.ANY) return true
        val hasEnabled = stores.any { it.enabled && it.name.isNotBlank() }
        if (!hasEnabled) return true
        return firstMatch(offerText, stores) != null
    }

    internal fun matches(offerText: String, store: StoreFilter): Boolean {
        if (store.name.isBlank()) return false

        store.storeId?.let { id ->
            val offerNumbers = offerStoreNumberRegex.findAll(offerText).map { it.groupValues[1] }.toSet()
            if (id.toString() in offerNumbers) return true
        }

        for (keyword in matchKeywords(store)) {
            if (keyword.length >= 3 && offerText.contains(keyword, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    internal fun matchKeywords(store: StoreFilter): List<String> {
        val keywords = mutableListOf<String>()
        val label = store.name.trim()
        if (label.isNotBlank()) keywords.add(label)

        val withoutId = label.substringBefore('#').trim()
        if (withoutId.isNotBlank() && withoutId != label) keywords.add(withoutId)

        val city = withoutId.substringBefore(',').trim()
        if (city.isNotBlank()) keywords.add(city)

        return keywords.distinct()
    }
}
