package com.tihor.quill.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import com.tihor.quill.accessibility.AccessibilityUtil
import com.tihor.quill.copy.CopyKeys as K
import com.tihor.quill.copy.copy
import com.tihor.quill.overlay.OverlayPermission
import com.tihor.quill.overlay.OverlayService
import com.tihor.quill.reliability.BatteryOptimization
import com.tihor.quill.reliability.ReliabilityPolicy
import com.tihor.quill.settings.QuillSettings
import com.tihor.quill.settings.SettingsRepository
import kotlinx.coroutines.launch
import com.tihor.quill.ui.app.QuillBottomNav
import com.tihor.quill.ui.app.QuillNav
import com.tihor.quill.ui.app.Screen
import com.tihor.quill.ui.components.Banner
import com.tihor.quill.ui.components.BubbleState
import com.tihor.quill.ui.components.LeadIconVariant
import com.tihor.quill.ui.components.PillKind
import com.tihor.quill.ui.components.QuillBubble
import com.tihor.quill.ui.components.QuillCard
import com.tihor.quill.ui.components.QuillListContainer
import com.tihor.quill.ui.components.QuillRow
import com.tihor.quill.ui.components.QuillTopBar
import com.tihor.quill.ui.components.RowChevron
import com.tihor.quill.ui.components.RowDivider
import com.tihor.quill.ui.components.SectionHeader
import com.tihor.quill.ui.components.Wordmark
import com.tihor.quill.ui.theme.quill

