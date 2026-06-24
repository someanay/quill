package com.tihor.quill.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Create
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tihor.quill.accessibility.AccessibilityUtil
import com.tihor.quill.secrets.KeystoreSecretStore
import com.tihor.quill.secrets.SecretStore
import com.tihor.quill.settings.QuillSettings
import com.tihor.quill.settings.SettingsRepository
import com.tihor.quill.ui.app.QuillBottomNav
import com.tihor.quill.ui.app.QuillNav
import com.tihor.quill.ui.app.Screen
import com.tihor.quill.ui.components.Banner
import com.tihor.quill.ui.components.BubbleState
import com.tihor.quill.ui.components.FieldLabel
import com.tihor.quill.ui.components.HelpText
import com.tihor.quill.ui.components.LeadIconVariant
import com.tihor.quill.ui.components.PillKind
import com.tihor.quill.ui.components.QuillBubble
import com.tihor.quill.ui.components.QuillCard
import com.tihor.quill.ui.components.QuillChip
import com.tihor.quill.ui.components.QuillListContainer
import com.tihor.quill.ui.components.QuillRow
import com.tihor.quill.ui.components.QuillTopBar
import com.tihor.quill.ui.components.QuillTopBarTitle
import com.tihor.quill.ui.components.RowChevron
import com.tihor.quill.ui.components.RowDivider
import com.tihor.quill.ui.components.RowValueChevron
import com.tihor.quill.ui.components.SectionHeader
import com.tihor.quill.ui.components.StatusPill
import com.tihor.quill.ui.theme.quill
import kotlinx.coroutines.launch

// ============================================================================
// Shared detail scaffold (back chevron + scrollable body), and a switch row.
// ============================================================================

@Composable
private fun DetailScaffold(
    title: String,
    nav: QuillNav,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = { QuillTopBar(title = { QuillTopBarTitle(title) }, onBack = nav::back, actions = actions) },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            content = content,
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    QuillRow(title = title, subtitle = subtitle, trailing = {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    })
}

// ============================================================================
// C1 — Settings index
// ============================================================================

@Composable
fun SettingsScreen(nav: QuillNav) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val secretStore = remember { KeystoreSecretStore(context) }
    val settings by repo.settings.collectAsState(initial = QuillSettings())

    val keyCount = listOf(SecretStore.ALIAS_STT_KEY, SecretStore.ALIAS_LLM_KEY)
        .count { secretStore.contains(it) }

    Scaffold(
        topBar = { QuillTopBar(title = { QuillTopBarTitle("Settings") }) },
        bottomBar = { QuillBottomNav(Screen.Settings, nav::switchTab) },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            SectionHeader("Dictation")
            QuillListContainer {
                QuillRow(
                    leadIcon = Icons.Rounded.GraphicEq, title = "Speech & model",
                    onClick = { nav.navigate(Screen.SpeechModel) },
                    trailing = { RowValueChevron("${settings.provider.displayName} · ${shortModel(settings.sttModel)}") },
                )
                RowDivider()
                QuillRow(
                    leadIcon = Icons.Rounded.Key, title = "API keys",
                    onClick = { nav.navigate(Screen.ApiKeys) },
                    trailing = { RowValueChevron(if (keyCount == 0) "None" else "$keyCount set") },
                )
                RowDivider()
                QuillRow(
                    leadIcon = Icons.Rounded.AutoAwesome, title = "Clean up with AI",
                    onClick = { nav.navigate(Screen.AiCleanup) },
                    trailing = { RowValueChevron(if (settings.cleanupEnabled) "On" else "Off") },
                )
                RowDivider()
                QuillRow(
                    leadIcon = Icons.Rounded.Create, title = "Custom words",
                    onClick = { nav.navigate(Screen.CustomWords) },
                    trailing = { RowValueChevron("0") },
                )
            }

            SectionHeader("Capture & bubble")
            QuillListContainer {
                QuillRow(
                    leadIcon = Icons.Rounded.Mic, leadVariant = LeadIconVariant.GOLD,
                    title = "Audio & recording",
                    onClick = { nav.navigate(Screen.AudioRecording) },
                    trailing = { RowChevron() },
                )
                RowDivider()
                QuillRow(
                    leadIcon = Icons.Rounded.PictureInPicture, title = "Bubble appearance",
                    onClick = { nav.navigate(Screen.BubbleAppearance) },
                    trailing = { RowChevron() },
                )
            }

            SectionHeader("Privacy & data")
            QuillListContainer {
                QuillRow(
                    leadIcon = Icons.Rounded.Shield, title = "Privacy",
                    onClick = { nav.navigate(Screen.Privacy) },
                    trailing = { RowChevron() },
                )
                RowDivider()
                QuillRow(
                    leadIcon = Icons.Rounded.Layers, leadVariant = LeadIconVariant.PLAIN,
                    title = "Advanced",
                    onClick = { nav.navigate(Screen.Advanced) },
                    trailing = { RowChevron() },
                )
                RowDivider()
                QuillRow(
                    leadIcon = Icons.Rounded.Info, leadVariant = LeadIconVariant.PLAIN,
                    title = "About & legal",
                    onClick = { nav.navigate(Screen.About) },
                    trailing = { RowChevron() },
                )
            }
        }
    }
}

