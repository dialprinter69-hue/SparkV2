package com.example.sparkv2.data

/** A US Walmart store from the bundled catalog (store id, city/state when known, ZIP). */
data class WalmartStore(
    val id: Int,
    val city: String,
    val state: String,
    val zip: String,
) {
    fun displayLabel(): String = when {
        city.isNotBlank() && state.isNotBlank() -> "$city, $state #$id"
        zip.isNotBlank() -> "Store #$id ($zip)"
        else -> "Store #$id"
    }

    fun toStoreFilter(enabled: Boolean = true): StoreFilter {
        return StoreFilter(name = displayLabel(), storeId = id, enabled = enabled)
    }
}