/** Home / control center (board B). Master toggle + glanceable stats; the work happens in the bubble. */
@Composable
fun HomeScreen(nav: QuillNav) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()
    val settings by repo.settings.collectAsState(initial = QuillSettings())

    var hasOverlay by remember { mutableStateOf(OverlayPermission.isGranted(context)) }
    // The master toggle reflects the unified visibility state (single source of truth): "on" = bubble
    // shown. Hiding (toggle-off or drag-dismiss) keeps the service + notification alive; only the
    // notification's "Dismiss bubble" fully stops it. Gated by the overlay grant (no bubble without it).
    val bubbleOn = settings.bubbleVisible && hasOverlay
    var a11yOn by remember { mutableStateOf(AccessibilityUtil.isServiceEnabled(context)) }
    var batteryExempt by remember { mutableStateOf(BatteryOptimization.isExempt(context)) }

    // Re-check grants whenever we resume (the OS may revoke accessibility / battery state may change).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlay = OverlayPermission.isGranted(context)
                a11yOn = AccessibilityUtil.isServiceEnabled(context)
                batteryExempt = BatteryOptimization.isExempt(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // POST_NOTIFICATIONS (Android 13+) gates the FGS notification + its "Show Quill" action — the
    // notification summon path. Requested at first bubble-start; if denied, the synced Home toggle is
    // the alternate way back, so denial isn't a dead end. Proceed regardless of the result.
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun requestNotifPermIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Toggle-on starts the service (if needed) and marks it desired + visible.
    fun startAndShow() {
        requestNotifPermIfNeeded()
        OverlayService.start(context)
        scope.launch { repo.setBubbleActive(true); repo.setBubbleVisible(true) }
    }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        hasOverlay = OverlayPermission.isGranted(context)
        if (hasOverlay) startAndShow()
    }

    // The toggle means "bubble shown vs hidden", NOT "service alive vs dead". Toggle-off HIDES (writes
    // the unified visibility state) but keeps the FGS + notification running, so the summon paths work
    // identically whether the user hid via the toggle, a drag, or returns via the notification. Only the
    // notification's "Dismiss bubble" fully stops the service (clearing bubbleActive).
    fun setBubble(on: Boolean) {
        if (on) {
            if (!hasOverlay) overlayLauncher.launch(OverlayPermission.settingsIntent(context)) else startAndShow()
        } else {
            scope.launch { repo.setBubbleVisible(false) }
        }
    }

    // M11/M12: bounded battery re-prompt — only when ungranted AND the bubble was killed at least once.
    // Deep-links to the battery-optimisation settings list (the user allows Quill there — no restricted
    // one-tap dialog). Consuming the flag stops it nagging on every launch.
    fun promptBattery() {
        runCatching { context.startActivity(BatteryOptimization.settingsIntent()) }
        scope.launch { repo.setBubbleWasKilled(false) }
    }

    Scaffold(
        topBar = {
            QuillTopBar(
                title = { Wordmark() },
                actions = {
                    IconButton(onClick = { nav.switchTab(Screen.Settings) }) {
                        Icon(Icons.Rounded.Settings, contentDescription = copy(K.A11Y_SETTINGS))
                    }
                },
            )
        },
        bottomBar = { QuillBottomNav(Screen.Home, nav::switchTab) },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // Revocation recovery: only nag if the user previously enabled the service (it's now off).
            if (ReliabilityPolicy.shouldPromptRevocation(settings.onboardingComplete, a11yOn)) {
                Banner(
                    title = copy(K.SYSTEM_RECOVERY_TITLE),
                    text = copy(K.HOME_BANNER_A11Y_OFF_TEXT),
                    icon = Icons.Rounded.WarningAmber,
                    kind = PillKind.ERROR,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable { nav.navigate(Screen.Recovery) },
                )
                Spacer(Modifier.padding(top = 6.dp))
            }
            // Battery re-prompt: bounded — only after an actual kill while still unexempted (M11).
            if (ReliabilityPolicy.shouldRepromptBattery(batteryExempt, settings.bubbleWasKilled)) {
                Banner(
                    title = copy(K.HOME_BATTERY_TITLE),
                    text = copy(K.HOME_BATTERY_TEXT),
                    icon = Icons.Rounded.BatteryAlert,
                    kind = PillKind.ERROR,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable { promptBattery() },
                )
                Spacer(Modifier.padding(top = 6.dp))
            }
            MasterToggleCard(on = bubbleOn, onToggle = ::setBubble)

            if (bubbleOn) {
                SectionHeader(copy(K.HOME_GROUP_TODAY))
                QuillListContainer {
                    QuillRow(
                        leadIcon = Icons.Rounded.GraphicEq,
                        leadVariant = LeadIconVariant.GOLD,
                        title = copy(K.HOME_TODAY_DICTATIONS),
                        subtitle = copy(K.HOME_TODAY_DICTATIONS_SUB),
                    )
                    RowDivider()
                    QuillRow(
                        leadIcon = Icons.Rounded.Schedule,
                        leadVariant = LeadIconVariant.PLAIN,
                        title = copy(K.HOME_TODAY_LAST_USED),
                        subtitle = copy(K.HOME_TODAY_LAST_USED_SUB),
                        onClick = { nav.switchTab(Screen.History) },
                        trailing = { RowChevron() },
                    )
                }

                SectionHeader(copy(K.HOME_GROUP_TIP))
                Banner(
                    title = copy(K.HOME_TIP_TITLE),
                    text = copy(K.HOME_TIP_TEXT),
                    icon = Icons.Rounded.Info,
                    kind = PillKind.INFO,
                )
            } else {
                Spacer(Modifier.padding(top = 14.dp))
                Banner(
                    title = copy(K.HOME_PAUSED_BANNER_TITLE),
                    text = copy(K.HOME_PAUSED_BANNER_TEXT),
                    icon = Icons.Rounded.Info,
                    kind = PillKind.LIVE,
                )
                Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Bottom) {
                    Text(
                        copy(K.HOME_PAUSED_FOOTER),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.quill.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    )
                }
            }
            Spacer(Modifier.padding(bottom = 16.dp))
        }
    }
}

@Composable
private fun MasterToggleCard(on: Boolean, onToggle: (Boolean) -> Unit) {
    val q = MaterialTheme.quill
    QuillCard(
        border = if (on) q.brandSoft else null,
        modifier = Modifier.padding(top = 4.dp),
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            QuillBubble(
                state = if (on) BubbleState.IDLE else BubbleState.PAUSED,
                icon = if (on) Icons.Rounded.Mic else Icons.Rounded.PowerSettingsNew,
                size = 48.dp,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    if (on) copy(K.HOME_TOGGLE_ON_TITLE) else copy(K.HOME_TOGGLE_OFF_TITLE),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (on) copy(K.HOME_TOGGLE_ON_SUB) else copy(K.HOME_TOGGLE_OFF_SUB),
                    style = MaterialTheme.typography.labelSmall,
                    color = q.muted,
                )
            }
            Switch(checked = on, onCheckedChange = onToggle)
        }
    }
}
