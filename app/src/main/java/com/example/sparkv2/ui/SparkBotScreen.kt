package com.example.sparkv2.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.annotation.StringRes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.sparkv2.PermissionUtils
import com.example.sparkv2.R
import com.example.sparkv2.SparkBotController
import com.example.sparkv2.automation.SparkAutomationHub
import com.example.sparkv2.data.CriterionId
import com.example.sparkv2.data.CriterionResult
import com.example.sparkv2.data.FlagLabel
import com.example.sparkv2.data.OfferHistory
import com.example.sparkv2.data.OfferOutcome
import com.example.sparkv2.data.OfferRecord
import com.example.sparkv2.data.OrderLog
import com.example.sparkv2.data.QuickMode
import com.example.sparkv2.data.SettingsManager
import com.example.sparkv2.data.SparkSettings
import com.example.sparkv2.data.StatsStore
import com.example.sparkv2.data.StoreFilter
import com.example.sparkv2.data.StoreFilterMode
import com.example.sparkv2.data.WalmartStore
import com.example.sparkv2.data.WalmartStoreCatalog
import com.example.sparkv2.ui.theme.SparkBlue
import com.example.sparkv2.ui.theme.SparkBlueGlow
import com.example.sparkv2.ui.theme.SparkCyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val STORE_SEARCH_DEBOUNCE_MS = 300L

private enum class SparkBotTab {
    Settings,
    Decisions,
}

