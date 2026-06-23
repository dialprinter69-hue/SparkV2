package com.example.sparkv2.data

import android.content.Context
import org.json.JSONArray

/**
 * Bundled catalog of US Walmart stores (~4,700). Loaded once from [ASSET_PATH].
 * Search supports store number, city, state, and ZIP.
 */
object WalmartStoreCatalog {
    private const val ASSET_PATH = "walmart_stores.json"

    private data class IndexedStore(
        val store: WalmartStore,
        val searchBlob: String,
        val cityLower: String,
        val stateLower: String,
        val idText: String,
    )

    @Volatile
    private var stores: List<WalmartStore>? = null

    @Volatile
    private var indexed: List<IndexedStore>? = null

    fun ensureLoaded(context: Context) {
        if (stores != null) return
        synchronized(this) {
            if (stores != null) return
            val loaded = load(context.applicationContext)
            stores = loaded
            indexed = buildIndex(loaded)
        }
    }

    fun all(context: Context): List<WalmartStore> {
        ensureLoaded(context)
        return stores.orEmpty()
    }

    fun count(context: Context): Int = all(context).size

    fun findById(context: Context, id: Int): WalmartStore? {
        return all(context).firstOrNull { it.id == id }
    }

    /**
     * Case-insensitive search. Empty query returns nothing; numeric-only queries match store ids
     * and ZIP prefixes; text queries match city and state.
     */
    fun search(context: Context, query: String, limit: Int = 25): List<WalmartStore> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()
        ensureLoaded(context)
        val catalog = indexed.orEmpty()
        val q = trimmed.lowercase()
        val numeric = trimmed.all { it.isDigit() }

        return catalog.asSequence()
            .filter { entry ->
                when {
                    numeric -> entry.idText.startsWith(trimmed) || entry.store.zip.startsWith(trimmed)
                    else -> entry.searchBlob.contains(q)
                }
            }
            .sortedWith(
                compareBy<IndexedStore> { !it.cityLower.startsWith(q) }
                    .thenBy { !it.idText.startsWith(trimmed) }
                    .thenBy { it.store.state }
                    .thenBy { it.store.city }
                    .thenBy { it.store.id },
            )
            .take(limit)
            .map { it.store }
            .toList()
    }

    private fun buildIndex(stores: List<WalmartStore>): List<IndexedStore> {
        return stores.map { store ->
            IndexedStore(
                store = store,
                searchBlob = buildSearchBlob(store),
                cityLower = store.city.lowercase(),
                stateLower = store.state.lowercase(),
                idText = store.id.toString(),
            )
        }
    }

    private fun buildSearchBlob(store: WalmartStore): String {
        return buildString {
            append(store.id)
            append(' ')
            append(store.city)
            append(' ')
            append(store.state)
            append(' ')
            append(store.zip)
            append(' ')
            append(store.displayLabel())
        }.lowercase()
    }

    private fun load(context: Context): List<WalmartStore> {
        val json = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        val array = JSONArray(json)
        return buildList(array.length()) {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(
                    WalmartStore(
                        id = obj.getInt("i"),
                        city = obj.optString("c", ""),
                        state = obj.optString("s", ""),
                        zip = obj.optString("z", ""),
                    ),
                )
            }
        }
    }
}
