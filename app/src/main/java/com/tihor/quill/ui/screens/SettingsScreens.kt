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
import com.tihor.quill.copy.CopyKeys as K
import com.tihor.quill.copy.copy
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
        topBar = { QuillTopBar(title = { QuillTopBarTitle(copy(K.SETTINGS_TITLE)) }) },
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
            SectionHeader(copy(K.SETTINGS_GROUP_DICTATION))
            QuillListContainer {
                QuillRow(
                    leadIcon = Icons.Rounded.GraphicEq, title = copy(K.SETTINGS_ROW_SPEECH_MODEL),
                    onClick = { nav.navigate(Screen.SpeechModel) },
                    trailing = { RowValueChevron("${settings.provider.displayName} · ${shortModel(settings.sttModel)}") },
                )
                RowDivider()
                QuillRow(
                    leadIcon = Icons.Rounded.Key, title = copy(K.SETTINGS_ROW_API_KEYS),
                    onClick = { nav.navigate(Screen.ApiKeys) },
                    trailing = { RowValueChevron(if (keyCount == 0) copy(K.VALUE_NONE) else copy(K.SETTINGS_KEYS_SET, keyCount)) },
                )
                RowDivider()
                QuillRow(
                    leadIcon = Icons.Rounded.AutoAwesome, title = copy(K.SETTINGS_ROW_CLEANUP),
                    onClick = { nav.navigate(Screen.AiCleanup) },
                    trailing = { RowValueChevron(if (settings.cleanupEnabled) copy(K.VALUE_ON) else copy(K.VALUE_OFF)) },
                )
                RowDivider()
                QuillRow(
                    leadIcon = Icons.Rounded.Create, title = copy(K.SETTINGS_ROW_CUSTOM_WORDS),
                    onClick = { nav.navigate(Screen.CustomWords) },
                    trailing = { RowValueChevron("0") }, // copy-exempt
                )
            }

            SectionHeader(copy(K.SETTINGS_GROUP_CAPTURE))
            QuillListContainer {
                QuillRow(
                    leadIcon = Icons.Rounded.Mic, leadVariant = LeadIconVariant.GOLD,
                    title = copy(K.SETTINGS_ROW_AUDIO),
                    onClick = { nav.navigate(Screen.AudioRecording) },
                    trailing = { RowChevron() },
                )
                RowDivider()
                QuillRow(
                    leadIcon = Icons.Rounded.PictureInPicture, title = copy(K.SETTINGS_ROW_BUBBLE),
                    onClick = { nav.navigate(Screen.BubbleAppearance) },
                    trailing = { RowChevron() },
                )
            }

            SectionHeader(copy(K.SETTINGS_GROUP_PRIVACY))
            QuillListContainer {
                QuillRow(
                    leadIcon = Icons.Rounded.Shield, title = copy(K.SETTINGS_ROW_PRIVACY),
                    onClick = { nav.navigate(Screen.Privacy) },
                    trailing = { RowChevron() },
                )
                RowDivider()
                QuillRow(
                    leadIcon = Icons.Rounded.Layers, leadVariant = LeadIconVariant.PLAIN,
                    title = copy(K.SETTINGS_ROW_ADVANCED),
                    onClick = { nav.navigate(Screen.Advanced) },
                    trailing = { RowChevron() },
                )
                RowDivider()
                QuillRow(
                    leadIcon = Icons.Rounded.Info, leadVariant = LeadIconVariant.PLAIN,
                    title = copy(K.SETTINGS_ROW_ABOUT),
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

    DetailScaffold(copy(K.SETTINGS_SPEECH_TITLE), nav) {
        FieldLabel(copy(K.SETTINGS_SPEECH_PROVIDER), Modifier.padding(top = 8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Only Groq ships in v1; the rest are shown disabled so the roadmap is legible.
            QuillChip("Groq", selected = true, onClick = {}) // copy-exempt
            QuillChip("OpenAI", selected = false, onClick = {}, enabled = false) // copy-exempt
            QuillChip("Deepgram", selected = false, onClick = {}, enabled = false) // copy-exempt
            QuillChip(copy(K.SETTINGS_SPEECH_PROVIDER_CUSTOM), selected = false, onClick = {}, enabled = false)
        }

        FieldLabel(copy(K.SETTINGS_SPEECH_MODEL), Modifier.padding(top = 16.dp))
        QuillListContainer {
            ModelRow(
                "whisper-large-v3-turbo", copy(K.SETTINGS_MODEL_TURBO_SUB), // copy-exempt
                selected = settings.sttModel.contains("turbo"),
                onClick = { scope.launch { repo.setSttModel("whisper-large-v3-turbo") } },
            )
            RowDivider()
            ModelRow(
                "whisper-large-v3", copy(K.SETTINGS_MODEL_ACCURATE_SUB), // copy-exempt
                selected = settings.sttModel == "whisper-large-v3",
                onClick = { scope.launch { repo.setSttModel("whisper-large-v3") } },
            )
        }

        FieldLabel(copy(K.SETTINGS_SPEECH_LANGUAGE), Modifier.padding(top = 16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QuillChip(
                copy(K.SETTINGS_LANG_EN),
                selected = settings.languageHint == "en",
                onClick = { scope.launch { repo.setLanguageHint("en") } },
            )
            QuillChip(
                copy(K.SETTINGS_LANG_AUTO),
                selected = settings.languageHint == QuillSettings.LANGUAGE_AUTO,
                onClick = { scope.launch { repo.setLanguageHint(QuillSettings.LANGUAGE_AUTO) } },
            )
            QuillChip(
                copy(K.SETTINGS_LANG_HI),
                selected = settings.languageHint == "hi",
                onClick = { scope.launch { repo.setLanguageHint("hi") } },
            )
        }
        HelpText(copy(K.SETTINGS_LANGUAGE_HELP))
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

    DetailScaffold(copy(K.SETTINGS_KEYS_TITLE), nav) {
        if (sttKey.isBlank()) {
            // Empty state (C3c) — zero keys, just the invitation to add one.
            Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    com.tihor.quill.ui.components.PermissionIllustration(Icons.Rounded.Key)
                    Spacer(Modifier.height(20.dp))
                    Text(copy(K.SETTINGS_KEYS_EMPTY_TITLE), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        copy(K.SETTINGS_KEYS_EMPTY_BODY),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.quill.text2,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    com.tihor.quill.ui.components.QuillPrimaryButton(
                        copy(K.SETTINGS_KEYS_ADD), onClick = { nav.navigate(Screen.SpeechModel) },
                        leadingIcon = Icons.Rounded.Key,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Banner(
                text = copy(K.SETTINGS_KEYS_BANNER),
                icon = Icons.Rounded.Lock, kind = PillKind.OK,
            )
            SectionHeader(copy(K.SETTINGS_KEYS_SECTION))
            KeyCard(
                role = copy(K.SETTINGS_KEYS_ROLE_STT), provider = "Groq", // copy-exempt
                masked = mask(sttKey), roleIcon = Icons.Rounded.GraphicEq,
                onRemove = { secretStore.clear(SecretStore.ALIAS_STT_KEY); sttKey = "" },
            )
            Spacer(Modifier.height(14.dp))
            com.tihor.quill.ui.components.QuillTonalButton(
                copy(K.SETTINGS_KEYS_ADD), onClick = { /* TODO(M7+): multi-key entry */ },
                leadingIcon = Icons.Rounded.Key,
            )
            HelpText(copy(K.SETTINGS_KEYS_HELP))
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
                StatusPill(copy(K.VALUE_VALID), PillKind.OK)
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
                LinkText(copy(K.SETTINGS_KEYS_REPLACE)) { }
                Dot()
                LinkText(copy(K.SETTINGS_KEYS_TEST)) { }
                Dot()
                LinkText(copy(K.SETTINGS_KEYS_REMOVE), color = MaterialTheme.colorScheme.error, onClick = onRemove)
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
    Text("·", color = MaterialTheme.quill.faint, style = MaterialTheme.typography.bodyMedium) // copy-exempt
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

    DetailScaffold(copy(K.SETTINGS_CLEANUP_TITLE), nav) {
        Spacer(Modifier.height(8.dp))
        QuillCard {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                com.tihor.quill.ui.components.LeadIcon(Icons.Rounded.AutoAwesome)
                Column(Modifier.weight(1f)) {
                    Text(copy(K.SETTINGS_CLEANUP_TIDY_TITLE), style = MaterialTheme.typography.titleMedium)
                    Text(
                        copy(K.SETTINGS_CLEANUP_TIDY_SUB),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.quill.muted,
                    )
                }
                Switch(
                    checked = settings.cleanupEnabled,
                    onCheckedChange = { scope.launch { repo.setCleanupEnabled(it) } },
                )
            }
        }

        SectionHeader(copy(K.SETTINGS_CLEANUP_WHAT))
        QuillCard {
            Column(Modifier.padding(16.dp)) {
                Text(copy(K.SETTINGS_CLEANUP_BEFORE), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.quill.faint)
                Text(
                    "\"um so like can you send me the uh the report by friday\"", // copy-exempt
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.quill.text2,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                )
                Text(copy(K.SETTINGS_CLEANUP_AFTER), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.quill.brandInk)
                Text(
                    "\"Can you send me the report by Friday?\"", // copy-exempt
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        QuillListContainer {
            QuillRow(title = copy(K.SETTINGS_CLEANUP_MODEL), trailing = { RowValueChevron("llama-3.3-70b") }) // copy-exempt
        }
        HelpText(copy(K.SETTINGS_CLEANUP_HELP))
    }
}

// ============================================================================
// C5 — Custom words (P1.5, stubbed)
// ============================================================================

@Composable
fun CustomWordsScreen(nav: QuillNav) {
    DetailScaffold(
        copy(K.SETTINGS_WORDS_TITLE), nav,
        actions = {
            androidx.compose.material3.IconButton(onClick = { /* TODO(P1.5) */ }) {
                Icon(
                    Icons.Rounded.Add, contentDescription = copy(K.SETTINGS_WORDS_ADD),
                    tint = MaterialTheme.quill.brandInk,
                )
            }
        },
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            copy(K.SETTINGS_WORDS_BODY),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.quill.muted,
            modifier = Modifier.padding(bottom = 14.dp),
        )
        // Placeholder until the substitution map ships (P1.5).
        QuillListContainer {
            QuillRow(title = copy(K.SETTINGS_WORDS_EMPTY_TITLE), subtitle = copy(K.SETTINGS_WORDS_EMPTY_SUB), trailing = {})
        }
        Spacer(Modifier.height(14.dp))
        com.tihor.quill.ui.components.QuillTonalButton(
            copy(K.SETTINGS_WORDS_ADD), onClick = { /* TODO(P1.5) */ },
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

    DetailScaffold(copy(K.SETTINGS_AUDIO_TITLE), nav) {
        FieldLabel(copy(K.SETTINGS_AUDIO_START_STOP), Modifier.padding(top = 8.dp))
        QuillListContainer {
            QuillRow(title = copy(K.SETTINGS_AUDIO_TRIGGER), subtitle = copy(K.SETTINGS_AUDIO_TRIGGER_SUB), trailing = { RowValueChevron(copy(K.SETTINGS_AUDIO_TRIGGER_VALUE)) })
            RowDivider()
            SwitchRow(copy(K.SETTINGS_AUDIO_HOLD), holdToTalk, { holdToTalk = it }, copy(K.SETTINGS_AUDIO_HOLD_SUB))
        }

        FieldLabel(copy(K.SETTINGS_AUDIO_MAX_LENGTH), Modifier.padding(top = 16.dp))
        QuillCard {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(copy(K.SETTINGS_AUDIO_AUTOSTOP), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.quill.muted)
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
            SwitchRow(copy(K.SETTINGS_AUDIO_SILENCE), stopOnSilence, { stopOnSilence = it }, copy(K.SETTINGS_AUDIO_SILENCE_SUB))
        }
        Spacer(Modifier.height(10.dp))
        Banner(
            text = copy(K.SETTINGS_AUDIO_SILENCE_BANNER),
            icon = Icons.Rounded.Info, kind = PillKind.LIVE,
        )

        Spacer(Modifier.height(16.dp))
        QuillListContainer {
            QuillRow(
                title = copy(K.SETTINGS_AUDIO_FORMAT), subtitle = copy(K.SETTINGS_AUDIO_FORMAT_SUB),
                trailing = { Text(copy(K.SETTINGS_AUDIO_FORMAT_VALUE), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.quill.faint) },
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
    var crashReports by rememberSaveable { mutableStateOf(true) } // TODO(M11): wire to crash opt-in

    val micGranted = androidx.core.content.ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.RECORD_AUDIO,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val a11yOn = AccessibilityUtil.isServiceEnabled(context)

    DetailScaffold(copy(K.SETTINGS_PRIVACY_TITLE), nav) {
        Spacer(Modifier.height(8.dp))
        Banner(
            title = copy(K.SETTINGS_PRIVACY_BANNER_TITLE),
            text = copy(K.SETTINGS_PRIVACY_BANNER_TEXT),
            icon = Icons.Rounded.Shield, kind = PillKind.OK,
        )
        Spacer(Modifier.height(14.dp))
        QuillListContainer {
            SwitchRow(
                copy(K.SETTINGS_PRIVACY_PRIVATE_MODE), settings.privateMode,
                { scope.launch { repo.setPrivateMode(it) } }, copy(K.SETTINGS_PRIVACY_PRIVATE_MODE_SUB),
            )
            RowDivider()
            SwitchRow(
                copy(K.SETTINGS_PRIVACY_HISTORY),
                settings.historyEnabled,
                { scope.launch { repo.setHistoryEnabled(it) } },
                copy(K.SETTINGS_PRIVACY_HISTORY_SUB),
            )
        }

        SectionHeader(copy(K.SETTINGS_PRIVACY_DIAGNOSTICS))
        QuillListContainer {
            SwitchRow(copy(K.SETTINGS_PRIVACY_CRASH), crashReports, { crashReports = it }, copy(K.SETTINGS_PRIVACY_CRASH_SUB))
            RowDivider()
            QuillRow(
                title = copy(K.SETTINGS_PRIVACY_EXAMPLE), subtitle = copy(K.SETTINGS_PRIVACY_EXAMPLE_SUB),
                onClick = { nav.navigate(Screen.CrashReport) }, trailing = { RowChevron() },
            )
        }
        HelpText(copy(K.SETTINGS_PRIVACY_DIAG_HELP))

        SectionHeader(copy(K.SETTINGS_PRIVACY_PERMISSIONS))
        QuillListContainer {
            QuillRow(
                leadIcon = Icons.Rounded.Mic, leadVariant = LeadIconVariant.PLAIN, title = copy(K.SETTINGS_PRIVACY_MIC),
                trailing = { StatusPill(if (micGranted) copy(K.VALUE_GRANTED) else copy(K.VALUE_OFF), if (micGranted) PillKind.OK else PillKind.ERROR) },
            )
            RowDivider()
            QuillRow(
                leadIcon = Icons.Rounded.AccessibilityNew,
                leadVariant = LeadIconVariant.PLAIN, title = copy(K.SETTINGS_PRIVACY_ACCESSIBILITY),
                trailing = { StatusPill(if (a11yOn) copy(K.VALUE_ON) else copy(K.VALUE_OFF), if (a11yOn) PillKind.OK else PillKind.ERROR) },
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

    DetailScaffold(copy(K.SETTINGS_BUBBLE_TITLE), nav) {
        Spacer(Modifier.height(8.dp))
        QuillCard {
            Row(
                Modifier.fillMaxWidth().padding(22.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PreviewTile(Color(0xFFF0F1F6), copy(K.SETTINGS_BUBBLE_PREVIEW_LIGHT), Color(0xFF9A9AAC))
                PreviewTile(Color(0xFF15161C), copy(K.SETTINGS_BUBBLE_PREVIEW_DARK), Color(0xFF888888))
            }
        }
        HelpText(copy(K.SETTINGS_BUBBLE_HELP), Modifier.fillMaxWidth())

        FieldLabel(copy(K.SETTINGS_BUBBLE_SIZE), Modifier.padding(top = 16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Small", "Medium", "Large").forEach { // copy-exempt
                QuillChip(it, selected = size == it, onClick = { size = it })
            }
        }

        FieldLabel(copy(K.SETTINGS_BUBBLE_THEME), Modifier.padding(top = 16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Match system", "Light", "Dark").forEach { // copy-exempt
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

    DetailScaffold(copy(K.SETTINGS_ADVANCED_TITLE), nav) {
        FieldLabel(copy(K.SETTINGS_ADVANCED_ENDPOINT), Modifier.padding(top = 8.dp))
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            singleLine = true,
            placeholder = { Text("https://api.groq.com/openai/v1", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)) }, // copy-exempt
            leadingIcon = { Icon(Icons.Rounded.Language, contentDescription = null) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )
        HelpText(copy(K.SETTINGS_ADVANCED_ENDPOINT_HELP))

        Spacer(Modifier.height(16.dp))
        QuillListContainer {
            QuillRow(title = copy(K.SETTINGS_ADVANCED_TIMEOUT), subtitle = copy(K.SETTINGS_ADVANCED_TIMEOUT_SUB), trailing = { RowValueChevron(copy(K.SETTINGS_ADVANCED_TIMEOUT_VALUE)) })
            RowDivider()
            SwitchRow(copy(K.SETTINGS_ADVANCED_KEEPALIVE), keepAlive, { keepAlive = it }, copy(K.SETTINGS_ADVANCED_KEEPALIVE_SUB))
            RowDivider()
            QuillRow(
                title = copy(K.SETTINGS_ADVANCED_BATTERY), subtitle = copy(K.SETTINGS_ADVANCED_BATTERY_SUB),
                trailing = { StatusPill(copy(K.VALUE_CHECK), PillKind.INFO) },
            )
        }

        SectionHeader(copy(K.SETTINGS_ADVANCED_RESET))
        QuillListContainer {
            QuillRow(
                leadIcon = Icons.Rounded.RestartAlt, leadVariant = LeadIconVariant.PLAIN,
                title = copy(K.SETTINGS_ADVANCED_RESET_SETTINGS),
                onClick = { /* TODO(M7): reset DataStore */ }, trailing = { RowChevron() },
            )
            RowDivider()
            QuillRow(
                leadIcon = Icons.Rounded.DeleteOutline, leadVariant = LeadIconVariant.ERROR,
                title = copy(K.SETTINGS_ADVANCED_CLEAR), titleColor = MaterialTheme.colorScheme.error,
                onClick = { showClearConfirm = true }, trailing = { RowChevron() },
            )
        }
    }

    if (showClearConfirm) {
        ConfirmDialog(
            icon = Icons.Rounded.DeleteOutline,
            title = copy(K.SETTINGS_ADVANCED_CLEAR_CONFIRM_TITLE),
            body = copy(K.SETTINGS_ADVANCED_CLEAR_CONFIRM_BODY),
            confirmLabel = copy(K.SETTINGS_ADVANCED_CLEAR_CONFIRM_CTA),
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
    DetailScaffold(copy(K.SETTINGS_ABOUT_TITLE), nav) {
        Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            com.tihor.quill.ui.components.PermissionIllustration(com.tihor.quill.ui.components.QuillIcons.Feather)
            Spacer(Modifier.height(10.dp))
            Text(
                "Quill", // copy-exempt
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = com.tihor.quill.ui.theme.WordmarkFont),
            )
            Text(
                copy(K.SETTINGS_ABOUT_VERSION, com.tihor.quill.BuildConfig.VERSION_NAME, com.tihor.quill.BuildConfig.VERSION_CODE),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.quill.faint,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        QuillListContainer {
            QuillRow(title = copy(K.SETTINGS_ABOUT_PRIVACY_POLICY), onClick = {}, trailing = { RowChevron() })
            RowDivider()
            QuillRow(title = copy(K.SETTINGS_ABOUT_TERMS), onClick = {}, trailing = { RowChevron() })
            RowDivider()
            QuillRow(title = copy(K.SETTINGS_ABOUT_LICENSES), onClick = {}, trailing = { RowChevron() })
            RowDivider()
            QuillRow(title = copy(K.SETTINGS_ABOUT_FEEDBACK), onClick = {}, trailing = { RowChevron() })
        }
        Spacer(Modifier.height(24.dp))
        Text(
            copy(K.SETTINGS_ABOUT_FOOTER),
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
    cancelLabel: String = "Cancel",
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
                    com.tihor.quill.ui.components.QuillGhostButton(cancelLabel, onClick = onDismiss, modifier = Modifier.weight(1f))
                    com.tihor.quill.ui.components.QuillDangerButton(confirmLabel, onClick = onConfirm, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
