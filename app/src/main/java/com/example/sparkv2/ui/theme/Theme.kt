package com.example.sparkv2.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = SparkBlue,
    onPrimary = Color(0xFF001A3D),
    primaryContainer = SparkBlueSoft,
    onPrimaryContainer = SparkBlueGlow,
    secondary = AcceptGreen,
    onSecondary = Color(0xFF00250F),
    secondaryContainer = AcceptGreenSoft,
    onSecondaryContainer = AcceptGreen,
    tertiary = SparkCyan,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    error = DeclineRed,
    onError = Color(0xFF3A0A0A),
    errorContainer = DeclineRedSoft,
    onErrorContainer = DeclineRed,
)

private val LightColorScheme = lightColorScheme(
    primary = SparkBlueDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E8FF),
    onPrimaryContainer = Color(0xFF0A2A5C),
    secondary = Color(0xFF16A34A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3F5DF),
    onSecondaryContainer = Color(0xFF06351A),
    tertiary = Color(0xFF0D9488),
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = Color(0xFFE8EEF7),
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutline,
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFCE0E0),
    onErrorContainer = Color(0xFF5A1212),
)

@Composable
fun SparkV2Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
