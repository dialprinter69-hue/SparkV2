package com.example.sparkv2.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persistent earnings/outcome counters that survive process death — unlike [OfferHistory], which
 * keeps only the recent in-memory detail list. Tracks "today" (rolled at midnight) plus lifetime
 * totals. Backed by SharedPreferences; no extra dependencies.
 */
object StatsStore {
    private const val PREFS = "spark_stats"

    data class Stats(
        val day: String = "",
        val todayAccepted: Int = 0,
        val todayDeclined: Int = 0,
        val todayEarnings: Double = 0.0,
        val lifetimeAccepted: Int = 0,
        val lifetimeDeclined: Int = 0,
        val lifetimeEarnings: Double = 0.0,
    ) {
        val todayEvaluated: Int get() = todayAccepted + todayDeclined
        val todayAcceptRate: Float
            get() = if (todayEvaluated > 0) todayAccepted.toFloat() / todayEvaluated else 0f
    }

    private val _stats = MutableStateFlow(Stats())
    val stats: StateFlow<Stats> = _stats.asStateFlow()

    @Volatile private var appContext: Context? = null

    private val dayFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val rolled = readAndRoll()
        _stats.value = rolled
        persist(rolled)
    }

    /** Rolls today counters at midnight while the process stays alive. */
    fun refreshDayRoll() {
        val rolled = rollIfNewDay(_stats.value)
        if (rolled != _stats.value) {
            _stats.value = rolled
            persist(rolled)
        }
    }

    /** Clears today's counters; lifetime totals are unchanged. */
    fun resetToday() = update { s ->
        s.copy(
            day = today(),
            todayAccepted = 0,
            todayDeclined = 0,
            todayEarnings = 0.0,
        )
    }

    /** Today's earnings, read live so callers (auto-shutoff) don't need a Flow. */
    fun todayEarnings(): Double = _stats.value.takeIf { it.day == today() }?.todayEarnings ?: 0.0

    fun recordAccepted(amount: Double) = update { s ->
        s.copy(
            todayAccepted = s.todayAccepted + 1,
            todayEarnings = s.todayEarnings + amount,
            lifetimeAccepted = s.lifetimeAccepted + 1,
            lifetimeEarnings = s.lifetimeEarnings + amount,
        )
    }

    fun recordDeclined() = update { s ->
        s.copy(
            todayDeclined = s.todayDeclined + 1,
            lifetimeDeclined = s.lifetimeDeclined + 1,
        )
    }

    private fun update(transform: (Stats) -> Stats) {
        val rolled = rollIfNewDay(_stats.value)
        val next = transform(rolled)
        _stats.value = next
        persist(next)
    }

    private fun today(): String = requireNotNull(dayFormat.get()).format(Date())

    private fun rollIfNewDay(current: Stats): Stats {
        val today = today()
        return if (current.day == today) current else current.copy(
            day = today,
            todayAccepted = 0,
            todayDeclined = 0,
            todayEarnings = 0.0,
        )
    }

    private fun readAndRoll(): Stats {
        val ctx = appContext ?: return Stats(day = today())
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = Stats(
            day = prefs.getString("day", "").orEmpty(),
            todayAccepted = prefs.getInt("today_accepted", 0),
            todayDeclined = prefs.getInt("today_declined", 0),
            todayEarnings = prefs.getFloat("today_earnings", 0f).toDouble(),
            lifetimeAccepted = prefs.getInt("life_accepted", 0),
            lifetimeDeclined = prefs.getInt("life_declined", 0),
            lifetimeEarnings = prefs.getFloat("life_earnings", 0f).toDouble(),
        )
        return rollIfNewDay(stored)
    }

    private fun persist(stats: Stats) {
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("day", stats.day)
            .putInt("today_accepted", stats.todayAccepted)
            .putInt("today_declined", stats.todayDeclined)
            .putFloat("today_earnings", stats.todayEarnings.toFloat())
            .putInt("life_accepted", stats.lifetimeAccepted)
            .putInt("life_declined", stats.lifetimeDeclined)
            .putFloat("life_earnings", stats.lifetimeEarnings.toFloat())
            .apply()
    }
}
