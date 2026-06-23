package com.example.sparkv2.automation

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.example.sparkv2.SparkIntents

object AccessibilityActions {

    fun clickAccept(root: AccessibilityNodeInfo, preferTopmost: Boolean = false): Boolean {
        return clickIntent(
            root = root,
            intent = SparkIntents.ACCEPT,
            exclude = SparkIntents.START_TRIP,
            augmentSubtree = true,
            preferActionable = true,
            preferTopmost = preferTopmost,
        )
    }

    fun clickReject(root: AccessibilityNodeInfo, preferTopmost: Boolean = false): Boolean {
        // Step 1 on the offer screen. Compose often puts the label on a non-clickable text
        // child while the parent row is clickable — so we must NOT requireClickable here
        // (that blocked every tap and made step 1 fail). We still prefer real buttons and
        // skip long dialog-style copy so we don't gesture-tap a paragraph by accident.
        return clickIntent(
            root = root,
            intent = SparkIntents.REJECT,
            exclude = SparkIntents.START_TRIP,
            extraExcludes = listOf(SparkIntents.ACCEPT),
            skipIntents = listOf(SparkIntents.CONFIRM_DIALOG),
            skipLongIntentMinLength = 36,
            requireClickable = false,
            augmentSubtree = true,
            preferActionable = true,
            maxLabelLength = 40,
            preferTopmost = preferTopmost,
        )
    }

    fun clickConfirmDecline(root: AccessibilityNodeInfo): Boolean {
        // Step 2 on the confirmation sheet. Prefer short, actionable button labels and ignore
        // the dialog body ("Are you sure you want to reject…") which also contains "reject".
        val strict = ClickOptions(
            exclude = SparkIntents.CANCEL,
            extraExcludes = listOf(SparkIntents.ACCEPT, SparkIntents.START_TRIP),
            skipIntents = listOf(SparkIntents.CONFIRM_DIALOG),
            skipLongIntentMinLength = 32,
            requireClickable = true,
            augmentSubtree = true,
            preferActionable = true,
            maxLabelLength = 28,
        )
        if (clickDuringTraversal(root, listOf(SparkIntents.REJECT_CONFIRM), strict)) return true
        if (clickDuringTraversal(root, listOf(SparkIntents.REJECT), strict)) return true

        // Compose confirm sheets sometimes expose the label without a clickable flag.
        val relaxed = strict.copy(requireClickable = false, maxLabelLength = 32)
        return clickDuringTraversal(root, listOf(SparkIntents.REJECT_CONFIRM), relaxed) ||
            clickDuringTraversal(root, listOf(SparkIntents.REJECT), relaxed)
    }

    fun openOfferDetail(root: AccessibilityNodeInfo): Boolean {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)

        // Conservative on purpose: only tap an actionable node whose own/subtree text is an
        // UNAMBIGUOUS offer — i.e. it carries BOTH a price AND a distance (OrderTextHints
        // .looksLikeOffer). A loose keyword like "estimated"/"trip" is NOT enough, and we never
        // fall back to a blind gesture. This stops the bot from wandering into Help / "Popular
        // Resources" / map info screens when no real offer card is present. Worst case it returns
        // false (does nothing) instead of tapping the wrong control.
        var bestNode: AccessibilityNodeInfo? = null
        var bestTop = Int.MAX_VALUE
        var nodesVisited = 0
        val rect = Rect()

        val maxNodes = ScanTiming.maxTreeNodes(SparkAutomationHub.speed())
        while (stack.isNotEmpty() && nodesVisited < maxNodes) {
            val node = stack.removeFirst()
            nodesVisited++

            val label = labelOf(node)
            val isStartTrip = TextMatcher.matchesIntent(label, SparkIntents.START_TRIP)

            if (!isStartTrip && isActionable(node)) {
                val text = if (label.isNotEmpty()) "$label ${collectSubtreeText(node)}".trim()
                else collectSubtreeText(node)
                // Require a full offer signal (price + distance) on this clickable node's subtree.
                if (OrderTextHints.looksLikeOffer(text)) {
                    node.getBoundsInScreen(rect)
                    val top = if (rect.isEmpty) Int.MAX_VALUE - 1 else rect.top
                    // Pick the top-most qualifying card so we always open the first offer.
                    if (top < bestTop) {
                        bestNode?.takeIf { it !== root }?.recycle()
                        bestNode = AccessibilityNodeInfo.obtain(node)
                        bestTop = top
                    }
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(stack::addLast)
            }
            if (node !== root) node.recycle()
        }

        while (stack.isNotEmpty()) {
            val leftover = stack.removeFirst()
            if (leftover !== root) leftover.recycle()
        }

        val target = bestNode ?: return false
        return try {
            // requireClickable = true → only a real clickable node/ancestor, never a blind gesture.
            clickNode(target, requireClickable = true)
        } finally {
            if (target !== root) target.recycle()
        }
    }