private fun shortModel(model: String): String = when {
    model.contains("turbo") -> "turbo"
    model.isBlank() -> "default"
    else -> model.substringAfterLast('-')
}

// ============================================================================
// C2 — Speech & model
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeechModelScreen(nav: QuillNav) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()
    val settings by repo.settings.collectAsState(initial = QuillSettings())

    DetailScaffold("Speech & model", nav) {
        FieldLabel("Provider", Modifier.padding(top = 8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Only Groq ships in v1; the rest are shown disabled so the roadmap is legible.
            QuillChip("Groq", selected = true, onClick = {})
            QuillChip("OpenAI", selected = false, onClick = {}, enabled = false)
            QuillChip("Deepgram", selected = false, onClick = {}, enabled = false)
            QuillChip("Custom", selected = false, onClick = {}, enabled = false)
        }

        FieldLabel("Model", Modifier.padding(top = 16.dp))
        QuillListContainer {
            ModelRow(
                "whisper-large-v3-turbo", "Fastest · recommended",
                selected = settings.sttModel.contains("turbo"),
                onClick = { scope.launch { repo.setSttModel("whisper-large-v3-turbo") } },
            )
            RowDivider()
            ModelRow(
                "whisper-large-v3", "Most accurate",
                selected = settings.sttModel == "whisper-large-v3",
                onClick = { scope.launch { repo.setSttModel("whisper-large-v3") } },
            )
        }

        FieldLabel("Spoken language", Modifier.padding(top = 16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QuillChip(
                "English (Roman)",
                selected = settings.languageHint == "en",
                onClick = { scope.launch { repo.setLanguageHint("en") } },
            )
            QuillChip(
                "Auto-detect",
                selected = settings.languageHint == QuillSettings.LANGUAGE_AUTO,
                onClick = { scope.launch { repo.setLanguageHint(QuillSettings.LANGUAGE_AUTO) } },
            )
            QuillChip(
                "Hindi (Devanagari)",
                selected = settings.languageHint == "hi",
                onClick = { scope.launch { repo.setLanguageHint("hi") } },
            )
        }
        HelpText(
            "English keeps mixed Hindi-English speech in Roman script (\"aaj ka\"). " +
                "Auto-detect or Hindi give native script.",
        )
    }
}

@Composable
private fun ModelRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    QuillRow(title = title, subtitle = subtitle, onClick = onClick, trailing = {
        if (selected) {
            Icon(
                Icons.Rounded.Check, contentDescription = "Selected",
                tint = MaterialTheme.quill.brandInk, modifier = Modifier.size(20.dp),
            )
        }
    })
}

// ============================================================================
// C3 — API keys
// ============================================================================