@Composable
fun SparkBotScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(SettingsManager.loadSettings(context)) }
    val snapshot by OfferHistory.snapshot.collectAsState()
    val dailyStats by StatsStore.stats.collectAsState()
    var accessibilityEnabled by remember {
        mutableStateOf(PermissionUtils.isAccessibilityServiceEnabled(context))
    }
    var notificationEnabled by remember {
        mutableStateOf(PermissionUtils.isNotificationListenerEnabled(context))
    }
    var showStopConfirm by remember { mutableStateOf(false) }
    var pendingStart by remember { mutableStateOf(false) }

    val canStart = accessibilityEnabled && notificationEnabled

    fun persist(newSettings: SparkSettings) {
        settings = newSettings
        SparkAutomationHub.turboMode = newSettings.turboMode
        SparkAutomationHub.aggressiveTurbo = newSettings.aggressiveTurbo
        SparkAutomationHub.superAggressiveTurbo = newSettings.superAggressiveTurbo
        SettingsManager.saveSettings(context, newSettings)
    }

    fun update(transform: (SparkSettings) -> SparkSettings) {
        persist(transform(settings).copy(quickMode = null))
    }

    fun applyQuickMode(mode: QuickMode) {
        persist(mode.toSettings(settings))
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!pendingStart) return@rememberLauncherForActivityResult
        pendingStart = false
        if (granted) {
            val a11y = PermissionUtils.isAccessibilityServiceEnabled(context)
            val notif = PermissionUtils.isNotificationListenerEnabled(context)
            accessibilityEnabled = a11y
            notificationEnabled = notif
            if (a11y && notif) {
                persist(settings.copy(enabled = true))
                SparkBotController.syncForegroundService(context)
            }
        } else {
            OrderLog.warn(context.getString(R.string.log_notification_denied))
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                StatsStore.refreshDayRoll()
                SettingsManager.invalidateCache()
                val fresh = SettingsManager.loadSettings(context)
                val a11y = PermissionUtils.isAccessibilityServiceEnabled(context)
                val notif = PermissionUtils.isNotificationListenerEnabled(context)
                accessibilityEnabled = a11y
                notificationEnabled = notif
                settings = if (fresh.enabled && (!a11y || !notif)) {
                    val disabled = fresh.copy(enabled = false)
                    SettingsManager.saveSettings(context, disabled)
                    SparkBotController.syncForegroundService(context)
                    OrderLog.warn(context.getString(R.string.log_start_blocked))
                    SparkAutomationHub.turboMode = disabled.turboMode
                    SparkAutomationHub.aggressiveTurbo = disabled.aggressiveTurbo
                    SparkAutomationHub.superAggressiveTurbo = disabled.superAggressiveTurbo
                    disabled
                } else {
                    SparkAutomationHub.turboMode = fresh.turboMode
                    SparkAutomationHub.aggressiveTurbo = fresh.aggressiveTurbo
                    SparkAutomationHub.superAggressiveTurbo = fresh.superAggressiveTurbo
                    fresh
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun startBot() {
        if (!canStart) {
            OrderLog.warn(context.getString(R.string.log_start_blocked))
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                pendingStart = true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        persist(settings.copy(enabled = true))
        SparkBotController.syncForegroundService(context)
    }

    fun toggleBot() {
        if (settings.enabled) {
            showStopConfirm = true
        } else {
            startBot()
        }
    }

    if (showStopConfirm) {
        AlertDialog(
            onDismissRequest = { showStopConfirm = false },
            title = { Text(stringResource(R.string.master_stop)) },
            text = { Text(stringResource(R.string.stop_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showStopConfirm = false
                        persist(settings.copy(enabled = false))
                        SparkBotController.syncForegroundService(context)
                    },
                ) {
                    Text(stringResource(R.string.master_stop))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirm = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    val backgroundColor = MaterialTheme.colorScheme.background
    var selectedTab by remember {
        mutableIntStateOf(
            if (settings.enabled) SparkBotTab.Decisions.ordinal else SparkBotTab.Settings.ordinal,
        )
    }

    LaunchedEffect(settings.enabled) {
        if (settings.enabled) {
            selectedTab = SparkBotTab.Decisions.ordinal
        }
    }

    val settingsTabLabel = stringResource(R.string.tab_settings)
    val decisionsTabLabel = stringResource(R.string.tab_decisions)

    Column(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(backgroundColor)
                // Primary top glow — bolder, electric.
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SparkBlue.copy(alpha = 0.22f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.05f),
                        radius = size.width * 0.95f,
                    ),
                    radius = size.width * 0.95f,
                    center = Offset(size.width * 0.5f, size.height * 0.05f),
                )
                // Cyan counter-glow drifting in from the lower edge for depth.
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SparkCyan.copy(alpha = 0.07f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.12f, size.height * 0.92f),
                        radius = size.width * 0.7f,
                    ),
                    radius = size.width * 0.7f,
                    center = Offset(size.width * 0.12f, size.height * 0.92f),
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HeroSection(
                running = settings.enabled,
                turboMode = settings.turboMode,
                aggressiveTurbo = settings.aggressiveTurbo,
                superAggressiveTurbo = settings.superAggressiveTurbo,
                canStart = canStart,
                accessibilityEnabled = accessibilityEnabled,
                notificationEnabled = notificationEnabled,
                onToggle = ::toggleBot,
                onTurboChange = { enabled ->
                    update {
                        it.copy(
                            turboMode = enabled,
                            aggressiveTurbo = if (enabled) it.aggressiveTurbo else false,
                            superAggressiveTurbo = if (enabled) it.superAggressiveTurbo else false,
                        )
                    }
                },
            )

            if (!canStart && !settings.enabled) {
                PermissionsBanner()
            }
        }

        PrimaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            modifier = Modifier.padding(horizontal = 20.dp),
        ) {
            Tab(
                selected = selectedTab == SparkBotTab.Settings.ordinal,
                onClick = { selectedTab = SparkBotTab.Settings.ordinal },
                modifier = Modifier.semantics {
                    contentDescription = settingsTabLabel
                    selected = selectedTab == SparkBotTab.Settings.ordinal
                },
                text = { Text(settingsTabLabel) },
            )
            Tab(
                selected = selectedTab == SparkBotTab.Decisions.ordinal,
                onClick = { selectedTab = SparkBotTab.Decisions.ordinal },
                modifier = Modifier.semantics {
                    contentDescription = decisionsTabLabel
                    selected = selectedTab == SparkBotTab.Decisions.ordinal
                },
                text = { Text(decisionsTabLabel) },
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when (SparkBotTab.entries[selectedTab]) {
                SparkBotTab.Settings -> SettingsTabContent(
                    settings = settings,
                    accessibilityEnabled = accessibilityEnabled,
                    notificationEnabled = notificationEnabled,
                    onGrantAccessibility = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onGrantNotification = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onSelectQuickMode = ::applyQuickMode,
                    onPersist = ::persist,
                    onUpdate = ::update,
                )
                SparkBotTab.Decisions -> DecisionsTabContent(
                    dailyStats = dailyStats,
                    records = snapshot.records,
                )
            }
        }
    }
}

@Composable
private fun SettingsTabContent(
    settings: SparkSettings,
    accessibilityEnabled: Boolean,
    notificationEnabled: Boolean,
    onGrantAccessibility: () -> Unit,
    onGrantNotification: () -> Unit,
    onSelectQuickMode: (QuickMode) -> Unit,
    onPersist: (SparkSettings) -> Unit,
    onUpdate: ((SparkSettings) -> SparkSettings) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        // Only surface permissions while something is missing — once granted,
        // it collapses out of the way for a cleaner first screen.
        if (!accessibilityEnabled || !notificationEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(R.string.permissions_section))
                PermissionsCard(
                    accessibilityEnabled = accessibilityEnabled,
                    notificationEnabled = notificationEnabled,
                    onGrantAccessibility = onGrantAccessibility,
                    onGrantNotification = onGrantNotification,
                )
            }
        }

        // Quick Mode leads — the one choice most users actually make.
        val quickModeSummary = when (settings.quickMode) {
            QuickMode.PREMIUM -> stringResource(R.string.quick_mode_premium)
            QuickMode.PICKY -> stringResource(R.string.quick_mode_picky)
            QuickMode.BALANCED -> stringResource(R.string.quick_mode_balanced)
            QuickMode.SLOW_SHIFT -> stringResource(R.string.quick_mode_slow_shift)
            null -> stringResource(R.string.quick_mode_custom)
        }
        ExpandableSection(
            title = stringResource(R.string.section_quick_mode),
            initiallyExpanded = settings.quickMode == null,
            summary = quickModeSummary,
        ) {
            QuickModeCard(
                selected = settings.quickMode,
                onSelect = onSelectQuickMode,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(stringResource(R.string.section_automation))
            AutomationCard(settings = settings, onUpdate = onUpdate)
        }

        ExpandableSection(stringResource(R.string.section_stores)) {
            StoresCard(settings = settings, onChange = onPersist)
        }

        ExpandableSection(stringResource(R.string.section_filters)) {
            FiltersCard(settings = settings, onUpdate = onUpdate)
        }

        ExpandableSection(stringResource(R.string.section_advanced)) {
            AdvancedFiltersCard(settings = settings, onUpdate = onUpdate)
        }

        ExpandableSection(stringResource(R.string.section_goal)) {
            GoalAndHoursCard(settings = settings, onUpdate = onUpdate)
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun GoalAndHoursCard(
    settings: SparkSettings,
    onUpdate: ((SparkSettings) -> SparkSettings) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val off = stringResource(R.string.value_off)
    PremiumCard {
        Text(
            text = stringResource(R.string.goal_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        SliderSetting(
            label = stringResource(R.string.earnings_goal),
            formatValue = { if (it <= 0f) off else "$%.0f".format(it) },
            hint = stringResource(R.string.earnings_goal_hint),
            value = settings.earningsGoal,
            valueRange = 0f..400f,
            steps = 39,
            onValueCommitted = { v -> onUpdate { it.copy(earningsGoal = v) } },
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = scheme.outline)
        Spacer(Modifier.height(12.dp))

        AutomationToggleRow(
            title = stringResource(R.string.operating_hours),
            subtitle = stringResource(R.string.operating_hours_sub),
            checked = settings.operatingHoursEnabled,
            onCheckedChange = { enabled -> onUpdate { it.copy(operatingHoursEnabled = enabled) } },
        )
        if (settings.operatingHoursEnabled) {
            Spacer(Modifier.height(4.dp))
            Stepper(
                label = stringResource(R.string.start_hour),
                value = settings.startHour,
                min = 0,
                max = 23,
                onValueChange = { v -> onUpdate { it.copy(startHour = v) } },
                decreaseDescription = stringResource(R.string.decrease_hour),
                increaseDescription = stringResource(R.string.increase_hour),
            )
            Stepper(
                label = stringResource(R.string.end_hour),
                value = settings.endHour,
                min = 1,
                max = 24,
                onValueChange = { v -> onUpdate { it.copy(endHour = v) } },
                decreaseDescription = stringResource(R.string.decrease_hour),
                increaseDescription = stringResource(R.string.increase_hour),
            )
        }
    }
}

@Composable
private fun AdvancedFiltersCard(
    settings: SparkSettings,
    onUpdate: ((SparkSettings) -> SparkSettings) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val off = stringResource(R.string.value_off)
    PremiumCard {
        Text(
            text = stringResource(R.string.advanced_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        SliderSetting(
            label = stringResource(R.string.min_per_hour),
            formatValue = { if (it <= 0f) off else "$%.0f/hr".format(it) },
            hint = stringResource(R.string.min_per_hour_hint),
            value = settings.minDollarsPerHour,
            valueRange = 0f..40f,
            steps = 39,
            onValueCommitted = { v -> onUpdate { it.copy(minDollarsPerHour = v) } },
        )
        SliderSetting(
            label = stringResource(R.string.min_base_pay),
            formatValue = { if (it <= 0f) off else "$%.0f".format(it) },
            hint = stringResource(R.string.min_base_pay_hint),
            value = settings.minBasePay,
            valueRange = 0f..30f,
            steps = 29,
            onValueCommitted = { v -> onUpdate { it.copy(minBasePay = v) } },
        )
        SliderSetting(
            label = stringResource(R.string.max_tip_ratio),
            formatValue = { if (it >= 1f) off else "%.0f%%".format(it * 100) },
            hint = stringResource(R.string.max_tip_ratio_hint),
            value = settings.maxTipRatio,
            valueRange = 0.5f..1f,
            steps = 9,
            onValueCommitted = { v -> onUpdate { it.copy(maxTipRatio = v) } },
        )
        SliderSetting(
            label = stringResource(R.string.max_deadhead),
            formatValue = { if (it <= 0f) off else "%.0f mi".format(it) },
            hint = stringResource(R.string.max_deadhead_hint),
            value = settings.maxDeadhead,
            valueRange = 0f..15f,
            steps = 14,
            onValueCommitted = { v -> onUpdate { it.copy(maxDeadhead = v) } },
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = scheme.outline)
        Spacer(Modifier.height(12.dp))

        AutomationToggleRow(
            title = stringResource(R.string.item_limit),
            subtitle = stringResource(R.string.item_limit_sub),
            checked = settings.itemLimitEnabled,
            onCheckedChange = { enabled -> onUpdate { it.copy(itemLimitEnabled = enabled) } },
        )
        if (settings.itemLimitEnabled) {
            Spacer(Modifier.height(4.dp))
            Stepper(
                label = stringResource(R.string.max_items),
                value = settings.maxItems,
                min = 5,
                max = 100,
                step = 5,
                onValueChange = { v -> onUpdate { it.copy(maxItems = v) } },
                decreaseDescription = stringResource(R.string.decrease_items),
                increaseDescription = stringResource(R.string.increase_items),
            )
        }
    }
}

@Composable
private fun DecisionsTabContent(
    dailyStats: StatsStore.Stats,
    records: List<OfferRecord>,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        StatsSection(dailyStats)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel(stringResource(R.string.offer_history))
                if (records.isNotEmpty() || dailyStats.todayEvaluated > 0 || dailyStats.todayEarnings > 0.0) {
                    TextButton(
                        onClick = {
                            OfferHistory.clear()
                            StatsStore.resetToday()
                        },
                    ) {
                        Text(stringResource(R.string.clear_offers))
                    }
                }
            }
            OfferHistoryCard(records)
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ── Hero ───────────────────────────────────────────────────────────────

@Composable
private fun PermissionsBanner() {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = scheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.permissions_required_to_start),
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onErrorContainer,
        )
    }
}

@Composable
private fun HeroSection(
    running: Boolean,
    turboMode: Boolean,
    aggressiveTurbo: Boolean,
    superAggressiveTurbo: Boolean,
    canStart: Boolean,
    accessibilityEnabled: Boolean,
    notificationEnabled: Boolean,
    onToggle: () -> Unit,
    onTurboChange: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SparkRobotMascot(
                size = 76.dp,
                active = running && accessibilityEnabled && notificationEnabled,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = scheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        val heroShape = RoundedCornerShape(20.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(heroShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            scheme.surfaceVariant.copy(alpha = 0.7f),
                            scheme.surface.copy(alpha = 0.95f),
                        ),
                    ),
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            SparkBlue.copy(alpha = 0.35f),
                            Color.White.copy(alpha = 0.03f),
                        ),
                    ),
                    shape = heroShape,
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatusPill(
                    running = running,
                    turboMode = turboMode,
                    aggressiveTurbo = aggressiveTurbo,
                    superAggressiveTurbo = superAggressiveTurbo,
                    accessibility = accessibilityEnabled,
                    notification = notificationEnabled,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TurboQuickToggle(
                        checked = turboMode,
                        onCheckedChange = onTurboChange,
                    )
                    MasterButton(
                        running = running,
                        enabled = running || canStart,
                        onToggle = onToggle,
                    )
                }
            }
        }
    }
}

