package com.example.sparkv2.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.sparkv2.R
import com.example.sparkv2.SparkConstants
import com.example.sparkv2.automation.ScanTiming
import com.example.sparkv2.automation.SparkAutomationHub
import com.example.sparkv2.data.OrderLog
import com.example.sparkv2.data.SettingsManager

class SparkNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!SparkConstants.isSparkPackage(sbn.packageName)) return
        val settings = SettingsManager.loadSettings(this)
        SparkAutomationHub.turboMode = settings.turboMode
        if (!settings.enabled) return

        val now = System.currentTimeMillis()
        val speed = SparkAutomationHub.speed()
        if (sbn.id == lastNotificationId && now - lastNotificationAtMs < ScanTiming.notificationDedup(speed)) {
            return
        }
        lastNotificationId = sbn.id
        lastNotificationAtMs = now

        // We deliberately do NOT open Spark via contentIntent here: the offer screen comes to
        // the foreground on its own, and launching the app re-focused/restarted it on every
        // alert (visible jank). The notification is used only as a fast hint to scan — the
        // accessibility service reads the offer once it's actually on screen.
        OrderLog.alert(getString(R.string.log_spark_alert_scanning))
        SparkAutomationHub.markAlertBoost()
        // Scan immediately in case the offer screen is already up — this shaves the fixed
        // notification delay off detection — then keep the staggered follow-ups for the case
        // where the screen is still coming to the foreground.
        SparkAutomationHub.requestScan("notification", delayMs = 0)
        SparkAutomationHub.service?.scheduleFollowUpScans()
    }

    companion object {
        private var lastNotificationId = -1
        private var lastNotificationAtMs = 0L
    }
}
