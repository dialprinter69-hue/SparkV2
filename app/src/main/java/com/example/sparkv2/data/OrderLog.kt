package com.example.sparkv2.data

/** Aggregated counters derived from evaluated offers. */
data class SessionStats(
    val accepted: Int = 0,
    val declined: Int = 0,
    val estimatedEarnings: Double = 0.0,
) {
    val evaluated: Int get() = accepted + declined
    val acceptRate: Float get() = if (evaluated > 0) accepted.toFloat() / evaluated else 0f
}

enum class LogLevel { INFO, WARN, SUCCESS }

data class LogEntry(
    val timestampMs: Long,
    val level: LogLevel,
    val message: String,
)

/**
 * Logs to Logcat (as before) and also keeps a small in-memory ring buffer exposed as a
 * [StateFlow] so the UI can show the bot's recent activity — previously these messages were
 * invisible to the driver.
 */
object OrderLog {
    private const val LOG_TAG = "SparkBot"
    private const val MAX_ENTRIES = 80

    private val entries = java.util.concurrent.CopyOnWriteArrayList<LogEntry>()
    private val _log = kotlinx.coroutines.flow.MutableStateFlow<List<LogEntry>>(emptyList())
    val log: kotlinx.coroutines.flow.StateFlow<List<LogEntry>> = _log

    /** Most recent message, for compact surfaces like the foreground notification. */
    val latest: LogEntry? get() = entries.firstOrNull()

    fun add(message: String) = push(LogLevel.INFO, message)

    fun warn(message: String) {
        android.util.Log.w(LOG_TAG, message)
        record(LogLevel.WARN, message)
    }

    fun alert(message: String) = push(LogLevel.INFO, message)

    fun offer(message: String) = push(LogLevel.INFO, message)

    fun accepted(message: String, amount: Double? = null) = push(LogLevel.SUCCESS, message)

    fun declined(message: String) = push(LogLevel.INFO, message)

    fun clear() {
        entries.clear()
        _log.value = emptyList()
    }

    private fun push(level: LogLevel, message: String) {
        android.util.Log.i(LOG_TAG, message)
        record(level, message)
    }

    private fun record(level: LogLevel, message: String) {
        entries.add(0, LogEntry(System.currentTimeMillis(), level, message))
        while (entries.size > MAX_ENTRIES) entries.removeAt(entries.lastIndex)
        _log.value = entries.toList()
    }
}