@Composable
private fun TurboQuickToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val label = stringResource(R.string.turbo_mode)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = label },
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (checked) scheme.primary else scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatusPill(
    running: Boolean,
    turboMode: Boolean,
    aggressiveTurbo: Boolean,
    superAggressiveTurbo: Boolean,
    accessibility: Boolean,
    notification: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val (label, dotColor) = when {
        !running -> stringResource(R.string.status_disabled) to scheme.onSurfaceVariant
        !accessibility && !notification -> stringResource(R.string.status_missing_both) to scheme.error
        !accessibility -> stringResource(R.string.status_missing_accessibility) to scheme.primary
        !notification -> stringResource(R.string.status_missing_notification) to scheme.primary
        else -> stringResource(R.string.status_active) to scheme.secondary
    }
    val active = running && accessibility && notification
    Surface(
        shape = CircleShape,
        color = scheme.surface.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, scheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PulsingDot(color = dotColor, pulsing = active)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurface,
                maxLines = 2,
            )
            if (turboMode && running) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = scheme.primaryContainer,
                ) {
                    Text(
                        text = stringResource(
                            when {
                                superAggressiveTurbo -> R.string.super_aggressive_turbo_badge
                                aggressiveTurbo -> R.string.aggressive_turbo_badge
                                else -> R.string.turbo_badge
                            },
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun MasterButton(running: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val label = stringResource(if (running) R.string.master_stop else R.string.master_start)
    val pendingLabel = stringResource(R.string.permissions_pending)
    Button(
        onClick = onToggle,
        enabled = enabled,
        modifier = Modifier
            .height(44.dp)
            .semantics {
                contentDescription = label
                if (!enabled) stateDescription = pendingLabel
            },
        shape = RoundedCornerShape(14.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 2.dp,
            pressedElevation = 0.dp,
        ),
        contentPadding = ButtonDefaults.ContentPadding,
        colors = if (running) {
            ButtonDefaults.buttonColors(
                containerColor = scheme.errorContainer,
                contentColor = scheme.onErrorContainer,
            )
        } else {
            ButtonDefaults.buttonColors(
                containerColor = SparkBlue,
                contentColor = scheme.onPrimary,
            )
        },
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(if (running) RoundedCornerShape(2.dp) else CircleShape)
                .background(LocalContentColor.current),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ── Stats ────────────────────────────────────────────────────────────────

@Composable
private fun StatsSection(stats: StatsStore.Stats) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.stat_today))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile(
                modifier = Modifier.weight(1f),
                value = stats.todayAccepted.toString(),
                label = stringResource(R.string.stat_accepted),
                accent = scheme.secondary,
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                value = stats.todayDeclined.toString(),
                label = stringResource(R.string.stat_declined),
                accent = scheme.error,
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                value = "$%.0f".format(stats.todayEarnings),
                label = stringResource(R.string.stat_earnings),
                accent = scheme.primary,
            )
        }
        PremiumCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.stat_accept_rate),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    text = "%.0f%%".format(stats.todayAcceptRate * 100),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurface,
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { stats.todayAcceptRate },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = scheme.secondary,
                trackColor = scheme.outline,
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = scheme.outline)
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.stat_lifetime),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.lifetime_summary,
                    stats.lifetimeAccepted,
                    stats.lifetimeDeclined,
                    "%.2f".format(stats.lifetimeEarnings),
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface,
            )
        }
    }
}

