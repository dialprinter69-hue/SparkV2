package com.example.sparkv2.automation

import android.view.accessibility.AccessibilityNodeInfo
import com.example.sparkv2.SparkIntents

data class ScreenSnapshot(
    val combinedText: String,
    val hasAcceptButton: Boolean,
    val hasDeclineButton: Boolean,
    val isDeclineConfirmDialog: Boolean,
    /** True when a post-acceptance "Start trip/shopping" control is on screen. */
    val hasStartTrip: Boolean = false,
    /**
     * How many clickable Accept buttons are on screen. The newer Spark "offer feed" stacks
     * several offers, each with its own inline Accept/Reject pair — so >1 means a multi-offer
     * list, not a single offer detail.
     */
    val acceptButtonCount: Int = 0,
)

object ScreenAnalyzer {
    fun analyze(root: AccessibilityNodeInfo): ScreenSnapshot {
        val maxNodes = ScanTiming.maxTreeNodes(SparkAutomationHub.speed())
        val texts = ArrayList<String>(64)
        var nodesVisited = 0
        var hasAccept = false
        var hasDecline = false
        var hasStartTrip = false
        var acceptButtonCount = 0

        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)

        while (stack.isNotEmpty() && nodesVisited < maxNodes) {
            val node = stack.removeFirst()
            nodesVisited++

            node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(texts::add)
            node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(texts::add)

            val label = effectiveLabel(node)
            if (label.isNotEmpty()) {
                if (TextMatcher.matchesIntent(label, SparkIntents.ACCEPT)) {
                    hasAccept = true
                    // Count only genuine actionable buttons so one card contributes one Accept,
                    // not also its inner text label. This is what lets us tell a single offer
                    // detail (1 Accept) apart from the stacked offer feed (2+ Accepts).
                    if (isActionable(node)) acceptButtonCount++
                }
                if (TextMatcher.matchesIntent(label, SparkIntents.REJECT)) hasDecline = true
                if (TextMatcher.matchesIntent(label, SparkIntents.START_TRIP)) hasStartTrip = true
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(stack::addLast)
            }

            if (node !== root) {
                node.recycle()
            }
        }

        while (stack.isNotEmpty()) {
            val leftover = stack.removeFirst()
            if (leftover !== root) leftover.recycle()
        }

        val combined = texts.distinct().joinToString(" | ")
        val looksLikeOffer = OrderTextHints.looksLikeOffer(combined)
        val isConfirmDialog = TextMatcher.matchesIntent(combined, SparkIntents.CONFIRM_DIALOG) ||
            (hasDecline && !hasAccept && !hasStartTrip && !looksLikeOffer && combined.length < 280)

        return ScreenSnapshot(
            combinedText = combined,
            hasAcceptButton = hasAccept,
            hasDeclineButton = hasDecline,
            isDeclineConfirmDialog = isConfirmDialog,
            hasStartTrip = hasStartTrip,
            acceptButtonCount = acceptButtonCount,
        )
    }

    /**
     * The stacked "offer feed": several offer cards on one screen, each with its own inline
     * Accept/Reject. We must NOT treat this as a single offer (the combined text mixes two
     * payouts/distances) — instead open one card's detail and evaluate it in isolation.
     */
    fun isMultiOfferList(snapshot: ScreenSnapshot): Boolean {
        if (snapshot.hasStartTrip) return false
        return snapshot.acceptButtonCount >= 2
    }

    fun isOfferScreen(snapshot: ScreenSnapshot): Boolean {
        // An accepted trip ("Start trip"/"Start shopping" on screen) is never an offer — the
        // driver advances it manually so they can still cancel.
        if (snapshot.hasStartTrip) return false
        if (snapshot.hasAcceptButton && snapshot.hasDeclineButton) return true
        // Some Spark offer screens use a slide-to-accept control that exposes no "Accept"
        // text. In that case a Decline button + offer-shaped text is enough to recognise it.
        if (snapshot.hasAcceptButton || snapshot.hasDeclineButton) {
            return OrderTextHints.looksLikeOffer(snapshot.combinedText)
        }
        return false
    }

    fun looksLikeOfferList(snapshot: ScreenSnapshot): Boolean {
        if (snapshot.hasStartTrip) return false
        return !snapshot.hasAcceptButton &&
            (OrderTextHints.looksLikeOffer(snapshot.combinedText) ||
                TextMatcher.matchesIntent(snapshot.combinedText, SparkIntents.OFFER_CARD))
    }

    private fun labelOf(node: AccessibilityNodeInfo): String {
        return listOfNotNull(
            node.text?.toString(),
            node.contentDescription?.toString(),
        ).joinToString(" ").trim()
    }

    /** Compose buttons often expose their label on a child while the parent is actionable. */
    private fun effectiveLabel(node: AccessibilityNodeInfo): String {
        val own = labelOf(node)
        if (own.isNotEmpty()) return own
        if (!isActionable(node)) return own
        val sb = StringBuilder()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until node.childCount) node.getChild(i)?.let(stack::addLast)
        var visited = 0
        while (stack.isNotEmpty() && visited < 24) {
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

    private fun isActionable(node: AccessibilityNodeInfo): Boolean {
        return node.isClickable ||
            node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }
    }
}

object OrderTextHints {
    private val priceHint = Regex(
        """(?:[\$€]|usd|pay|payout|earn|earning|estimated|est\.?|ganar)\s*[\$€]?\s*\d+(?:\.\d{1,2})?""",
        RegexOption.IGNORE_CASE,
    )
    private val barePriceHint = Regex("""[\$€]\s*\d+(?:\.\d{1,2})?""")
    private val bareEarningsHint = Regex(
        """(?:earn|ganar(?:ás)?|you(?:'|')?ll\s+(?:earn|make))\s+\d+(?:\.\d{1,2})?(?!\d)""",
        RegexOption.IGNORE_CASE,
    )
    private val distanceHint = Regex(
        """\d+(?:\.\d+)?\s*(?:mi|mile|miles|km|kilometer|kilometers)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val distanceLabelHint = Regex(
        """(?:distance|dist|total|trip)\s*:?\s*\d+(?:\.\d+)?""",
        RegexOption.IGNORE_CASE,
    )

    fun looksLikeOffer(text: String): Boolean {
        val hasPrice = priceHint.containsMatchIn(text) ||
            barePriceHint.containsMatchIn(text) ||
            bareEarningsHint.containsMatchIn(text)
        val hasDistance = distanceHint.containsMatchIn(text) || distanceLabelHint.containsMatchIn(text)
        return hasPrice && hasDistance
    }
}
