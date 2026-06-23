package com.example.sparkv2.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

enum class OfferOutcome {
    ACCEPTED,
    DECLINED,
    SKIPPED_ACCEPT_OFF,
    SKIPPED_DECLINE_OFF,
}

data class OfferRecord(
    val fingerprint: String,
    val timestampMs: Long,
    val order: ParsedOrder,
    val evaluation: OfferEvaluation,
    val outcome: OfferOutcome,
)

object OfferHistory {
    private const val MAX_RECORDS = 50
    private val records = CopyOnWriteArrayList<OfferRecord>()
    private val timeFormat = ThreadLocal.withInitial {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    }

    data class Snapshot(
        val records: List<OfferRecord> = emptyList(),
        val stats: SessionStats = SessionStats(),
    )

    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    fun formatTime(timestampMs: Long): String =
        requireNotNull(timeFormat.get()).format(Date(timestampMs))

    fun record(
        fingerprint: String,
        order: ParsedOrder,
        evaluation: OfferEvaluation,
        outcome: OfferOutcome,
    ) {
        records.add(
            0,
            OfferRecord(
                fingerprint = fingerprint,
                timestampMs = System.currentTimeMillis(),
                order = order,
                evaluation = evaluation,
                outcome = outcome,
            ),
        )
        while (records.size > MAX_RECORDS) {
            records.removeAt(records.lastIndex)
        }
        // Mirror accept/decline into the persistent store so today/lifetime totals survive restarts.
        when (outcome) {
            OfferOutcome.ACCEPTED -> StatsStore.recordAccepted(order.price)
            OfferOutcome.DECLINED -> StatsStore.recordDeclined()
            OfferOutcome.SKIPPED_ACCEPT_OFF, OfferOutcome.SKIPPED_DECLINE_OFF -> Unit
        }
        publish()
    }

    fun clear() {
        records.clear()
        publish()
    }

    private fun publish() {
        _snapshot.value = Snapshot(records.toList(), computeStats())
    }

    private fun computeStats(): SessionStats {
        var accepted = 0
        var declined = 0
        var earnings = 0.0
        for (record in records) {
            when (record.outcome) {
                OfferOutcome.ACCEPTED -> {
                    accepted++
                    earnings += record.order.price
                }
                OfferOutcome.DECLINED -> declined++
                else -> Unit
            }
        }
        return SessionStats(accepted, declined, earnings)
    }
}