@Composable
private fun MetricTile(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    accent: Color,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.16f),
                        scheme.surface.copy(alpha = 0.92f),
                    ),
                ),
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.12f),
                        Color.White.copy(alpha = 0.02f),
                    ),
                ),
                shape = shape,
            )
            .padding(16.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = scheme.onSurface,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── Permissions ────────────────────────────────────────────────────────

@Composable
private fun PermissionsCard(
    accessibilityEnabled: Boolean,
    notificationEnabled: Boolean,
    onGrantAccessibility: () -> Unit,
    onGrantNotification: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    PremiumCard {
        if (accessibilityEnabled && notificationEnabled) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PulsingDot(color = scheme.secondary, pulsing = false, size = 12.dp)
                Text(
                    text = stringResource(R.string.permissions_all_ok),
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurface,
                )
            }
            return@PremiumCard
        }

        PermissionRow(
            title = stringResource(R.string.permission_accessibility),
            subtitle = stringResource(R.string.permission_accessibility_sub),
            granted = accessibilityEnabled,
        )
        if (!accessibilityEnabled) {
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onGrantAccessibility,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) { Text(stringResource(R.string.grant_accessibility_access)) }
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = scheme.outline)
        Spacer(Modifier.height(14.dp))

        PermissionRow(
            title = stringResource(R.string.permission_notifications),
            subtitle = stringResource(R.string.permission_notifications_sub),
            granted = notificationEnabled,
        )
        if (!notificationEnabled) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onGrantNotification,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) { Text(stringResource(R.string.grant_notification_access)) }
        }
    }
}

@Composable
private fun PermissionRow(title: String, subtitle: String, granted: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        StatusChip(granted)
    }
}

@Composable
private fun StatusChip(granted: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val bg = if (granted) scheme.secondaryContainer else scheme.surfaceVariant
    val fg = if (granted) scheme.onSecondaryContainer else scheme.primary
    Surface(shape = CircleShape, color = bg) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PulsingDot(color = fg, pulsing = false, size = 8.dp)
            Text(
                text = stringResource(if (granted) R.string.permission_granted else R.string.permission_missing),
                style = MaterialTheme.typography.labelMedium,
                color = fg,
            )
        }
    }
}

// ── Quick mode presets ─────────────────────────────────────────────────────

