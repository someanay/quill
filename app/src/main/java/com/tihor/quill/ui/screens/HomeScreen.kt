package com.tihor.quill.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.tihor.quill.accessibility.AccessibilityUtil
import com.tihor.quill.copy.CopyKeys as K
import com.tihor.quill.copy.copy
import com.tihor.quill.overlay.OverlayPermission
import com.tihor.quill.overlay.OverlayService
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

    var hasOverlay by remember { mutableStateOf(OverlayPermission.isGranted(context)) }
    var bubbleOn by remember { mutableStateOf(OverlayPermission.isGranted(context)) }
    var a11yOn by remember { mutableStateOf(AccessibilityUtil.isServiceEnabled(context)) }

    // Re-check grants whenever we resume (the OS may revoke accessibility to save battery).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlay = OverlayPermission.isGranted(context)
                a11yOn = AccessibilityUtil.isServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        hasOverlay = OverlayPermission.isGranted(context)
        if (hasOverlay) { OverlayService.start(context); bubbleOn = true }
    }

    fun setBubble(on: Boolean) {
        if (on) {
            if (!hasOverlay) { overlayLauncher.launch(OverlayPermission.settingsIntent(context)) } else {
                OverlayService.start(context); bubbleOn = true
            }
        } else {
            OverlayService.stop(context); bubbleOn = false
        }
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
            if (!a11yOn) {
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