@Composable
fun ApiKeysScreen(nav: QuillNav) {
    val context = LocalContext.current
    val secretStore = remember { KeystoreSecretStore(context) }
    var sttKey by rememberSaveable { mutableStateOf(secretStore.get(SecretStore.ALIAS_STT_KEY).orEmpty()) }

    DetailScaffold("API keys", nav) {
        if (sttKey.isBlank()) {
            // Empty state (C3c) — zero keys, just the invitation to add one.
            Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    com.tihor.quill.ui.components.PermissionIllustration(Icons.Rounded.Key)
                    Spacer(Modifier.height(20.dp))
                    Text("No keys yet", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Quill needs a speech-to-text key to transcribe. Add one to start dictating again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.quill.text2,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    com.tihor.quill.ui.components.QuillPrimaryButton(
                        "Add a key", onClick = { nav.navigate(Screen.SpeechModel) },
                        leadingIcon = Icons.Rounded.Key,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Banner(
                text = "Keys are encrypted in this device's keystore and never leave it or appear in logs.",
                icon = Icons.Rounded.Lock, kind = PillKind.OK,
            )
            SectionHeader("Your keys")
            KeyCard(
                role = "Speech-to-text", provider = "Groq",
                masked = mask(sttKey), roleIcon = Icons.Rounded.GraphicEq,
                onRemove = { secretStore.clear(SecretStore.ALIAS_STT_KEY); sttKey = "" },
            )
            Spacer(Modifier.height(14.dp))
            com.tihor.quill.ui.components.QuillTonalButton(
                "Add a key", onClick = { /* TODO(M7+): multi-key entry */ },
                leadingIcon = Icons.Rounded.Key,
            )
            HelpText("Your cleanup model reuses this key unless you add a separate one.")
        }
    }
}

@Composable
private fun KeyCard(
    role: String,
    provider: String,
    masked: String,
    roleIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onRemove: () -> Unit,
) {
    QuillCard {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                com.tihor.quill.ui.components.LeadIcon(roleIcon)
                Column(Modifier.weight(1f)) {
                    Text(role, style = MaterialTheme.typography.titleMedium)
                    Text(provider, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.quill.muted)
                }
                StatusPill("Valid", PillKind.OK)
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedKeyField(masked)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                LinkText("Replace") { }
                Dot()
                LinkText("Test again") { }
                Dot()
                LinkText("Remove", color = MaterialTheme.colorScheme.error, onClick = onRemove)
            }
        }
    }
}