@Composable
private fun QuickModeCard(
    selected: QuickMode?,
    onSelect: (QuickMode) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    PremiumCard {
        Text(
            text = stringResource(R.string.quick_mode_hint),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickModeOption(
                title = stringResource(R.string.quick_mode_premium),
                subtitle = stringResource(R.string.quick_mode_premium_sub),
                volumeLabel = stringResource(R.string.volume_min),
                mode = QuickMode.PREMIUM,
                selected = selected == QuickMode.PREMIUM,
                onClick = { onSelect(QuickMode.PREMIUM) },
            )
            QuickModeOption(
                title = stringResource(R.string.quick_mode_picky),
                subtitle = stringResource(R.string.quick_mode_picky_sub),
                volumeLabel = stringResource(R.string.volume_low),
                mode = QuickMode.PICKY,
                selected = selected == QuickMode.PICKY,
                onClick = { onSelect(QuickMode.PICKY) },
            )
            QuickModeOption(
                title = stringResource(R.string.quick_mode_balanced),
                subtitle = stringResource(R.string.quick_mode_balanced_sub),
                volumeLabel = stringResource(R.string.volume_medium),
                mode = QuickMode.BALANCED,
                selected = selected == QuickMode.BALANCED,
                onClick = { onSelect(QuickMode.BALANCED) },
            )
            QuickModeOption(
                title = stringResource(R.string.quick_mode_slow_shift),
                subtitle = stringResource(R.string.quick_mode_slow_shift_sub),
                volumeLabel = stringResource(R.string.volume_high),
                mode = QuickMode.SLOW_SHIFT,
                selected = selected == QuickMode.SLOW_SHIFT,
                onClick = { onSelect(QuickMode.SLOW_SHIFT) },
            )
        }
        if (selected == null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.quick_mode_custom),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.primary,
            )
        }
    }
}

@Composable
private fun QuickModeOption(
    title: String,
    subtitle: String,
    volumeLabel: String,
    mode: QuickMode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val borderColor = if (selected) scheme.primary else scheme.outline
    val containerColor = if (selected) scheme.primaryContainer else scheme.surfaceVariant
    val preset = mode.preset

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "$title, $subtitle"
                this.selected = selected
                role = Role.RadioButton
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Presets filter on $/mile only — show it inline as the key stat.
            Text(
                text = stringResource(R.string.qm_value_per_mile, preset.dollarsPerMile),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
            )
            Surface(shape = CircleShape, color = if (selected) scheme.primary else scheme.outline) {
                Text(
                    text = volumeLabel,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Store / location filter ──────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoresCard(settings: SparkSettings, onChange: (SparkSettings) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val catalogCount = remember { WalmartStoreCatalog.count(context) }
    var searchResults by remember { mutableStateOf(emptyList<WalmartStore>()) }
    var isSearching by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery) {
        val query = searchQuery.trim()
        if (query.length < 2) {
            isSearching = false
            searchResults = emptyList()
            return@LaunchedEffect
        }
        isSearching = true
        delay(STORE_SEARCH_DEBOUNCE_MS)
        if (searchQuery.trim() != query) return@LaunchedEffect
        searchResults = withContext(Dispatchers.Default) {
            WalmartStoreCatalog.search(context, query)
        }
        isSearching = false
    }

    fun addStoreFilter(store: StoreFilter) {
        if (settings.stores.any {
                (store.storeId != null && it.storeId == store.storeId) ||
                    it.name.equals(store.name, ignoreCase = true)
            }
        ) {
            return
        }
        onChange(settings.copy(stores = settings.stores + store))
    }

    PremiumCard {
        Text(
            text = stringResource(R.string.stores_hint),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.store_catalog_count, catalogCount),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StoreModeChip(
                label = stringResource(R.string.store_mode_any),
                selected = settings.storeFilterMode == StoreFilterMode.ANY,
                onClick = { onChange(settings.copy(storeFilterMode = StoreFilterMode.ANY)) },
            )
            StoreModeChip(
                label = stringResource(R.string.store_mode_only),
                selected = settings.storeFilterMode == StoreFilterMode.ONLY_SELECTED,
                onClick = { onChange(settings.copy(storeFilterMode = StoreFilterMode.ONLY_SELECTED)) },
            )
        }

        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            placeholder = { Text(stringResource(R.string.store_search_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )

        Spacer(Modifier.height(8.dp))

        when {
            searchQuery.trim().length < 2 -> {
                Text(
                    text = stringResource(R.string.store_search_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            isSearching -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = scheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.store_searching),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            searchResults.isEmpty() -> {
                Text(
                    text = stringResource(R.string.store_search_no_results, searchQuery.trim()),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    searchResults.forEach { store ->
                        StoreSearchResultRow(
                            store = store,
                            alreadySelected = settings.stores.any { it.storeId == store.id },
                            onAdd = {
                                addStoreFilter(store.toStoreFilter())
                                searchQuery = ""
                            },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (settings.stores.isEmpty()) {
            Text(
                text = stringResource(R.string.stores_empty),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                settings.stores.forEach { store ->
                    StoreChip(
                        store = store,
                        onToggle = {
                            onChange(
                                settings.copy(
                                    stores = settings.stores.map {
                                        if (it.name == store.name && it.storeId == store.storeId) {
                                            it.copy(enabled = !it.enabled)
                                        } else {
                                            it
                                        }
                                    },
                                ),
                            )
                        },
                        onRemove = {
                            onChange(
                                settings.copy(
                                    stores = settings.stores.filterNot {
                                        it.name == store.name && it.storeId == store.storeId
                                    },
                                ),
                            )
                        },
                    )
                }
            }
        }

        val onlyEmpty = settings.storeFilterMode == StoreFilterMode.ONLY_SELECTED &&
            settings.stores.none { it.enabled && it.name.isNotBlank() }
        if (onlyEmpty) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.stores_only_empty),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.error,
            )
        }
    }
}

@Composable
private fun StoreSearchResultRow(
    store: WalmartStore,
    alreadySelected: Boolean,
    onAdd: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = scheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = store.displayLabel(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (store.zip.isNotBlank()) {
                    Text(
                        text = store.zip,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = onAdd, enabled = !alreadySelected) {
                Text(
                    if (alreadySelected) {
                        stringResource(R.string.store_added)
                    } else {
                        stringResource(R.string.store_add)
                    },
                )
            }
        }
    }
}

@Composable
private fun StoreModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) scheme.primaryContainer else scheme.surfaceVariant,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outline),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StoreChip(store: StoreFilter, onToggle: () -> Unit, onRemove: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val container = if (store.enabled) scheme.primaryContainer else scheme.surfaceVariant
    val content = if (store.enabled) scheme.onPrimaryContainer else scheme.onSurfaceVariant
    Surface(
        onClick = onToggle,
        shape = CircleShape,
        color = container,
        border = BorderStroke(1.dp, if (store.enabled) scheme.primary else scheme.outline),
        modifier = Modifier.semantics { contentDescription = store.name },
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = store.name, style = MaterialTheme.typography.labelLarge, color = content)
            Surface(
                onClick = onRemove,
                shape = CircleShape,
                color = content.copy(alpha = 0.15f),
                modifier = Modifier
                    .size(20.dp)
                    .semantics { contentDescription = "" },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = "×", style = MaterialTheme.typography.labelLarge, color = content)
                }
            }
        }
    }
}

// ── Automation toggles ───────────────────────────────────────────────────

@Composable
private fun AutomationCard(settings: SparkSettings, onUpdate: ((SparkSettings) -> SparkSettings) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    PremiumCard {
        AutomationToggleRow(
            title = stringResource(R.string.turbo_mode),
            subtitle = stringResource(R.string.turbo_mode_sub),
            checked = settings.turboMode,
            onCheckedChange = { enabled ->
                onUpdate {
                    it.copy(
                        turboMode = enabled,
                        aggressiveTurbo = if (enabled) it.aggressiveTurbo else false,
                        superAggressiveTurbo = if (enabled) it.superAggressiveTurbo else false,
                    )
                }
            },
        )
        if (settings.turboMode) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = scheme.outline)
            Spacer(Modifier.height(12.dp))
            AutomationToggleRow(
                title = stringResource(R.string.aggressive_turbo),
                subtitle = stringResource(R.string.aggressive_turbo_sub),
                checked = settings.aggressiveTurbo,
                onCheckedChange = { enabled ->
                    onUpdate {
                        it.copy(
                            aggressiveTurbo = enabled,
                            superAggressiveTurbo = if (enabled) it.superAggressiveTurbo else false,
                        )
                    }
                },
            )
        }
        if (settings.turboMode && settings.aggressiveTurbo) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = scheme.outline)
            Spacer(Modifier.height(12.dp))
            AutomationToggleRow(
                title = stringResource(R.string.super_aggressive_turbo),
                subtitle = stringResource(R.string.super_aggressive_turbo_sub),
                checked = settings.superAggressiveTurbo,
                onCheckedChange = { enabled -> onUpdate { it.copy(superAggressiveTurbo = enabled) } },
            )
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = scheme.outline)
        Spacer(Modifier.height(12.dp))
        AutomationToggleRow(
            title = stringResource(R.string.auto_accept),
            subtitle = stringResource(R.string.auto_accept_sub),
            checked = settings.autoAccept,
            onCheckedChange = { enabled -> onUpdate { it.copy(autoAccept = enabled) } },
        )
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = scheme.outline)
        Spacer(Modifier.height(12.dp))
        AutomationToggleRow(
            title = stringResource(R.string.auto_decline),
            subtitle = stringResource(R.string.auto_decline_sub),
            checked = settings.autoDecline,
            onCheckedChange = { enabled -> onUpdate { it.copy(autoDecline = enabled) } },
        )
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = scheme.outline)
        Spacer(Modifier.height(12.dp))
        AutomationToggleRow(
            title = stringResource(R.string.ocr_fallback),
            subtitle = stringResource(R.string.ocr_fallback_sub),
            checked = settings.ocrFallbackEnabled,
            onCheckedChange = { enabled -> onUpdate { it.copy(ocrFallbackEnabled = enabled) } },
        )
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = scheme.outline)
        Spacer(Modifier.height(12.dp))
        AutomationToggleRow(
            title = stringResource(R.string.debug_mode),
            subtitle = stringResource(R.string.debug_mode_sub),
            checked = settings.debugDump,
            onCheckedChange = { enabled -> onUpdate { it.copy(debugDump = enabled) } },
        )
    }
}

