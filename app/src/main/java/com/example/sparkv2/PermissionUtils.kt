package com.example.sparkv2

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import com.example.sparkv2.service.SparkAccessibilityService
import com.example.sparkv2.service.SparkNotificationListener

object PermissionUtils {

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false

        val component = ComponentName(context, SparkAccessibilityService::class.java).flattenToString()
        return TextUtils.SimpleStringSplitter(':').let { splitter ->
            splitter.setString(enabledServices)
            generateSequence { if (splitter.hasNext()) splitter.next() else null }
                .any { it.equals(component, ignoreCase = true) }
        }
    }

    fun isNotificationListenerEnabled(context: Context): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false

        val component = ComponentName(context, SparkNotificationListener::class.java).flattenToString()
        return TextUtils.SimpleStringSplitter(':').let { splitter ->
            splitter.setString(enabledListeners)
            generateSequence { if (splitter.hasNext()) splitter.next() else null }
                .any { it.equals(component, ignoreCase = true) }
        }
    }
}
