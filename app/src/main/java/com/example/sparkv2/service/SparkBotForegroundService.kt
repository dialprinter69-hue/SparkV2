package com.example.sparkv2.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.example.sparkv2.MainActivity
import com.example.sparkv2.R
import com.example.sparkv2.automation.SparkAutomationHub
import com.example.sparkv2.data.OrderLog
import com.example.sparkv2.data.SettingsManager
import com.example.sparkv2.data.StatsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SparkBotForegroundService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val keepAliveRunnable = object : Runnable {
        override fun run() {
            if (!SettingsManager.loadSettings(this@SparkBotForegroundService).enabled) {
                stopSelf()
                return
            }
            if (SparkAutomationHub.service == null) {
                OrderLog.warn(getString(R.string.log_accessibility_disconnected))
            }
            handler.postDelayed(this, KEEP_ALIVE_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val settings = SettingsManager.loadSettings(this)
        if (!settings.enabled) {
            stopSelf()
            return START_NOT_STICKY
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(getString(R.string.foreground_notification_text)),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )

        observeStats()
        handler.removeCallbacks(keepAliveRunnable)
        handler.postDelayed(keepAliveRunnable, KEEP_ALIVE_INTERVAL_MS)
        OrderLog.add(getString(R.string.log_foreground_active))
        return START_STICKY
    }

    /** Keep the foreground notification showing live today-totals as offers are handled. */
    private fun observeStats() {
        scope.launch {
            StatsStore.stats.collectLatest { stats ->
                val text = getString(
                    R.string.foreground_notification_live,
                    stats.todayAccepted,
                    "%.2f".format(stats.todayEarnings),
                    stats.todayDeclined,
                )
                runCatching {
                    NotificationManagerCompat.from(this@SparkBotForegroundService)
                        .notify(NOTIFICATION_ID, buildNotification(text))
                }
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(keepAliveRunnable)
        scope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.foreground_notification_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "spark_bot_channel"
        private const val NOTIFICATION_ID = 1001
        private const val KEEP_ALIVE_INTERVAL_MS = 60_000L
    }
}
