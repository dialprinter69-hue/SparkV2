package com.example.sparkv2

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.sparkv2.data.SettingsManager
import com.example.sparkv2.service.SparkBotForegroundService

object SparkBotController {

    fun syncForegroundService(context: Context) {
        val settings = SettingsManager.loadSettings(context)
        val intent = Intent(context, SparkBotForegroundService::class.java)
        if (settings.enabled) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.stopService(intent)
        }
    }
}
