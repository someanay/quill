package com.tihor.quill.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tihor.quill.accessibility.AccessibilityUtil
import com.tihor.quill.ui.app.QuillNav
import com.tihor.quill.ui.components.Banner
import com.tihor.quill.ui.components.IllustrationVariant
import com.tihor.quill.ui.components.PermissionIllustration
import com.tihor.quill.ui.components.PillKind
import com.tihor.quill.ui.components.QuillGhostButton
import com.tihor.quill.ui.components.QuillListContainer
import com.tihor.quill.ui.components.QuillPrimaryButton
import com.tihor.quill.ui.components.QuillRow
import com.tihor.quill.ui.components.QuillTopBar
import com.tihor.quill.ui.components.QuillTopBarTitle
import com.tihor.quill.ui.theme.quill

/** F1 — accessibility revoked recovery. Reached when the OS turns the service off to save battery. */
@Composable
fun PermissionRevokedScreen(nav: QuillNav) {
    val context = LocalContext.current
    Scaffold(
        topBar = { QuillTopBar(title = { QuillTopBarTitle("Quill") }, onBack = nav::back) },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Banner(
                title = "Quill stopped typing into apps",
                text = "Your phone switched off Accessibility access — Quill can't insert text until it's back on.",
                icon = Icons.Rounded.WarningAmber, kind = PillKind.ERROR,
            )
            Spacer(Modifier.height(20.dp))
            PermissionIllustration(Icons.Rounded.AccessibilityNew, variant = IllustrationVariant.ERROR)
            Spacer(Modifier.height(18.dp))
            Text("Let's switch it back on", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Some phones turn this off to save battery. Turning it back on takes two taps.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.quill.muted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            QuillPrimaryButton("Re-enable Accessibility", onClick = {
                context.startActivity(AccessibilityUtil.settingsIntent())
            })
            Spacer(Modifier.height(10.dp))
            QuillGhostButton("Why did this happen?", onClick = { /* OEM battery-manager explainer */ })
        }
    }
}

/** F2 — opt-in crash-report review. Shows the exact scrubbed payload before anything is shared. */
@Composable
fun CrashReportScreen(nav: QuillNav) {
    val context = LocalContext.current
    var alwaysSend by rememberSaveable { mutableStateOf(false) } // TODO(M11): persist opt-in

    Scaffold(
        topBar = { QuillTopBar(title = { QuillTopBarTitle("Send crash report?") }, onBack = nav::back) },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Text(
                "Quill closed unexpectedly last time. You can send a report to help fix it — here's exactly what it contains:",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.quill.muted,
            )
            Spacer(Modifier.height(12.dp))
            CodeBlock(
                included = listOf(
                    "error: IllegalStateException",
                    "screen: OverlayService.onStart",
                    "app: 1.0.0 (build 1)",
                    "device: Honor Magic6 Pro · A14",
                ),
                excluded = listOf("no transcript text", "no audio", "no clipboard", "no API key"),
            )
            Spacer(Modifier.height(12.dp))
            Banner(
                text = "Sent through your own share sheet — you choose the app, nothing auto-uploads.",
                icon = Icons.Rounded.Shield, kind = PillKind.OK,
            )
            Spacer(Modifier.height(12.dp))
            QuillListContainer {
                QuillRow(title = "Always send reports automatically", trailing = {
                    Switch(checked = alwaysSend, onCheckedChange = { alwaysSend = it })
                })
            }
            Spacer(Modifier.height(20.dp))
            QuillPrimaryButton("Send via…", onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Quill crash report\nerror: IllegalStateException\nscreen: OverlayService.onStart")
                }
                context.startActivity(Intent.createChooser(send, null))
            }, leadingIcon = Icons.Rounded.Share)
            Spacer(Modifier.height(10.dp))
            QuillGhostButton("Don't send", onClick = nav::back)
        }
    }
}

@Composable
private fun CodeBlock(included: List<String>, excluded: List<String>) {
    val q = MaterialTheme.quill
    Column(
        Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        included.forEach { CodeLine("✓", q.ok, it) }
        Spacer(Modifier.height(4.dp))
        excluded.forEach { CodeLine("✕", MaterialTheme.colorScheme.error, it) }
    }
}

@Composable
private fun CodeLine(mark: String, markColor: androidx.compose.ui.graphics.Color, text: String) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(mark, color = markColor, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace))
        Text(
            text,
            color = MaterialTheme.quill.text2,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
        )
    }
}
