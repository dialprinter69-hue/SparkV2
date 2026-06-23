package com.example.sparkv2.automation

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.example.sparkv2.SparkConstants
import com.example.sparkv2.service.SparkAccessibilityService

/**
 * Finds the Spark Driver window root — not only [AccessibilityService.rootInActiveWindow],
 * which can point at overlays or the wrong layer on some devices.
 */
object SparkWindowFinder {

    fun findSparkRoot(service: SparkAccessibilityService): AccessibilityNodeInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val windows = service.windows
            if (windows != null) {
                var fallback: AccessibilityNodeInfo? = null
                for (window in windows) {
                    if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue
                    val root = window.root ?: continue
                    val pkg = root.packageName?.toString().orEmpty()
                    if (!SparkConstants.isSparkPackage(pkg)) {
                        root.recycle()
                        continue
                    }
                    if (window.isActive || window.isFocused) {
                        return root
                    }
                    if (fallback == null) {
                        fallback = AccessibilityNodeInfo.obtain(root)
                    }
                    root.recycle()
                }
                if (fallback != null) return fallback
            }
        }

        val active = service.rootInActiveWindow ?: return null
        val pkg = active.packageName?.toString().orEmpty()
        return if (SparkConstants.isSparkPackage(pkg)) active else {
            active.recycle()
            null
        }
    }
}
