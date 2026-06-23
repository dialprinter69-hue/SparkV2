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
        SparkAutomationHub.aggressiveTurbo = settings.aggressiveTurbo
        SparkAutomationHub.superAggressiveTurbo = settings.superAggressiveTurbo
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
        SparkAutomationHub.requestScan(
            "notification",
            delayMs = ScanTiming.notificationScanDelay(speed),
        )
        SparkAutomationHub.service?.scheduleFollowUpScans()
    }

    companion object {
        private var lastNotificationId = -1
        private var lastNotificationAtMs = 0L
    }
}
