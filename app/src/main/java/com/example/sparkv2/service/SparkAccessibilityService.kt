package com.example.sparkv2.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.sparkv2.R
import com.example.sparkv2.SparkConstants
import com.example.sparkv2.automation.AccessibilityActions
import com.example.sparkv2.automation.ActionGate
import com.example.sparkv2.automation.DeclineFlow
import com.example.sparkv2.automation.NodeDumper
import com.example.sparkv2.automation.OcrResult
import com.example.sparkv2.automation.OrderTextHints
import com.example.sparkv2.automation.ScanScheduler
import com.example.sparkv2.automation.ScanTiming
import com.example.sparkv2.automation.ScreenAnalyzer
import com.example.sparkv2.automation.ScreenOcr
import com.example.sparkv2.automation.ScreenSnapshot
import com.example.sparkv2.automation.TextMatcher
import com.example.sparkv2.SparkIntents
import com.example.sparkv2.automation.SparkAutomationHub
import com.example.sparkv2.automation.SparkWindowFinder
import com.example.sparkv2.data.OfferEvaluation
import com.example.sparkv2.data.OfferEvaluator
import com.example.sparkv2.data.OfferHistory
import com.example.sparkv2.data.OperatingPolicy
import com.example.sparkv2.data.StatsStore
import com.example.sparkv2.data.OfferOutcome
import com.example.sparkv2.data.OrderLog
import com.example.sparkv2.data.OrderParser
import com.example.sparkv2.data.ParsedOrder
import com.example.sparkv2.data.SettingsManager
import com.example.sparkv2.data.SparkSettings

class SparkAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    // Heavy work (tree traversal + taps) runs on this dedicated background thread so the UI
    // thread never janks — this is what lets us scan aggressively without freezing the app.
    private val scanThread = HandlerThread("spark-scan").apply { start() }
    private val scanHandler = Handler(scanThread.looper)
    private val declineFlow = DeclineFlow()
    private val scanScheduler = ScanScheduler()
    private val warnThrottle = WarnThrottle()

    @Volatile private var pendingScanReason = "scheduled"
    @Volatile private var cachedSettings: SparkSettings? = null
    @Volatile private var settingsLoadedAtMs = 0L
    private var declinePollRunnable: Runnable? = null

    private val scanGuard = ScanGuard()
    /** While now < this, heartbeat/content scans are suppressed to relieve the scan thread. */
    @Volatile private var quietUntilMs = 0L
    /** Set true when a scan performed a real action (tap/open), so the guard doesn't back off. */
    @Volatile private var scanProgress = false

    private val scanRunnable = Runnable {
        runScan(pendingScanReason)
    }

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            // Skip the heartbeat scan while backing off from a static/stuck screen — this is
            // what keeps the main thread free when many offers (with ticking timers) are listed.
            if (isBotEnabled() && System.currentTimeMillis() >= quietUntilMs) {
                scheduleScan("heartbeat", delayMs = 0)
            }
            scanHandler.postDelayed(this, ScanTiming.heartbeatInterval(SparkAutomationHub.speed()))
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        SparkAutomationHub.service = this
        OrderLog.add(
            getString(
                R.string.log_accessibility_connected,
                SparkConstants.SPARK_PACKAGES.joinToString(),
            ),
        )
        scanHandler.postDelayed(
            heartbeatRunnable,
            ScanTiming.heartbeatInterval(SparkAutomationHub.speed()),
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !isBotEnabled()) return

        val packageName = event.packageName?.toString().orEmpty()
        val sparkVisible = SparkConstants.isSparkPackage(packageName)
        if (!sparkVisible) {
            maybeWarnUnknownSparkPackage(packageName)
            if (!declineFlow.isAwaitingConfirmation()) return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            -> {
                // Real navigation: a new screen is up. Drop any stuck-screen backoff so we
                // react immediately instead of waiting out the quiet window. The guard is only
                // touched from the scan thread, so post the reset there to avoid a data race.
                scanHandler.post {
                    quietUntilMs = 0L
                    scanGuard.clear()
                }
                queueScan("window-state", ScanTiming.windowStateDelay(SparkAutomationHub.speed()))
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (declineFlow.isAwaitingConfirmation()) {
                    queueScan("decline-content", ScanTiming.declineContentDelay(SparkAutomationHub.speed()))
                } else if (
                    sparkVisible &&
                    System.currentTimeMillis() >= quietUntilMs &&
                    scanScheduler.canRunContentScan(SparkAutomationHub.speed())
                ) {
                    // Respect the stuck-screen backoff here too. The Spark map animates its
                    // markers, firing a stream of content-changed events; without this gate we'd
                    // keep walking the whole window tree every ~380ms forever, which is what
                    // saturates the accessibility channel and freezes Spark over time.
                    scanScheduler.markContentScan()
                    queueScan("content", ScanTiming.contentChangeDelay(SparkAutomationHub.speed()))
                }
            }
        }
    }

    override fun onInterrupt() {
        OrderLog.warn(getString(R.string.log_accessibility_interrupted))
    }

    override fun onDestroy() {
        scanHandler.removeCallbacks(heartbeatRunnable)
        stopDeclineConfirmPolling()
        scanHandler.removeCallbacksAndMessages(null)
        mainHandler.removeCallbacksAndMessages(null)
        scanThread.quitSafely()
        if (SparkAutomationHub.service === this) {
            SparkAutomationHub.service = null
        }
        super.onDestroy()
    }

    fun scheduleScan(reason: String, delayMs: Long = ScanTiming.defaultScanDelay(SparkAutomationHub.speed())) {
        pendingScanReason = reason
        queueScan(reason, delayMs)
    }

    fun scheduleFollowUpScans() {
        ScanTiming.notificationFollowUps(SparkAutomationHub.speed()).forEach { delay ->
            scanHandler.postDelayed({ scheduleScan("notification-followup", delayMs = 0) }, delay)
        }
    }

    private fun queueScan(reason: String, delayMs: Long) {
        pendingScanReason = reason
        scanHandler.removeCallbacks(scanRunnable)
        scanHandler.postDelayed(scanRunnable, delayMs)
    }

    private fun runScan(reason: String) {
        if (!isBotEnabled()) return
        val speed = SparkAutomationHub.speed()

        val urgent = reason.startsWith("decline") ||
            reason.contains("retry") ||
            reason.startsWith("notification") ||
            reason.startsWith("offer-open") ||
            reason == "heartbeat" ||
            SparkAutomationHub.isAlertBoostActive()

        if (ActionGate.isBusy() && !declineFlow.isAwaitingConfirmation() && !reason.contains("retry") && !urgent) return

        if (!urgent && !scanScheduler.canRunScan(speed)) return

        val root = SparkWindowFinder.findSparkRoot(this) ?: run {
            warnThrottle.warn("no-window", getString(R.string.log_no_spark_window, reason))
            return
        }

        if (!urgent) scanScheduler.markScan()

        try {
            val snapshot = ScreenAnalyzer.analyze(root)
            val settings = currentSettings()

            if (settings.debugDump) maybeDumpScreen(root, snapshot, reason)

            scanProgress = false
            if (declineFlow.isAwaitingConfirmation()) {
                if (snapshot.hasAcceptButton &&
                    snapshot.hasDeclineButton &&
                    !snapshot.isDeclineConfirmDialog &&
                    ScreenAnalyzer.isOfferScreen(snapshot)
                ) {
                    declineFlow.reset()
                    stopDeclineConfirmPolling()
                    handleOfferFlow(root, snapshot, settings, reason)
                } else {
                    handleDeclineConfirmation(root, snapshot)
                }
            } else {
                handleOfferFlow(root, snapshot, settings, reason)
            }
            updateScanGuard(snapshot)
        } finally {
            root.recycle()
        }
    }

    /**
     * Adaptive backoff: if the screen looks identical scan after scan and we made no progress
     * (e.g. a tap that won't land, or an idle list waiting on the human), stop hammering the
     * main thread every heartbeat. This keeps the app responsive when many offers are on screen.
     * Skipped while awaiting decline confirmation, where rapid re-scans are expected.
     */
    private fun updateScanGuard(snapshot: ScreenSnapshot) {
        if (declineFlow.isAwaitingConfirmation()) return
        if (scanProgress) {
            scanGuard.clear()
            quietUntilMs = 0L
            openOfferAttempts = 0
            openOfferSignature = null
            return
        }
        val signature = buildString {
            append(snapshot.hasAcceptButton)
            append('|')
            append(snapshot.hasDeclineButton)
            append('|')
            append(snapshot.acceptButtonCount)
            append('|')
            append(snapshot.combinedText.length)
            append('|')
            append(snapshot.combinedText.hashCode())
        }
        if (scanGuard.seen(signature) >= ScanTiming.stuckRepeatThreshold(SparkAutomationHub.speed())) {
            quietUntilMs = System.currentTimeMillis() + ScanTiming.stuckBackoff(SparkAutomationHub.speed())
            openOfferSignature = signature
            if (scanGuard.shouldLogStuck()) {
                warnThrottle.warn(
                    "stuck",
                    getString(R.string.log_screen_stuck),
                )
            }
        }
    }

    private fun handleOfferFlow(
        root: AccessibilityNodeInfo,
        snapshot: ScreenSnapshot,
        settings: SparkSettings,
        reason: String,
    ) {
        if (OrderTextHints.looksLikeOffer(snapshot.combinedText)) {
            warnThrottle.warn(
                "feed-diag",
                "feed? accept=${snapshot.acceptButtonCount} hasA=${snapshot.hasAcceptButton}" +
                    " hasD=${snapshot.hasDeclineButton}" +
                    " list=${ScreenAnalyzer.looksLikeOfferList(snapshot)}" +
                    " multi=${ScreenAnalyzer.isMultiOfferList(snapshot)}",
            )
        }

        // The bot ONLY ever clicks Accept/Reject on a real offer — it never taps a card to
        // navigate into a detail screen except for the multi-offer feed, where combined text
        // mixes payouts and would parse incorrectly.
        if (!ScreenAnalyzer.isOfferScreen(snapshot)) {
            // Canvas-rendered offer screens expose no text/buttons. Right after a Spark alert,
            // fall back to a screenshot + OCR read so we can still catch the offer.
            if (shouldTryOcr(snapshot, settings, reason)) {
                runOcrFallback()
                return
            }
            if (reason == "heartbeat" || reason.contains("notification")) {
                logScanDiagnostics(snapshot)
            }
            return
        }

        handleOfferScreen(root, snapshot, settings)
    }

    private fun shouldTryOcr(snapshot: ScreenSnapshot, settings: SparkSettings, reason: String): Boolean {
        if (!settings.ocrFallbackEnabled || !ScreenOcr.isSupported) return false
        val opaque = snapshot.combinedText.isBlank() &&
            !snapshot.hasAcceptButton && !snapshot.hasDeclineButton
        if (!opaque) return false
        val alertDriven = reason.contains("notification") ||
            SparkAutomationHub.isAlertBoostActive()
        return alertDriven && ScreenOcr.canRun(this)
    }

    private fun runOcrFallback() {
        OrderLog.add(getString(R.string.log_ocr_scanning))
        ScreenOcr.capture(this) { result ->
            // Callback is on the main thread; hop to the scan thread for parse + actions.
            if (result != null) {
                scanHandler.post { handleOcrResult(result) }
            }
        }
    }

    private fun handleOcrResult(result: OcrResult) {
        val settings = currentSettings()
        if (!settings.enabled) return
        if (!OrderTextHints.looksLikeOffer(result.text)) return

        val order = OrderParser.parse(result.text) ?: return
        val fingerprint = "ocr|" + buildFingerprint(order)
        if (!ActionGate.shouldHandleOffer(fingerprint, speed = SparkAutomationHub.speed())) return

        val summary = formatSummary(order)
        OrderLog.offer(getString(R.string.log_ocr_offer, summary))
        val evaluation = OfferEvaluator.evaluate(order, settings)

        if (!ActionGate.tryLock(speed = SparkAutomationHub.speed())) return
        try {
            when {
                evaluation.passes && settings.autoAccept -> {
                    if (clickAcceptViaNodeOrOcr(result)) {
                        OfferHistory.record(fingerprint, order, evaluation, OfferOutcome.ACCEPTED)
                        OrderLog.accepted(getString(R.string.log_ocr_accepted, summary))
                        ActionGate.markHandled(fingerprint)
                        scanProgress = true
                    } else {
                        warnThrottle.warn("ocr-accept-fail", getString(R.string.log_ocr_accept_fail))
                    }
                }
                evaluation.passes -> {
                    OfferHistory.record(fingerprint, order, evaluation, OfferOutcome.SKIPPED_ACCEPT_OFF)
                    ActionGate.markHandled(fingerprint)
                }
                else -> {
                    // Failing offer via OCR: we deliberately don't auto-decline (a mis-placed
                    // gesture on a canvas screen is the risky case). Surface it for the driver.
                    warnThrottle.warn("ocr-decline", getString(R.string.log_ocr_would_decline, summary))
                    ActionGate.markHandled(fingerprint)
                }
            }
        } finally {
            ActionGate.release()
        }
    }

    /** Prefer a real clickable Accept node (some buttons may still be accessible); else OCR-tap. */
    private fun clickAcceptViaNodeOrOcr(result: OcrResult): Boolean {
        val root = SparkWindowFinder.findSparkRoot(this)
        val byNode = root?.let {
            try {
                AccessibilityActions.clickAccept(it)
            } finally {
                it.recycle()
            }
        } ?: false
        if (byNode) return true

        val acceptWord = result.words.firstOrNull { word ->
            TextMatcher.matchesIntent(word.text, SparkIntents.ACCEPT) &&
                !TextMatcher.matchesIntent(word.text, SparkIntents.START_TRIP)
        } ?: return false
        return AccessibilityActions.tapRect(this, acceptWord.bounds)
    }

    private var lastDumpKey: String? = null
    private var lastDumpAtMs = 0L

    private fun maybeDumpScreen(root: AccessibilityNodeInfo, snapshot: ScreenSnapshot, reason: String) {
        val dump = NodeDumper.dump(root, getString(R.string.log_empty_tree))
        // Throttle: only emit when the screen content changed or 5s passed, to avoid log spam.
        val key = "${dump.nodeCount}:${snapshot.combinedText.take(40)}"
        val now = System.currentTimeMillis()
        if (key == lastDumpKey && now - lastDumpAtMs < 5_000L) return
        lastDumpKey = key
        lastDumpAtMs = now

        if (dump.looksOpaque) {
            OrderLog.warn(
                getString(
                    R.string.log_dump_opaque,
                    reason,
                    dump.nodeCount,
                    dump.clickableCount,
                ),
            )
        }
        OrderLog.add(
            getString(
                R.string.log_dump_header,
                reason,
                snapshot.hasAcceptButton,
                snapshot.hasDeclineButton,
                dump.nodeCount,
                dump.clickableCount,
                dump.text,
            ),
        )
    }

    private fun logScanDiagnostics(snapshot: ScreenSnapshot) {
        val preview = snapshot.combinedText
            .take(120)
            .replace('\n', ' ')
            .ifBlank { getString(R.string.log_empty_preview) }
        warnThrottle.warn(
            "scan-debug",
            getString(
                R.string.log_scan_debug,
                snapshot.hasAcceptButton,
                snapshot.hasDeclineButton,
                preview,
            ),
        )
    }

    private fun handleDeclineConfirmation(root: AccessibilityNodeInfo, snapshot: ScreenSnapshot) {
        if (!declineFlow.isAwaitingConfirmation()) return
        // Keep tapping the confirm button until the dialog actually dismisses or we time out.
        // Do not infer "dialog closed" from a snapshot miss — Spark's confirm sheet often has
        // no Accept button and no "are you sure" copy in the accessibility tree, which used to
        // make us bail out while the dialog was still open.
        confirmDecline(root, snapshot)
    }

    private fun confirmDecline(root: AccessibilityNodeInfo, snapshot: ScreenSnapshot) {
        if (!currentSettings().autoDecline) {
            declineFlow.reset()
            stopDeclineConfirmPolling()
            return
        }

        if (!ActionGate.tryLock(
                durationMs = ScanTiming.actionConfirmLock(SparkAutomationHub.speed()),
                speed = SparkAutomationHub.speed(),
            )
        ) {
            scheduleScan(
                "decline-confirm-wait",
                delayMs = ScanTiming.confirmRetryDelay(SparkAutomationHub.speed()),
            )
            return
        }

        try {
            val attempt = declineFlow.recordConfirmAttempt()
            if (attempt > MAX_CONFIRM_ATTEMPTS) {
                warnThrottle.warn("confirm-giveup", getString(R.string.log_confirm_giveup))
                finishDecline(reason = "next-offer")
                return
            }

            val confirmed = AccessibilityActions.clickConfirmDecline(root)
            if (confirmed) {
                OrderLog.declined(getString(R.string.log_declined_confirmed))
                finishDecline(reason = "next-offer")
            } else {
                warnThrottle.warn(
                    "decline-confirm",
                    getString(
                        R.string.log_decline_step2,
                        snapshot.isDeclineConfirmDialog,
                        snapshot.hasDeclineButton,
                    ),
                )
                scheduleScan(
                    "decline-confirm-retry",
                    delayMs = ScanTiming.confirmRetryDelay(SparkAutomationHub.speed()),
                )
            }
        } finally {
            ActionGate.release()
        }
    }

    /** Decline finished (or abandoned). Reset state and immediately look for the next offer. */
    private fun finishDecline(reason: String) {
        scanProgress = true
        declineFlow.reset()
        stopDeclineConfirmPolling()
        ActionGate.resetOfferMemory()
        scanGuard.clear()
        quietUntilMs = 0L
        scheduleScan(reason, delayMs = ScanTiming.nextOfferDelay(SparkAutomationHub.speed()))
    }

    private fun handleOfferScreen(root: AccessibilityNodeInfo, snapshot: ScreenSnapshot, settings: SparkSettings) {
        OperatingPolicy.pauseReason(
            settings,
            StatsStore.todayEarnings(),
            java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY),
        )?.let { reason ->
            val key = "paused-${reason.name}"
            val msg = when (reason) {
                OperatingPolicy.PauseReason.GOAL_REACHED -> getString(R.string.log_paused_goal)
                OperatingPolicy.PauseReason.OUTSIDE_HOURS -> getString(R.string.log_paused_hours)
            }
            warnThrottle.warn(key, msg)
            return
        }

        val order = OrderParser.parse(snapshot.combinedText)
            ?: OrderParser.parseFirstCard(snapshot.combinedText)
            ?: OrderParser.parseBestSegment(snapshot.combinedText)
            ?: run {
                if (ScreenAnalyzer.isMultiOfferList(snapshot) && tryOpenTopOffer(root, snapshot)) return
                warnThrottle.warn("parse-fail", getString(R.string.log_parse_fail))
                return
            }

        val fingerprint = buildFingerprint(order)
        if (!ActionGate.shouldHandleOffer(fingerprint, speed = SparkAutomationHub.speed())) return

        val summary = formatSummary(order)
        OrderLog.offer(getString(R.string.log_evaluating, summary))

        val evaluation = OfferEvaluator.evaluate(order, settings)
        val multiOffer = ScreenAnalyzer.isMultiOfferList(snapshot)
        val outcome = resolveOutcome(evaluation, settings)

        if (!ActionGate.tryLock(speed = SparkAutomationHub.speed())) {
            scheduleScan(
                "offer-busy-retry",
                delayMs = ScanTiming.acceptRetryDelay(SparkAutomationHub.speed()),
            )
            return
        }

        try {
            when (outcome) {
                OfferOutcome.SKIPPED_ACCEPT_OFF -> {
                    warnThrottle.warn(
                        "accept-off",
                        getString(R.string.log_meets_filters_accept_off, summary),
                    )
                    OfferHistory.record(fingerprint, order, evaluation, outcome)
                    ActionGate.markHandled(fingerprint)
                }
                OfferOutcome.ACCEPTED -> acceptOffer(
                    root,
                    fingerprint,
                    order,
                    evaluation,
                    summary,
                    preferTopmost = multiOffer,
                )
                OfferOutcome.SKIPPED_DECLINE_OFF -> {
                    warnThrottle.warn(
                        "decline-off",
                        getString(R.string.log_fails_filters_decline_off, summary),
                    )
                    OfferHistory.record(fingerprint, order, evaluation, outcome)
                    ActionGate.markHandled(fingerprint)
                }
                OfferOutcome.DECLINED -> declineOffer(
                    root,
                    fingerprint,
                    order,
                    evaluation,
                    summary,
                    preferTopmost = multiOffer,
                )
            }
        } finally {
            ActionGate.release()
        }
    }

    private fun resolveOutcome(evaluation: OfferEvaluation, settings: SparkSettings): OfferOutcome {
        return when {
            evaluation.passes && settings.autoAccept -> OfferOutcome.ACCEPTED
            evaluation.passes -> OfferOutcome.SKIPPED_ACCEPT_OFF
            !evaluation.passes && settings.autoDecline -> OfferOutcome.DECLINED
            else -> OfferOutcome.SKIPPED_DECLINE_OFF
        }
    }

    private var openOfferAttempts = 0
    private var openOfferSignature: String? = null

    private fun tryOpenTopOffer(root: AccessibilityNodeInfo, snapshot: ScreenSnapshot): Boolean {
        val sig = "${snapshot.combinedText.hashCode()}|${snapshot.acceptButtonCount}"
        if (openOfferSignature != sig) {
            openOfferSignature = sig
            openOfferAttempts = 0
        }
        if (openOfferAttempts >= 3) return false
        openOfferAttempts++
        if (!ActionGate.tryLock(
                durationMs = ScanTiming.openOfferLock(SparkAutomationHub.speed()),
                speed = SparkAutomationHub.speed(),
            )
        ) return false
        return try {
            if (AccessibilityActions.openOfferDetail(root)) {
                scanProgress = true
                scheduleScan(
                    "offer-opened",
                    delayMs = ScanTiming.offerOpenDelay(SparkAutomationHub.speed()),
                )
                true
            } else {
                scheduleScan(
                    "offer-open-retry",
                    delayMs = ScanTiming.acceptRetryDelay(SparkAutomationHub.speed()),
                )
                false
            }
        } finally {
            ActionGate.release()
        }
    }

    private fun acceptOffer(
        root: AccessibilityNodeInfo,
        fingerprint: String,
        order: ParsedOrder,
        evaluation: OfferEvaluation,
        summary: String,
        preferTopmost: Boolean = false,
    ) {
        val accepted = AccessibilityActions.clickAccept(root, preferTopmost = preferTopmost)
        if (accepted) {
            OfferHistory.record(fingerprint, order, evaluation, OfferOutcome.ACCEPTED)
            OrderLog.accepted(getString(R.string.log_accepted, summary), amount = summary.extractPrice())
            ActionGate.markHandled(fingerprint)
            scanProgress = true
        } else {
            warnThrottle.warn("accept-fail", getString(R.string.log_accept_fail))
            retrySoon("accept-retry")
        }
    }

    private fun declineOffer(
        root: AccessibilityNodeInfo,
        fingerprint: String,
        order: ParsedOrder,
        evaluation: OfferEvaluation,
        summary: String,
        preferTopmost: Boolean = false,
    ) {
        val declined = AccessibilityActions.clickReject(root, preferTopmost = preferTopmost)
        if (declined) {
            OfferHistory.record(fingerprint, order, evaluation, OfferOutcome.DECLINED)
            declineFlow.startDecline()
            OrderLog.declined(getString(R.string.log_declined_step1, summary))
            ActionGate.markHandled(fingerprint)
            scanProgress = true
            startDeclineConfirmPolling()
        } else {
            warnThrottle.warn("decline-fail", getString(R.string.log_decline_fail))
            retrySoon("decline-retry")
        }
    }

    private fun retrySoon(reason: String) {
        scanHandler.postDelayed(
            { scheduleScan(reason, delayMs = 0) },
            ScanTiming.acceptRetryDelay(SparkAutomationHub.speed()),
        )
    }

    private fun startDeclineConfirmPolling() {
        stopDeclineConfirmPolling()
        val pollInterval = ScanTiming.declinePollInterval(SparkAutomationHub.speed())
        val runnable = object : Runnable {
            override fun run() {
                if (!declineFlow.isAwaitingConfirmation()) {
                    stopDeclineConfirmPolling()
                    return
                }
                scheduleScan("decline-poll", delayMs = 0)
                scanHandler.postDelayed(this, pollInterval)
            }
        }
        declinePollRunnable = runnable
        scanHandler.postDelayed(runnable, pollInterval)
    }

    private fun stopDeclineConfirmPolling() {
        declinePollRunnable?.let { scanHandler.removeCallbacks(it) }
        declinePollRunnable = null
    }

    private fun formatSummary(order: ParsedOrder): String {
        val dist = order.distance
        val core = if (dist != null && dist > 0f) {
            "$${order.price} | $dist mi | $%.2f/mi".format(order.price / dist)
        } else {
            "$${order.price} | ${getString(R.string.log_distance_unknown)}"
        }
        return order.storeName?.let { "$it | $core" } ?: core
    }

    private fun String.extractPrice(): Double? {
        return Regex("""\$(\d+(?:\.\d+)?)""").find(this)?.groupValues?.get(1)?.toDoubleOrNull()
    }

    private fun buildFingerprint(order: ParsedOrder): String {
        val f = order.flags
        return buildString {
            append(order.price).append('|')
            append(order.distance).append('|')
            append(order.dropoffs).append('|')
            append(order.storeName.orEmpty()).append('|')
            append(f.shopDeliverCurbside).append(f.shopAndDeliver).append(f.curbside)
            append(f.pharmacy).append(f.dotcom).append(f.customerReturns)
            append(f.bulkyItem).append(f.shopperBulk).append(f.apartment)
            append(f.customerVerification).append(f.alcohol).append(f.heavy)
        }
    }

    private fun maybeWarnUnknownSparkPackage(packageName: String) {
        if (packageName.isBlank()) return
        val looksLikeSpark = packageName.contains("spark", ignoreCase = true) ||
            packageName.contains("walmart", ignoreCase = true)
        if (looksLikeSpark) {
            warnThrottle.warn("unknown-pkg", getString(R.string.log_unknown_package, packageName))
        }
    }

    private fun isBotEnabled(): Boolean = currentSettings().enabled

    private fun currentSettings(): SparkSettings {
        val now = System.currentTimeMillis()
        cachedSettings?.takeIf { now - settingsLoadedAtMs < SETTINGS_CACHE_MS }?.let {
            SparkAutomationHub.turboMode = it.turboMode
            SparkAutomationHub.aggressiveTurbo = it.aggressiveTurbo
            SparkAutomationHub.superAggressiveTurbo = it.superAggressiveTurbo
            return it
        }
        return SettingsManager.loadSettings(this).also {
            SparkAutomationHub.turboMode = it.turboMode
            SparkAutomationHub.aggressiveTurbo = it.aggressiveTurbo
            SparkAutomationHub.superAggressiveTurbo = it.superAggressiveTurbo
            cachedSettings = it
            settingsLoadedAtMs = now
        }
    }

    private class WarnThrottle {
        private var lastKey: String? = null
        private var lastAtMs = 0L

        fun warn(key: String, message: String) {
            val now = System.currentTimeMillis()
            if (key == lastKey && now - lastAtMs < 4_000L) return
            lastKey = key
            lastAtMs = now
            OrderLog.warn(message)
        }
    }

    /** Tracks how many consecutive scans saw the same (no-progress) screen, for adaptive backoff. */
    private class ScanGuard {
        private var signature: String? = null
        private var repeats = 0
        private var loggedStuck = false

        /** Returns how many times this exact signature has now repeated (0 = first time). */
        fun seen(sig: String): Int {
            if (sig == signature) {
                repeats++
            } else {
                signature = sig
                repeats = 0
                loggedStuck = false
            }
            return repeats
        }

        fun clear() {
            signature = null
            repeats = 0
            loggedStuck = false
        }

        /** True only the first time we cross the stuck threshold for the current signature. */
        fun shouldLogStuck(): Boolean {
            if (loggedStuck) return false
            loggedStuck = true
            return true
        }
    }

    companion object {
        private const val SETTINGS_CACHE_MS = 3_000L
        private const val MAX_CONFIRM_ATTEMPTS = 24
    }
}