@Composable
private fun AutomationToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = title },
        )
    }
}

// ── Filters ──────────────────────────────────────────────────────────────

private data class FilterChipSpec(
    @StringRes val labelRes: Int,
    val getter: (SparkSettings) -> Boolean,
    val setter: (SparkSettings, Boolean) -> SparkSettings,
)

private val ORDER_TYPE_CHIP_SPECS = listOf(
    FilterChipSpec(R.string.shop_and_deliver, { it.shopAndDeliver }) { s, v -> s.copy(shopAndDeliver = v) },
    FilterChipSpec(R.string.shop_deliver_curbside, { it.shopDeliverCurbside }) { s, v -> s.copy(shopDeliverCurbside = v) },
    FilterChipSpec(R.string.curbside, { it.curbside }) { s, v -> s.copy(curbside = v) },
    FilterChipSpec(R.string.pharmacy, { it.pharmacy }) { s, v -> s.copy(pharmacy = v) },
    FilterChipSpec(R.string.dotcom, { it.dotcom }) { s, v -> s.copy(dotcom = v) },
    FilterChipSpec(R.string.customer_returns, { it.customerReturns }) { s, v -> s.copy(customerReturns = v) },
)

private val OFFER_TAG_CHIP_SPECS = listOf(
    FilterChipSpec(R.string.bulky_items, { it.bulkyItem }) { s, v -> s.copy(bulkyItem = v) },
    FilterChipSpec(R.string.shopper_bulk, { it.shopperBulk }) { s, v -> s.copy(shopperBulk = v) },
    FilterChipSpec(R.string.apartment, { it.apartment }) { s, v -> s.copy(apartment = v) },
    FilterChipSpec(R.string.customer_verification, { it.customerVerification }) { s, v -> s.copy(customerVerification = v) },
    FilterChipSpec(R.string.alcohol, { it.alcohol }) { s, v -> s.copy(alcohol = v) },
    FilterChipSpec(R.string.heavy, { it.heavy }) { s, v -> s.copy(heavy = v) },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterChipGroup(
    specs: List<FilterChipSpec>,
    settings: SparkSettings,
    onUpdate: ((SparkSettings) -> SparkSettings) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        specs.forEach { spec ->
            TypeChip(
                label = stringResource(spec.labelRes),
                selected = spec.getter(settings),
                onClick = {
                    onUpdate { current -> spec.setter(current, !spec.getter(current)) }
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FiltersCard(settings: SparkSettings, onUpdate: ((SparkSettings) -> SparkSettings) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var showResetConfirm by remember { mutableStateOf(false) }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.reset_filters_confirm_title)) },
            text = { Text(stringResource(R.string.reset_filters_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirm = false
                        onUpdate { current -> current.resetOfferFilters() }
                    },
                ) {
                    Text(stringResource(R.string.reset_filters_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    PremiumCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.filters_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { showResetConfirm = true }) {
                Text(stringResource(R.string.reset_filters))
            }
        }

        Spacer(Modifier.height(8.dp))

        SliderSetting(
            label = stringResource(R.string.dollars_per_mile),
            formatValue = { "$%.1f/mi".format(it) },
            hint = stringResource(R.string.dollars_per_mile_hint),
            value = settings.dollarsPerMile,
            valueRange = 0f..5f,
            steps = 49,
            onValueCommitted = { v -> onUpdate { it.copy(dollarsPerMile = v) } },
        )
        SliderSetting(
            label = stringResource(R.string.max_distance),
            formatValue = { "%.0f mi".format(it) },
            hint = null,
            value = settings.maxDistance,
            valueRange = 1f..30f,
            steps = 28,
            onValueCommitted = { v -> onUpdate { it.copy(maxDistance = v) } },
        )
        SliderSetting(
            label = stringResource(R.string.min_pay),
            formatValue = { "$%.0f".format(it) },
            hint = stringResource(R.string.min_pay_hint),
            value = settings.minPay,
            valueRange = 0f..50f,
            steps = 49,
            onValueCommitted = { v -> onUpdate { it.copy(minPay = v) } },
        )

        Stepper(
            label = stringResource(R.string.max_dropoffs),
            value = settings.numDropoffs,
            min = 1,
            max = 5,
            onValueChange = { v -> onUpdate { it.copy(numDropoffs = v) } },
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = scheme.outline)
        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.section_order_types),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.order_types_hint),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        FilterChipGroup(ORDER_TYPE_CHIP_SPECS, settings, onUpdate)

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = scheme.outline)
        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.section_offer_tags),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.offer_tags_hint),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        FilterChipGroup(OFFER_TAG_CHIP_SPECS, settings, onUpdate)
    }
}