@Composable
private fun OutlinedKeyField(masked: String) {
    val q = MaterialTheme.quill
    Row(
        Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.Key, contentDescription = null, tint = q.faint, modifier = Modifier.size(18.dp))
        Text(
            masked,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun LinkText(text: String, color: Color = MaterialTheme.quill.brandInk, onClick: () -> Unit) {
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = color,
        modifier = Modifier.clip(MaterialTheme.shapes.extraSmall).clickable(onClick = onClick),
    )
}

@Composable
private fun Dot() {
    Text("·", color = MaterialTheme.quill.faint, style = MaterialTheme.typography.bodyMedium)
}

private fun mask(key: String): String {
    val last4 = key.takeLast(4)
    val prefix = key.take(8).ifBlank { "key" }
    return "$prefix••••$last4"
}

// ============================================================================
// C4 — Clean up with AI
// ============================================================================

@Composable
fun AiCleanupScreen(nav: QuillNav) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()
    val settings by repo.settings.collectAsState(initial = QuillSettings())

    DetailScaffold("Clean up with AI", nav) {
        Spacer(Modifier.height(8.dp))
        QuillCard {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                com.tihor.quill.ui.components.LeadIcon(Icons.Rounded.AutoAwesome)
                Column(Modifier.weight(1f)) {
                    Text("Tidy my dictation", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Remove filler, fix punctuation",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.quill.muted,
                    )
                }
                Switch(
                    checked = settings.cleanupEnabled,
                    onCheckedChange = { scope.launch { repo.setCleanupEnabled(it) } },
                )
            }
        }

        SectionHeader("What it does")
        QuillCard {
            Column(Modifier.padding(16.dp)) {
                Text("Before", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.quill.faint)
                Text(
                    "\"um so like can you send me the uh the report by friday\"",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.quill.text2,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                )
                Text("After", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.quill.brandInk)
                Text(
                    "\"Can you send me the report by Friday?\"",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        QuillListContainer {
            QuillRow(title = "Cleanup model", trailing = { RowValueChevron("llama-3.3-70b") })
        }
        HelpText("If cleanup ever fails, your raw transcript is inserted instead — never blocked.")
    }
}

// ============================================================================
// C5 — Custom words (P1.5, stubbed)
// ============================================================================

@Composable
fun CustomWordsScreen(nav: QuillNav) {
    DetailScaffold(
        "Custom words", nav,
        actions = {
            androidx.compose.material3.IconButton(onClick = { /* TODO(P1.5) */ }) {
                Icon(
                    Icons.Rounded.Add, contentDescription = "Add a word",
                    tint = MaterialTheme.quill.brandInk,
                )
            }
        },
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Teach Quill names and terms it keeps mishearing. Each is matched and replaced after transcription.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.quill.muted,
            modifier = Modifier.padding(bottom = 14.dp),
        )
        // Placeholder until the substitution map ships (P1.5).
        QuillListContainer {
            QuillRow(title = "No custom words yet", subtitle = "Tap + to add a name or term", trailing = {})
        }
        Spacer(Modifier.height(14.dp))
        com.tihor.quill.ui.components.QuillTonalButton(
            "Add a word", onClick = { /* TODO(P1.5) */ },
            leadingIcon = Icons.Rounded.Add,
        )
    }
}

// ============================================================================
// C6 — Audio & recording
// ============================================================================

@Composable
fun AudioRecordingScreen(nav: QuillNav) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()
    val settings by repo.settings.collectAsState(initial = QuillSettings())
    var holdToTalk by rememberSaveable { mutableStateOf(false) } // TODO(M11): persist
    var stopOnSilence by rememberSaveable { mutableStateOf(true) } // TODO(M4): wire to recorder

    DetailScaffold("Audio & recording", nav) {
        FieldLabel("Start & stop", Modifier.padding(top = 8.dp))
        QuillListContainer {
            QuillRow(title = "Trigger", subtitle = "How recording starts", trailing = { RowValueChevron("Tap to toggle") })
            RowDivider()
            SwitchRow("Hold-to-talk", holdToTalk, { holdToTalk = it }, "Press & hold for quick snippets")
        }

        FieldLabel("Max recording length", Modifier.padding(top = 16.dp))
        QuillCard {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Auto-stops at", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.quill.muted)
                    Text(formatCap(settings.sessionCapSeconds), style = MaterialTheme.typography.titleMedium)
                }
                Slider(
                    value = settings.sessionCapSeconds.toFloat(),
                    onValueChange = { scope.launch { repo.setSessionCapSeconds(it.toInt()) } },
                    valueRange = 60f..300f,
                    steps = 3, // 60 / 120 / 180 / 240 / 300
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        QuillListContainer {
            SwitchRow("Stop on silence", stopOnSilence, { stopOnSilence = it }, "End after a quiet pause")
        }
        Spacer(Modifier.height(10.dp))
        Banner(
            text = "In very noisy places this may not trigger — the max-length limit always applies.",
            icon = Icons.Rounded.Info, kind = PillKind.LIVE,
        )

        Spacer(Modifier.height(16.dp))
        QuillListContainer {
            QuillRow(
                title = "Upload format", subtitle = "FLAC · smaller, lossless",
                trailing = { Text("Recommended", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.quill.faint) },
            )
        }
    }
}

private fun formatCap(seconds: Int): String = if (seconds < 60) "${seconds}s" else "${seconds / 60} min"

// ============================================================================
// C7 — Privacy
// ============================================================================

@Composable
fun PrivacyScreen(nav: QuillNav) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()
    val settings by repo.settings.collectAsState(initial = QuillSettings())
    var saveHistory by rememberSaveable { mutableStateOf(true) } // TODO(M9): wire to history store
    var crashReports by rememberSaveable { mutableStateOf(true) } // TODO(M11): wire to crash opt-in

    val micGranted = androidx.core.content.ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.RECORD_AUDIO,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val a11yOn = AccessibilityUtil.isServiceEnabled(context)

    DetailScaffold("Privacy", nav) {
        Spacer(Modifier.height(8.dp))
        Banner(
            title = "Nothing is collected by Quill.",
            text = "Audio goes only to your provider; everything else stays on this phone.",
            icon = Icons.Rounded.Shield, kind = PillKind.OK,
        )
        Spacer(Modifier.height(14.dp))
        QuillListContainer {
            SwitchRow(
                "Private mode", settings.privateMode,
                { scope.launch { repo.setPrivateMode(it) } }, "Don't save any transcripts",
            )
            RowDivider()
            SwitchRow("Save dictation history", saveHistory, { saveHistory = it }, "Keep the last 1,000 on device")
        }

        SectionHeader("Diagnostics")
        QuillListContainer {
            SwitchRow("Share crash reports", crashReports, { crashReports = it }, "Only if a crash happens — you review first")
            RowDivider()
            QuillRow(
                title = "See an example report", subtitle = "Exactly what a report contains",
                onClick = { nav.navigate(Screen.CrashReport) }, trailing = { RowChevron() },
            )
        }
        HelpText("Reports include the error, app version and device — never your audio, text, clipboard, or key.")

        SectionHeader("Permissions")
        QuillListContainer {
            QuillRow(
                leadIcon = Icons.Rounded.Mic, leadVariant = LeadIconVariant.PLAIN, title = "Microphone",
                trailing = { StatusPill(if (micGranted) "Granted" else "Off", if (micGranted) PillKind.OK else PillKind.ERROR) },
            )
            RowDivider()
            QuillRow(
                leadIcon = Icons.Rounded.AccessibilityNew,
                leadVariant = LeadIconVariant.PLAIN, title = "Accessibility",
                trailing = { StatusPill(if (a11yOn) "On" else "Off", if (a11yOn) PillKind.OK else PillKind.ERROR) },
            )
        }
    }
}