    /** Aggregates text/contentDescription from a node's descendants (bounded), for card scoring. */
    private fun collectSubtreeText(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until node.childCount) node.getChild(i)?.let(stack::addLast)
        var visited = 0
        while (stack.isNotEmpty() && visited < SUBTREE_MAX_NODES) {
            val n = stack.removeFirst()
            visited++
            n.text?.toString()?.takeIf { it.isNotBlank() }?.let { sb.append(it).append(' ') }
            n.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { sb.append(it).append(' ') }
            for (i in 0 until n.childCount) n.getChild(i)?.let(stack::addLast)
            n.recycle()
        }
        while (stack.isNotEmpty()) stack.removeFirst().recycle()
        return sb.toString().trim()
    }

    private data class ClickOptions(
        val exclude: TextIntent? = null,
        val extraExcludes: List<TextIntent> = emptyList(),
        val skipIntents: List<TextIntent> = emptyList(),
        val skipLongIntentMinLength: Int = Int.MAX_VALUE,
        val requireClickable: Boolean = false,
        val augmentSubtree: Boolean = false,
        val preferActionable: Boolean = false,
        val maxLabelLength: Int = Int.MAX_VALUE,
        val preferTopmost: Boolean = false,
    )

    private fun clickIntent(
        root: AccessibilityNodeInfo,
        intent: TextIntent,
        exclude: TextIntent? = null,
        extraExcludes: List<TextIntent> = emptyList(),
        skipIntents: List<TextIntent> = emptyList(),
        skipLongIntentMinLength: Int = Int.MAX_VALUE,
        requireClickable: Boolean = false,
        augmentSubtree: Boolean = false,
        preferActionable: Boolean = false,
        maxLabelLength: Int = Int.MAX_VALUE,
        preferTopmost: Boolean = false,
    ): Boolean {
        return clickDuringTraversal(
            root = root,
            intents = listOf(intent),
            options = ClickOptions(
                exclude = exclude,
                extraExcludes = extraExcludes,
                skipIntents = skipIntents,
                skipLongIntentMinLength = skipLongIntentMinLength,
                requireClickable = requireClickable,
                augmentSubtree = augmentSubtree,
                preferActionable = preferActionable,
                maxLabelLength = maxLabelLength,
                preferTopmost = preferTopmost,
            ),
        )
    }

    private fun clickDuringTraversal(
        root: AccessibilityNodeInfo,
        intents: List<TextIntent>,
        options: ClickOptions,
    ): Boolean {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)

        var bestNode: AccessibilityNodeInfo? = null
        var bestScore = -1
        var bestActionable = false
        var bestLabelLength = Int.MAX_VALUE
        var bestTop = Int.MAX_VALUE
        var nodesVisited = 0
        val rect = Rect()

        val maxNodes = ScanTiming.maxTreeNodes(SparkAutomationHub.speed())
        while (stack.isNotEmpty() && nodesVisited < maxNodes) {
            val node = stack.removeFirst()
            nodesVisited++

            val ownLabel = labelOf(node)
            // Material buttons sometimes keep their text on a child while the clickable wrapper
            // has no label of its own. When asked, fall back to the subtree text so an
            // empty-labeled Reject/Confirm button is still found. The opposite action is in
            // `exclude`/`extraExcludes`, so a wrapper covering both buttons is skipped, not tapped.
            val label = if (options.augmentSubtree && ownLabel.isEmpty() && isActionable(node)) {
                collectSubtreeText(node)
            } else {
                ownLabel
            }
            if (label.isNotEmpty()) {
                if (label.length > options.maxLabelLength) {
                    for (i in 0 until node.childCount) node.getChild(i)?.let(stack::addLast)
                    if (node !== root) node.recycle()
                    continue
                }
                if (options.skipIntents.any {
                        label.length >= options.skipLongIntentMinLength &&
                            TextMatcher.matchesIntent(label, it)
                    }
                ) {
                    for (i in 0 until node.childCount) node.getChild(i)?.let(stack::addLast)
                    if (node !== root) node.recycle()
                    continue
                }
                if (options.exclude != null && TextMatcher.matchesIntent(label, options.exclude)) {
                    for (i in 0 until node.childCount) node.getChild(i)?.let(stack::addLast)
                    if (node !== root) node.recycle()
                    continue
                }
                if (options.extraExcludes.any { TextMatcher.matchesIntent(label, it) }) {
                    for (i in 0 until node.childCount) node.getChild(i)?.let(stack::addLast)
                    if (node !== root) node.recycle()
                    continue
                }
                var score = intents.maxOf { TextMatcher.scoreIntent(label, it) }
                if (score > 0) {
                    val actionable = isActionable(node)
                    // When requireClickable is set, skip non-actionable nodes entirely —
                    // this prevents a gesture-tap on a text label landing on the wrong button.
                    if (options.requireClickable && !actionable) {
                        for (i in 0 until node.childCount) node.getChild(i)?.let(stack::addLast)
                        if (node !== root) node.recycle()
                        continue
                    }
                    if (options.preferActionable && actionable) score += ACTIONABLE_SCORE_BOOST
                    node.getBoundsInScreen(rect)
                    val top = if (rect.isEmpty) Int.MAX_VALUE else rect.top
                    val better = bestNode == null ||
                        score > bestScore ||
                        (score == bestScore && options.preferTopmost && top < bestTop) ||
                        (score == bestScore && (!options.preferTopmost || top == bestTop) && actionable && !bestActionable) ||
                        (score == bestScore && (!options.preferTopmost || top == bestTop) && actionable == bestActionable && label.length < bestLabelLength)
                    if (better) {
                        bestNode?.takeIf { it !== root }?.recycle()
                        bestNode = AccessibilityNodeInfo.obtain(node)
                        bestScore = score
                        bestActionable = actionable
                        bestLabelLength = label.length
                        bestTop = top
                        if (actionable && score >= 95) {
                            while (stack.isNotEmpty()) {
                                val leftover = stack.removeFirst()
                                if (leftover !== root) leftover.recycle()
                            }
                            break
                        }
                    }
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(stack::addLast)
            }
            if (node !== root) node.recycle()
        }

        while (stack.isNotEmpty()) {
            val leftover = stack.removeFirst()
            if (leftover !== root) leftover.recycle()
        }

        val target = bestNode ?: return false
        return try {
            if (bestScore > 0) clickNode(target, options.requireClickable) else false
        } finally {
            if (target !== root) target.recycle()
        }
    }

    private fun labelOf(node: AccessibilityNodeInfo): String {
        return listOfNotNull(
            node.text?.toString(),
            node.contentDescription?.toString(),
        ).joinToString(" ").trim()
    }

    private fun isActionable(node: AccessibilityNodeInfo): Boolean {
        return node.isClickable ||
            node.isFocusable ||
            node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }
    }

    private fun clickNode(node: AccessibilityNodeInfo, requireClickable: Boolean = false): Boolean {
        if (performClick(node)) return true

        var parent = node.parent
        while (parent != null) {
            if (performClick(parent)) {
                parent.recycle()
                return true
            }
            val next = parent.parent
            parent.recycle()
            parent = next
        }

        // Never use an imprecise gesture tap when the caller demands a clickable target —
        // the gesture coordinates could land on an adjacent button (e.g. Accept instead of Decline).
        if (requireClickable) return false
        return dispatchGestureTap(node)
    }

    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        return node.actionList.any { action ->
            action.id == AccessibilityNodeInfo.ACTION_CLICK &&
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    /**
     * Taps the centre of a screen rectangle via a gesture. Used by the OCR fallback to press a
     * button it could only see as recognised text (canvas-rendered, no clickable node). Honours the
     * same cooldown as node gesture taps so we never double-fire.
     */
    fun tapRect(service: android.accessibilityservice.AccessibilityService, bounds: Rect): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastGestureAtMs < ScanTiming.gestureCooldown(SparkAutomationHub.speed())) return false
        if (bounds.isEmpty) return false
        val path = android.graphics.Path().apply {
            moveTo(bounds.centerX().toFloat(), bounds.centerY().toFloat())
        }
        val strokeMs = ScanTiming.gestureStrokeMs(SparkAutomationHub.speed())
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(
                android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, strokeMs),
            )
            .build()
        val dispatched = service.dispatchGesture(gesture, null, null)
        if (dispatched) lastGestureAtMs = now
        return dispatched
    }

    private var lastGestureAtMs = 0L

    private fun dispatchGestureTap(node: AccessibilityNodeInfo): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastGestureAtMs < ScanTiming.gestureCooldown(SparkAutomationHub.speed())) return false

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.isEmpty) return false

        val service = SparkAutomationHub.service ?: return false
        val path = android.graphics.Path().apply {
            moveTo(bounds.centerX().toFloat(), bounds.centerY().toFloat())
        }
        val strokeMs = ScanTiming.gestureStrokeMs(SparkAutomationHub.speed())
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(
                android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, strokeMs),
            )
            .build()

        val dispatched = service.dispatchGesture(gesture, null, null)
        if (dispatched) lastGestureAtMs = now
        return dispatched
    }

    private const val SUBTREE_MAX_NODES = 60
    private const val ACTIONABLE_SCORE_BOOST = 40
}
