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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tihor.quill.accessibility.AccessibilityUtil
import com.tihor.quill.copy.CopyKeys as K
import com.tihor.quill.copy.copy
import com.tihor.quill.ui.app.QuillNav
import com.tihor.quill.ui.components.Banner
import com.tihor.quill.ui.components.IllustrationVariant
import com.tihor.quill.ui.components.PermissionIllustration
import com.tihor.quill.ui.components.PillKind
import com.tihor.quill.ui.components.QuillChip
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
        topBar = { QuillTopBar(title = { QuillTopBarTitle("Quill") }, onBack = nav::back) }, // copy-exempt
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Banner(
                title = copy(K.SYSTEM_RECOVERY_TITLE),
                text = copy(K.SYSTEM_RECOVERY_BANNER_TEXT),
                icon = Icons.Rounded.WarningAmber, kind = PillKind.ERROR,
            )
            Spacer(Modifier.height(20.dp))
            PermissionIllustration(Icons.Rounded.AccessibilityNew, variant = IllustrationVariant.ERROR)
            Spacer(Modifier.height(18.dp))
            Text(copy(K.SYSTEM_RECOVERY_HEADING), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                copy(K.SYSTEM_RECOVERY_BODY),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.quill.muted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            QuillPrimaryButton(copy(K.SYSTEM_RECOVERY_CTA), onClick = {
                context.startActivity(AccessibilityUtil.settingsIntent())
            })
            Spacer(Modifier.height(10.dp))
            // The OEM battery-manager explainer isn't written yet — disabled rather than a dead button.
            QuillGhostButton(copy(K.SYSTEM_RECOVERY_WHY), onClick = {}, enabled = false)
        }
    }
}

/** F2 — opt-in crash-report review. Shows the exact scrubbed payload before anything is shared. */
@Composable
fun CrashReportScreen(nav: QuillNav) {
    val context = LocalContext.current

    Scaffold(
        topBar = { QuillTopBar(title = { QuillTopBarTitle(copy(K.SYSTEM_CRASH_TITLE)) }, onBack = nav::back) },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Text(
                copy(K.SYSTEM_CRASH_BODY),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.quill.muted,
            )
            Spacer(Modifier.height(12.dp))
            CodeBlock(
                included = listOf(
                    "error: IllegalStateException", // copy-exempt
                    "screen: OverlayService.onStart", // copy-exempt
                    "app: 1.0.0 (build 1)", // copy-exempt
                    "device: Honor Magic6 Pro · A14", // copy-exempt
                ),
                excluded = listOf("no transcript text", "no audio", "no clipboard", "no API key"), // copy-exempt
            )
            Spacer(Modifier.height(12.dp))
            Banner(
                text = copy(K.SYSTEM_CRASH_BANNER),
                icon = Icons.Rounded.Shield, kind = PillKind.OK,
            )
            Spacer(Modifier.height(12.dp))
            QuillListContainer {
                // "Always send" persists in M11 (with the crash reporter); honest placeholder until then.
                QuillRow(title = copy(K.SYSTEM_CRASH_ALWAYS), titleColor = MaterialTheme.quill.muted, trailing = {
                    QuillChip(copy(K.COMMON_COMING_SOON), selected = false, onClick = {}, enabled = false)
                })
            }
            Spacer(Modifier.height(20.dp))
            QuillPrimaryButton(copy(K.SYSTEM_CRASH_SEND), onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Quill crash report\nerror: IllegalStateException\nscreen: OverlayService.onStart")
                }
                context.startActivity(Intent.createChooser(send, null))
            }, leadingIcon = Icons.Rounded.Share)
            Spacer(Modifier.height(10.dp))
            QuillGhostButton(copy(K.SYSTEM_CRASH_DONT_SEND), onClick = nav::back)
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