// ============================================================================
// C8 — Bubble appearance (stubbed selectors)
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BubbleAppearanceScreen(nav: QuillNav) {
    var size by rememberSaveable { mutableStateOf("Medium") } // TODO(M11): persist
    var theme by rememberSaveable { mutableStateOf("Match system") } // TODO(M11): persist

    DetailScaffold("Bubble appearance", nav) {
        Spacer(Modifier.height(8.dp))
        QuillCard {
            Row(
                Modifier.fillMaxWidth().padding(22.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PreviewTile(Color(0xFFF0F1F6), "Light app", Color(0xFF9A9AAC))
                PreviewTile(Color(0xFF15161C), "Dark app", Color(0xFF888888))
            }
        }
        HelpText("The light ring keeps the bubble visible on any background.", Modifier.fillMaxWidth())

        FieldLabel("Size", Modifier.padding(top = 16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Small", "Medium", "Large").forEach {
                QuillChip(it, selected = size == it, onClick = { size = it })
            }
        }

        FieldLabel("Theme", Modifier.padding(top = 16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Match system", "Light", "Dark").forEach {
                QuillChip(it, selected = theme == it, onClick = { theme = it })
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.PreviewTile(bg: Color, label: String, labelColor: Color) {
    Box(
        Modifier.weight(1f).height(74.dp).clip(RoundedCornerShape(14.dp)).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, style = MaterialTheme.typography.labelSmall, color = labelColor,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        )
        QuillBubble(BubbleState.IDLE, Icons.Rounded.Mic, size = 42.dp)
    }
}

// ============================================================================
// C9 — Advanced
// ============================================================================

@Composable
fun AdvancedScreen(nav: QuillNav) {
    val context = LocalContext.current
    val secretStore = remember { KeystoreSecretStore(context) }
    var baseUrl by rememberSaveable { mutableStateOf("") } // TODO(M5): wire to provider base-URL override
    var keepAlive by rememberSaveable { mutableStateOf(true) } // TODO(M11): persist
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }

    DetailScaffold("Advanced", nav) {
        FieldLabel("Custom endpoint (base URL)", Modifier.padding(top = 8.dp))
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            singleLine = true,
            placeholder = { Text("https://api.groq.com/openai/v1", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)) },
            leadingIcon = { Icon(Icons.Rounded.Language, contentDescription = null) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )
        HelpText("For any OpenAI-compatible provider. Changing this re-validates your key.")

        Spacer(Modifier.height(16.dp))
        QuillListContainer {
            QuillRow(title = "Request timeout", subtitle = "Give up after…", trailing = { RowValueChevron("30 s") })
            RowDivider()
            SwitchRow("Keep bubble alive aggressively", keepAlive, { keepAlive = it }, "Helps on phones that kill apps")
            RowDivider()
            QuillRow(
                title = "Battery exemption", subtitle = "Allow running unrestricted",
                trailing = { StatusPill("Check", PillKind.INFO) },
            )
        }

        SectionHeader("Reset")
        QuillListContainer {
            QuillRow(
                leadIcon = Icons.Rounded.RestartAlt, leadVariant = LeadIconVariant.PLAIN,
                title = "Reset settings to default",
                onClick = { /* TODO(M7): reset DataStore */ }, trailing = { RowChevron() },
            )
            RowDivider()
            QuillRow(
                leadIcon = Icons.Rounded.DeleteOutline, leadVariant = LeadIconVariant.ERROR,
                title = "Clear all data & keys", titleColor = MaterialTheme.colorScheme.error,
                onClick = { showClearConfirm = true }, trailing = { RowChevron() },
            )
        }
    }

    if (showClearConfirm) {
        ConfirmDialog(
            icon = Icons.Rounded.DeleteOutline,
            title = "Clear all data & keys?",
            body = "This removes your saved key and resets every setting on this device. It can't be undone.",
            confirmLabel = "Clear everything",
            onConfirm = {
                secretStore.clear(SecretStore.ALIAS_STT_KEY)
                secretStore.clear(SecretStore.ALIAS_LLM_KEY)
                showClearConfirm = false
            },
            onDismiss = { showClearConfirm = false },
        )
    }
}

// ============================================================================
// C10 — About & legal
// ============================================================================

@Composable
fun AboutScreen(nav: QuillNav) {
    DetailScaffold("About", nav) {
        Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            com.tihor.quill.ui.components.PermissionIllustration(com.tihor.quill.ui.components.QuillIcons.Feather)
            Spacer(Modifier.height(10.dp))
            Text(
                "Quill",
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = com.tihor.quill.ui.theme.WordmarkFont),
            )
            Text(
                "Version ${com.tihor.quill.BuildConfig.VERSION_NAME} · build ${com.tihor.quill.BuildConfig.VERSION_CODE}",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.quill.faint,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        QuillListContainer {
            QuillRow(title = "Privacy Policy", onClick = {}, trailing = { RowChevron() })
            RowDivider()
            QuillRow(title = "Terms of Service", onClick = {}, trailing = { RowChevron() })
            RowDivider()
            QuillRow(title = "Open-source licenses", onClick = {}, trailing = { RowChevron() })
            RowDivider()
            QuillRow(title = "Send feedback", onClick = {}, trailing = { RowChevron() })
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Made for people who'd rather talk than type.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.quill.faint,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ============================================================================
// Shared destructive-confirm dialog (board D4 / C9)
// ============================================================================

@Composable
fun ConfirmDialog(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        QuillCard {
            Column(Modifier.padding(20.dp)) {
                com.tihor.quill.ui.components.LeadIcon(icon, LeadIconVariant.ERROR)
                Spacer(Modifier.height(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.quill.muted)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    com.tihor.quill.ui.components.QuillGhostButton("Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
                    com.tihor.quill.ui.components.QuillDangerButton(confirmLabel, onClick = onConfirm, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