@Composable
private fun SliderSetting(
    label: String,
    formatValue: (Float) -> String,
    hint: String?,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueCommitted: (Float) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var localValue by remember(value) { mutableFloatStateOf(value) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
            ValuePill(formatValue(localValue))
        }
        Slider(
            value = localValue,
            onValueChange = { localValue = it },
            onValueChangeFinished = { onValueCommitted(localValue) },
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = label },
        )
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ValuePill(text: String) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = CircleShape, color = scheme.primaryContainer) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun Stepper(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onValueChange: (Int) -> Unit,
    step: Int = 1,
    decreaseDescription: String = stringResource(R.string.decrease_dropoffs),
    increaseDescription: String = stringResource(R.string.increase_dropoffs),
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StepperButton(
                symbol = "–",
                enabled = value > min,
                contentDescription = decreaseDescription,
            ) { onValueChange((value - step).coerceAtLeast(min)) }
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = scheme.onSurface,
            )
            StepperButton(
                symbol = "+",
                enabled = value < max,
                contentDescription = increaseDescription,
            ) { onValueChange((value + step).coerceAtMost(max)) }
        }
    }
}

@Composable
private fun StepperButton(
    symbol: String,
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = if (enabled) scheme.primaryContainer else scheme.surfaceVariant,
        modifier = Modifier
            .size(36.dp)
            .semantics { this.contentDescription = contentDescription },
        onClick = onClick,
        enabled = enabled,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = symbol,
                style = MaterialTheme.typography.titleLarge,
                color = if (enabled) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = CircleShape,
        modifier = Modifier.semantics { contentDescription = label },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

// ── Offer history ─────────────────────────────────────────────────────

@Composable
private fun OfferHistoryCard(records: List<OfferRecord>) {
    val scheme = MaterialTheme.colorScheme
    PremiumCard {
        if (records.isEmpty()) {
            Text(
                text = stringResource(R.string.no_offers_yet),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            return@PremiumCard
        }
        records.forEachIndexed { index, record ->
            key(record.fingerprint, record.timestampMs) {
                OfferRecordRow(record)
            }
            if (index != records.lastIndex) {
                HorizontalDivider(
                    color = scheme.outline.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun OfferRecordRow(record: OfferRecord) {
    val scheme = MaterialTheme.colorScheme
    val order = record.order
    val evaluation = record.evaluation

    val (outcomeLabel, outcomeColor, whyLabel) = when (record.outcome) {
        OfferOutcome.ACCEPTED -> Triple(
            stringResource(R.string.outcome_accepted),
            scheme.secondary,
            stringResource(R.string.why_accepted),
        )
        OfferOutcome.DECLINED -> Triple(
            stringResource(R.string.outcome_declined),
            scheme.error,
            stringResource(R.string.why_declined),
        )
        OfferOutcome.SKIPPED_ACCEPT_OFF -> Triple(
            stringResource(R.string.outcome_skipped_accept),
            scheme.tertiary,
            stringResource(R.string.why_skipped_accept),
        )
        OfferOutcome.SKIPPED_DECLINE_OFF -> Triple(
            stringResource(R.string.outcome_skipped_decline),
            scheme.primary,
            stringResource(R.string.why_skipped_decline),
        )
    }

    val perMile = evaluation.dollarsPerMile?.let { "$%.2f/mi".format(it) }
        ?: stringResource(R.string.log_distance_unknown)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(8.dp), color = outcomeColor.copy(alpha = 0.15f)) {
                Text(
                    text = outcomeLabel,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = outcomeColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = OfferHistory.formatTime(record.timestampMs),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
        }

        val dropoffsLabel = pluralStringResource(R.plurals.offer_dropoffs_count, order.dropoffs, order.dropoffs)
        Text(
            text = "$${"%.2f".format(order.price)} · ${order.distance?.let { "%.1f mi".format(it) } ?: "?"} · $perMile · $dropoffsLabel",
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurface,
        )

        val richParts = buildList {
            evaluation.dollarsPerHour?.let { add("$%.0f/hr".format(it)) }
            evaluation.tip?.let { add("tip $%.2f".format(it)) }
            order.estimatedMinutes?.let { add("$it min") }
            order.itemCount?.let { add("$it items") }
        }
        if (richParts.isNotEmpty()) {
            Text(
                text = richParts.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }

        if (evaluation.detectedTypes.isNotEmpty()) {
            FlagRow(
                label = stringResource(R.string.offer_types),
                flags = evaluation.detectedTypes,
            )
        }
        if (evaluation.detectedTags.isNotEmpty()) {
            FlagRow(
                label = stringResource(R.string.offer_tags),
                flags = evaluation.detectedTags,
            )
        }

        Text(
            text = whyLabel,
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )

        val criteriaToShow = when (record.outcome) {
            OfferOutcome.DECLINED, OfferOutcome.SKIPPED_DECLINE_OFF ->
                evaluation.criteria.filter { !it.passed }
            else -> evaluation.criteria
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            criteriaToShow.forEach { criterion ->
                CriterionRow(criterion)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlagRow(label: String, flags: List<FlagLabel>) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            flags.forEach { flag ->
                val color = if (flag.allowed) scheme.secondaryContainer else scheme.errorContainer
                val textColor = if (flag.allowed) scheme.onSecondaryContainer else scheme.onErrorContainer
                Surface(shape = RoundedCornerShape(8.dp), color = color) {
                    Text(
                        text = stringResource(flag.nameRes),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = textColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun CriterionRow(criterion: CriterionResult) {
    val scheme = MaterialTheme.colorScheme
    val passed = criterion.passed
    val dotColor = if (passed) scheme.secondary else scheme.error

    val title = when (criterion.id) {
        CriterionId.MIN_PAY -> stringResource(R.string.criterion_min_pay)
        CriterionId.MAX_DISTANCE -> stringResource(R.string.criterion_max_distance)
        CriterionId.DOLLARS_PER_MILE -> stringResource(R.string.criterion_dollars_per_mile)
        CriterionId.DOLLARS_PER_HOUR -> stringResource(R.string.criterion_dollars_per_hour)
        CriterionId.MIN_BASE_PAY -> stringResource(R.string.criterion_min_base_pay)
        CriterionId.MAX_TIP_RATIO -> stringResource(R.string.criterion_max_tip_ratio)
        CriterionId.MAX_DEADHEAD -> stringResource(R.string.criterion_max_deadhead)
        CriterionId.MAX_ITEMS -> stringResource(R.string.criterion_max_items)
        CriterionId.MAX_DROPOFFS -> stringResource(R.string.criterion_max_dropoffs)
        CriterionId.STORE_LOCATION -> stringResource(R.string.criterion_store)
        CriterionId.ORDER_TYPES -> stringResource(R.string.criterion_order_types)
        CriterionId.OFFER_TAGS -> stringResource(R.string.criterion_offer_tags)
    }

    val detail = when (criterion.id) {
        CriterionId.ORDER_TYPES -> if (passed) {
            stringResource(R.string.criterion_types_ok)
        } else {
            stringResource(R.string.criterion_types_fail)
        }
        CriterionId.OFFER_TAGS -> if (passed) {
            stringResource(R.string.criterion_tags_ok)
        } else {
            stringResource(R.string.criterion_tags_fail)
        }
        else -> {
            val base = stringResource(
                R.string.criterion_value_format,
                criterion.actual,
                criterion.required,
            )
            if (criterion.note == CriterionResult.NOTE_UNKNOWN) {
                "$base (${stringResource(R.string.criterion_unknown_skipped)})"
            } else {
                base
            }
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

// ── Shared building blocks ───────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .padding(start = 4.dp)
            .semantics { heading() },
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(SparkBlueGlow, SparkBlue),
                    ),
                ),
        )
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
            letterSpacing = 1.4.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * A section whose body can be collapsed behind a tappable header. Keeps the
 * Settings tab calm on first open — advanced controls stay one tap away.
 */
@Composable
private fun ExpandableSection(
    title: String,
    initiallyExpanded: Boolean = false,
    summary: String? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "chevron",
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(title)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(end = 8.dp),
            ) {
                if (summary != null && !expanded) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.primary,
                        maxLines = 1,
                    )
                }
                Text(
                    text = "⌄",
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.rotate(chevronRotation),
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(content = content)
        }
    }
}

@Composable
private fun PremiumCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 22.dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        scheme.surfaceVariant.copy(alpha = 0.55f),
                        scheme.surface.copy(alpha = 0.92f),
                    ),
                ),
            )
            // Hairline top-lit edge — the signature glass highlight.
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.14f),
                        Color.White.copy(alpha = 0.02f),
                    ),
                ),
                shape = shape,
            )
            .padding(contentPadding),
        content = content,
    )
}

@Composable
private fun PulsingDot(color: Color, pulsing: Boolean, size: Dp = 8.dp) {
    val alpha = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "pulse")
        val pulseAlpha by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.25f,
            animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
            label = "alpha",
        )
        pulseAlpha
    } else {
        1f
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha)),
    )
}
