package com.example.sparkv2.automation

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Diagnostic walker: dumps the live accessibility tree of the Spark window so we can see
 * exactly what text / buttons / view-ids the app actually exposes on a given device.
 *
 * This is the ground truth for debugging "no acepta / no rechaza": if the offer's price,
 * distance or Accept control never appears here, the matchers can't act on it.
 */
object NodeDumper {
    private const val MAX_NODES = 600
    private const val MAX_LINE_LEN = 80

    data class Dump(
        val text: String,
        val nodeCount: Int,
        val clickableCount: Int,
        val hasAnyText: Boolean,
    ) {
        /** Likely a FLAG_SECURE / canvas-rendered screen: tree present but no readable content. */
        val looksOpaque: Boolean get() = nodeCount <= 2 || !hasAnyText
    }

    fun dump(root: AccessibilityNodeInfo, emptyLabel: String = "(empty)"): Dump {
        val sb = StringBuilder()
        var nodeCount = 0
        var clickableCount = 0
        var hasAnyText = false

        // Depth-first with explicit stack so we can render indentation.
        data class Item(val node: AccessibilityNodeInfo, val depth: Int)
        val stack = ArrayDeque<Item>()
        stack.add(Item(root, 0))

        while (stack.isNotEmpty() && nodeCount < MAX_NODES) {
            val (node, depth) = stack.removeLast()
            nodeCount++

            val text = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            if (text.isNotEmpty() || desc.isNotEmpty()) hasAnyText = true

            val clickable = node.isClickable ||
                node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }
            if (clickable) clickableCount++

            // Only print nodes that carry signal: some text, or an interactive control.
            if (text.isNotEmpty() || desc.isNotEmpty() || clickable || node.isScrollable) {
                val bounds = Rect().also(node::getBoundsInScreen)
                sb.append("·".repeat(depth.coerceAtMost(8)))
                sb.append(shortClass(node.className?.toString()))
                if (text.isNotEmpty()) sb.append(" t=\"").append(clip(text)).append('"')
                if (desc.isNotEmpty()) sb.append(" d=\"").append(clip(desc)).append('"')
                node.viewIdResourceName?.let { sb.append(" id=").append(it.substringAfterLast('/')) }
                val flags = buildString {
                    if (clickable) append('C')
                    if (node.isScrollable) append('S')
                    if (node.isEnabled.not()) append('x')
                }
                if (flags.isNotEmpty()) sb.append(" [").append(flags).append(']')
                sb.append(" @").append(bounds.centerX()).append(',').append(bounds.centerY())
                sb.append('\n')
            }

            for (i in node.childCount - 1 downTo 0) {
                node.getChild(i)?.let { stack.addLast(Item(it, depth + 1)) }
            }
            if (node !== root) node.recycle()
        }

        while (stack.isNotEmpty()) {
            val leftover = stack.removeLast().node
            if (leftover !== root) leftover.recycle()
        }

        return Dump(sb.toString().ifBlank { emptyLabel }, nodeCount, clickableCount, hasAnyText)
    }

    private fun shortClass(cls: String?): String =
        cls?.substringAfterLast('.')?.removeSuffix("View") ?: "?"

    private fun clip(s: String): String =
        if (s.length <= MAX_LINE_LEN) s else s.take(MAX_LINE_LEN) + "…"
}
