package com.tihor.quill.ui.screens

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Create
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tihor.quill.accessibility.AccessibilityUtil
import com.tihor.quill.copy.CopyKeys as K
import com.tihor.quill.copy.copy
import com.tihor.quill.audio.StopReason
import com.tihor.quill.secrets.KeystoreSecretStore
import com.tihor.quill.secrets.SecretStore
import com.tihor.quill.secrets.SttKeyStore
import com.tihor.quill.history.HistoryRepository
import com.tihor.quill.crash.CrashReporter
import com.tihor.quill.reliability.BatteryOptimization
import com.tihor.quill.settings.AppDataReset
import com.tihor.quill.settings.CustomEndpoint
import com.tihor.quill.settings.CustomWord
import com.tihor.quill.settings.CustomWordsStore
import com.tihor.quill.settings.QuillSettings
import com.tihor.quill.settings.SettingsRepository
import com.tihor.quill.settings.VoiceWordCapture
import com.tihor.quill.settings.SttProvider
import com.tihor.quill.style.WritingStyleStore
import com.tihor.quill.ui.LegalLinks
import com.tihor.quill.stt.KeyValidation
import com.tihor.quill.stt.KeyValidator
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                .imePadding()
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

/** Disabled "Coming soon" chip — the honest affordance for a control whose real home is a later milestone. */
@Composable
private fun ComingSoonChip() {
    QuillChip(copy(K.COMMON_COMING_SOON), selected = false, onClick = {}, enabled = false)
}

/** A row for a not-yet-built control: muted title, no onClick (so it can't appear to act), Coming-soon chip. */
@Composable
private fun ComingSoonRow(title: String, subtitle: String? = null) {
    QuillRow(title = title, subtitle = subtitle, titleColor = MaterialTheme.quill.muted, trailing = { ComingSoonChip() })
}

/** About legal link: a real `ACTION_VIEW` when [url] is set, otherwise a disabled "Coming soon" row. */
@Composable
private fun LegalRow(title: String, url: String) {
    val context = LocalContext.current
    if (url.isBlank()) {
        ComingSoonRow(title)
    } else {
        QuillRow(
            title = title,
            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) },
            trailing = { RowChevron() },
        )
    }
}

/** A link rendered disabled (muted, not clickable) — its real behavior isn't available yet. */
@Composable
private fun DisabledLinkText(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.quill.faint)
}

// ============================================================================
// C1 — Settings index
// ============================================================================

@Composable
fun SettingsScreen(nav: QuillNav) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val secretStore = remember { KeystoreSecretStore(context) }
    val wordsStore = remember { CustomWordsStore(context) }
    val vocabStore = remember { com.tihor.quill.vocab.ContactsVocabularyStore(context) }
    val settings by repo.settings.collectAsState(initial = QuillSettings())
    val customWords by wordsStore.words.collectAsState(initial = emptyList())
    val contactsVocab by vocabStore.terms.collectAsState(initial = emptyList())

    val sttKeys = remember { SttKeyStore(secretStore) }
    val keyCount = SttProvider.selectable().count { sttKeys.has(it) } +
        (if (secretStore.contains(SecretStore.ALIAS_LLM_KEY)) 1 else 0)

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
                    subtitle = "${settings.provider.displayName} · ${shortModel(settings.sttModel)}", // copy-exempt: provider and model identifiers
                    onClick = { nav.navigate(Screen.SpeechModel) },
                    trailing = { RowChevron() },
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
                    leadIcon = Icons.Rounded.Style, title = copy(K.SETTINGS_ROW_WRITING_STYLE),
                    onClick = { nav.navigate(Screen.WritingStyle) },
                    trailing = { RowValueChevron(styleName(settings.writingStyleDefault)) },
                )
                RowDivider()
                QuillRow(
                    leadIcon = Icons.Rounded.Create, title = copy(K.SETTINGS_ROW_CUSTOM_WORDS),
                    onClick = { nav.navigate(Screen.CustomWords) },
                    trailing = { RowValueChevron(copy(K.SETTINGS_WORDS_COUNT, customWords.size)) },
                )
                RowDivider()
                QuillRow(
                    leadIcon = Icons.Rounded.Contacts, title = copy(K.SETTINGS_ROW_CONTACTS_VOCAB),
                    onClick = { nav.navigate(Screen.ContactsVocab) },
                    trailing = { RowValueChevron(copy(K.SETTINGS_VOCAB_COUNT, contactsVocab.size)) },
                )
            }

            SectionHeader(copy(K.SETTINGS_GROUP_CAPTURE))
            QuillListContainer {
                QuillRow(
                    leadIcon = Icons.Rounded.PictureInPicture, title = copy(K.SETTINGS_ROW_BUBBLE),
                    onClick = { nav.navigate(Screen.BubbleAppearance) },
                    trailing = { RowChevron() },
                )
            }

            SectionHeader(copy(K.SETTINGS_GROUP_PRIVACY))
            QuillListContainer {
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
    val sttKeys = remember { SttKeyStore(KeystoreSecretStore(context)) }
    val scope = rememberCoroutineScope()
    val settings by repo.settings.collectAsState(initial = QuillSettings())

    DetailScaffold(copy(K.SETTINGS_SPEECH_TITLE), nav) {
        FieldLabel(copy(K.KEY_CHOOSE))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SttProvider.selectable().forEach { provider ->
                QuillChip(providerLabel(provider), selected = settings.provider == provider,
                    onClick = { scope.launch { repo.setProvider(provider) } })
            }
        }
        TextButton(onClick = { nav.navigate(Screen.ApiKeys) }) { Text(copy(K.SETTINGS_ROW_API_KEYS)) }

        FieldLabel(copy(K.SETTINGS_SPEECH_MODEL), Modifier.padding(top = 16.dp))
        com.tihor.quill.ui.components.ModelDropdown(
            provider = settings.provider,
            selected = settings.sttModel,
            customBaseUrl = settings.customBaseUrl,
            onSelect = { model -> scope.launch {
                if (settings.provider == SttProvider.CUSTOM) repo.setCustomModel(model) else repo.setSttModel(model)
            } },
        )
        if (settings.provider == SttProvider.CUSTOM) CustomEndpointFields(settings, repo)

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
                Icons.Rounded.Check, contentDescription = copy(K.A11Y_SELECTED),
                tint = MaterialTheme.quill.brandInk, modifier = Modifier.size(20.dp),
            )
        }
    })
}

/** Provider chip label: proper nouns stay copy-exempt; only "Custom" is a translatable word. */
@Composable
private fun providerLabel(provider: SttProvider): String =
    if (provider == SttProvider.CUSTOM) copy(K.SETTINGS_SPEECH_PROVIDER_CUSTOM) else provider.displayName

/**
 * Free-text model id (OpenRouter's catalog is huge; Custom is user-defined). Local typing state wins
 * while editing; until then the field tracks the persisted value (which loads async from DataStore).
 */
@Composable
private fun FreeTextModelField(value: String, onValue: (String) -> Unit) {
    var typed by remember { mutableStateOf<String?>(null) }
    FieldLabel(copy(K.SETTINGS_SPEECH_MODEL_FIELD_LABEL), Modifier.padding(top = 10.dp))
    OutlinedTextField(
        value = typed ?: value,
        onValueChange = { typed = it; onValue(it) },
        singleLine = true,
        placeholder = { Text(copy(K.SETTINGS_SPEECH_MODEL_FIELD_HINT)) },
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Custom provider config (decision 1): OpenAI-compatible base URL (required, https-only) + model id
 * (required, free text). An invalid URL shows the error and is never persisted.
 */
@Composable
private fun CustomEndpointFields(settings: QuillSettings, repo: SettingsRepository) {
    val scope = rememberCoroutineScope()
    var typedUrl by remember { mutableStateOf<String?>(null) }
    val url = typedUrl ?: settings.customBaseUrl
    val urlInvalid = url.isNotBlank() && !CustomEndpoint.isValidBaseUrl(url)

    FieldLabel(copy(K.SETTINGS_SPEECH_CUSTOM_URL_LABEL), Modifier.padding(top = 10.dp))
    OutlinedTextField(
        value = url,
        onValueChange = {
            typedUrl = it
            if (CustomEndpoint.isValidBaseUrl(it)) scope.launch { repo.setCustomBaseUrl(it) }
        },
        singleLine = true,
        isError = urlInvalid,
        placeholder = { Text(copy(K.SETTINGS_SPEECH_CUSTOM_URL_HINT)) },
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    )
    if (urlInvalid) {
        HelpText(copy(K.SETTINGS_SPEECH_CUSTOM_URL_ERROR), bad = true)
    } else {
        HelpText(copy(K.SETTINGS_SPEECH_CUSTOM_URL_HELP))
    }

    FreeTextModelField(
        value = settings.customModel,
        onValue = { scope.launch { repo.setCustomModel(it) } },
    )
}

// ============================================================================
// C3 — API keys
// ============================================================================


@Composable
fun ApiKeysScreen(nav: QuillNav) {
    val context = LocalContext.current
    val sttKeys = remember { SttKeyStore(KeystoreSecretStore(context)) }
    val repo = remember { SettingsRepository(context) }
    val settings by repo.settings.collectAsState(initial = QuillSettings())
    // Bumped after every store mutation so the per-provider card list re-reads the Keystore.
    var revision by remember { mutableStateOf(0) }
    val stored = remember(revision) { SttProvider.selectable().filter { sttKeys.has(it) } }
    // The provider the inline entry form is open for (null = closed).
    var addingFor by rememberSaveable { mutableStateOf<String?>(null) }

    DetailScaffold(copy(K.SETTINGS_KEYS_TITLE), nav) {
        val adding = addingFor
        if (adding != null) {
            ApiKeyEntry(
                initialProvider = SttProvider.fromId(adding),
                onSaved = { revision++; addingFor = null },
                onCancel = { addingFor = null },
            )
        } else if (stored.isEmpty()) {
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
                        copy(K.SETTINGS_KEYS_ADD), onClick = { addingFor = settings.provider.id },
                        leadingIcon = Icons.Rounded.Key,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Banner(
                text = copy(K.SETTINGS_KEYS_BANNER),
                icon = Icons.Rounded.Lock, kind = PillKind.OK,
            )
            SectionHeader(copy(K.SETTINGS_KEYS_SECTION))
            stored.forEachIndexed { i, provider ->
                if (i > 0) Spacer(Modifier.height(10.dp))
                sttKeys.list(provider).forEachIndexed { keyIndex, saved ->
                    KeyCard(
                        role = copy(K.SETTINGS_KEYS_ROLE_STT), provider = providerLabel(provider),
                        masked = mask(saved.key), roleIcon = Icons.Rounded.Key,
                        onRemove = { sttKeys.remove(provider, saved.id); revision++ },
                        onReplace = { addingFor = provider.id },
                    )
                    TextButton(onClick = { sttKeys.select(provider, saved.id); revision++ },
                        enabled = saved.id != sttKeys.activeId(provider)) {
                        Text(copy(if (saved.id == sttKeys.activeId(provider)) K.KEY_ACTIVE else K.KEY_USE))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            com.tihor.quill.ui.components.QuillTonalButton(
                copy(K.SETTINGS_KEYS_ADD), onClick = { addingFor = settings.provider.id },
                leadingIcon = Icons.Rounded.Key,
            )
            HelpText(copy(K.SETTINGS_KEYS_HELP))
        }
    }
}

/** Shared provider setup; only authenticated keys can be saved. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.ApiKeyEntry(
    initialProvider: SttProvider,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    com.tihor.quill.ui.components.ProviderKeyAccordion(
        secretStore = remember { KeystoreSecretStore(context) },
        onSaved = onSaved, initialProvider = initialProvider, activateSpeechProvider = false,
    )
    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(copy(K.ACTION_CANCEL)) }
}
@Composable
private fun KeyCard(
    role: String,
    provider: String,
    masked: String,
    roleIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onRemove: () -> Unit,
    onReplace: () -> Unit,
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
                LinkText(copy(K.SETTINGS_KEYS_ADD), onClick = onReplace)
                Dot()
                // Disabled: re-validating in place needs checking/invalid card states that don't exist
                // yet. "Replace" re-opens entry, which re-validates on save (M5). (Pass 2 / no-lies.)
                DisabledLinkText(copy(K.SETTINGS_KEYS_TEST))
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(copy(K.CLEANUP_AUTO), modifier = Modifier.weight(1f))
            Switch(checked = settings.cleanupAutomatic, onCheckedChange = { scope.launch { repo.setCleanupAutomatic(it) } })
        }
        HelpText(copy(K.CLEANUP_AUTO_HELP))
        FieldLabel(copy(K.SETTINGS_SPEECH_PROVIDER), Modifier.padding(top = 16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SttProvider.selectable().forEach { provider ->
                QuillChip(providerLabel(provider), selected = settings.cleanupProvider == provider,
                    onClick = { scope.launch { repo.setCleanupProvider(provider) } })
            }
        }
        val cleanupKeySaved = SttKeyStore(KeystoreSecretStore(context)).has(settings.cleanupProvider)
        if (!cleanupKeySaved) {
            Banner(text = copy(K.SETTINGS_SPEECH_KEY_REQUIRED, providerLabel(settings.cleanupProvider)),
                icon = Icons.Rounded.Key, kind = PillKind.ERROR)
            TextButton(onClick = { nav.navigate(Screen.ApiKeys) }) { Text(copy(K.SETTINGS_SPEECH_KEY_REQUIRED_CTA)) }
        }
        if (settings.cleanupProvider == SttProvider.CUSTOM) {
            HelpText(copy(K.SETTINGS_CLEANUP_CUSTOM))
            var customUrl by remember(settings.customBaseUrl) { mutableStateOf(settings.customBaseUrl) }
            OutlinedTextField(customUrl, { value -> customUrl = value }, singleLine = true,
                label = { Text(copy(K.SETTINGS_SPEECH_CUSTOM_URL_LABEL)) }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { scope.launch { repo.setCustomBaseUrl(customUrl.trim()) } },
                enabled = runCatching { com.tihor.quill.cleanup.CleanupProviders.baseUrl(SttProvider.CUSTOM, customUrl) }.isSuccess) {
                Text(copy(K.MODELS_SAVE))
            }
            FreeTextModelField(value = settings.cleanupModel, onValue = { scope.launch { repo.setCleanupModel(it) } })
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
        com.tihor.quill.ui.components.ModelDropdown(
            provider = settings.cleanupProvider,
            customBaseUrl = settings.customBaseUrl,
            selected = settings.cleanupModel,
            use = com.tihor.quill.models.ModelUse.CLEANUP,
            onSelect = { model -> scope.launch { repo.setCleanupModel(model) } },
        )
        HelpText(copy(K.SETTINGS_CLEANUP_HELP))
    }
}

// ============================================================================
// C5 — Custom words (P1.5, stubbed)
// ============================================================================

@Composable
fun CustomWordsScreen(nav: QuillNav) {
    val context = LocalContext.current
    val store = remember { CustomWordsStore(context) }
    val scope = rememberCoroutineScope()
    val words by store.words.collectAsState(initial = emptyList())
    var showAdd by rememberSaveable { mutableStateOf(false) }

    DetailScaffold(
        copy(K.SETTINGS_WORDS_TITLE), nav,
        actions = {
            IconButton(onClick = { showAdd = true }) {
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
        QuillListContainer {
            if (words.isEmpty()) {
                QuillRow(title = copy(K.SETTINGS_WORDS_EMPTY_TITLE), subtitle = copy(K.SETTINGS_WORDS_EMPTY_SUB), trailing = {})
            } else {
                words.forEachIndexed { i, word ->
                    if (i > 0) RowDivider()
                    QuillRow(
                        title = word.heard,
                        subtitle = "→ ${word.replacement}", // copy-exempt
                        trailing = {
                            IconButton(onClick = { scope.launch { store.remove(word) } }) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = copy(K.SETTINGS_WORDS_DELETE),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        com.tihor.quill.ui.components.QuillTonalButton(
            copy(K.SETTINGS_WORDS_ADD), onClick = { showAdd = true },
            leadingIcon = Icons.Rounded.Add,
        )
    }

    if (showAdd) {
        AddWordDialog(
            onAdd = { heard, replacement ->
                scope.launch { store.add(heard, replacement) }
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }
}

private enum class WordCapturePhase { IDLE, RECORDING, TRANSCRIBING }

@Composable
private fun AddWordDialog(onAdd: (String, String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val capture = remember { VoiceWordCapture(context) }
    val scope = rememberCoroutineScope()
    var heard by rememberSaveable { mutableStateOf("") }
    var replacement by rememberSaveable { mutableStateOf("") }
    var phase by remember { mutableStateOf(WordCapturePhase.IDLE) }
    var errorKey by remember { mutableStateOf<String?>(null) }
    val canSave = heard.isNotBlank() && replacement.isNotBlank()

    // Stop the microphone if the dialog is dismissed mid-capture. Otherwise VoiceWordCapture's recording
    // thread keeps the mic live (up to the 8 s cap) after the UI is gone — a resource leak, and in a
    // dictation app a privacy concern (the mic-active indicator lingers). Mirrors MicTestStep.
    DisposableEffect(Unit) {
        onDispose { capture.stopRecording() }
    }

    fun beginCapture() {
        errorKey = null
        phase = WordCapturePhase.RECORDING
        capture.startRecording { wav, reason ->
            // Fires on the recording thread — hop to Main (scope dispatches there) for UI + STT.
            scope.launch {
                if (reason == StopReason.ERROR) {
                    phase = WordCapturePhase.IDLE
                    errorKey = K.SETTINGS_WORDS_ERR_RECORD
                    runCatching { wav.delete() }
                    return@launch
                }
                phase = WordCapturePhase.TRANSCRIBING
                try {
                    capture.transcribe(wav).fold(
                        onSuccess = { text ->
                            if (text.isNotBlank()) heard = text
                            phase = WordCapturePhase.IDLE
                        },
                        onFailure = {
                            errorKey = when ((it as? VoiceWordCapture.FailureException)?.failure) {
                                VoiceWordCapture.Failure.NoKey -> K.SETTINGS_WORDS_ERR_KEY
                                VoiceWordCapture.Failure.NoSpeech -> K.RECORDING_NO_SPEECH
                                else -> K.SETTINGS_WORDS_ERR_NETWORK
                            }
                            phase = WordCapturePhase.IDLE
                        },
                    )
                } finally { runCatching { wav.delete() } }
            }
        }
    }

    val micPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) beginCapture() else errorKey = K.SETTINGS_WORDS_ERR_PERMISSION
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        QuillCard {
            Column(Modifier.padding(20.dp)) {
                Text(copy(K.SETTINGS_WORDS_ADD_TITLE), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                FieldLabel(copy(K.SETTINGS_WORDS_HEARD_LABEL))
                OutlinedTextField(
                    value = heard,
                    onValueChange = { heard = it },
                    singleLine = true,
                    placeholder = { Text(copy(K.SETTINGS_WORDS_HEARD_HINT)) },
                    trailingIcon = {
                        when (phase) {
                            WordCapturePhase.TRANSCRIBING ->
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else -> IconButton(
                                onClick = {
                                    when (phase) {
                                        WordCapturePhase.RECORDING -> capture.stopRecording()
                                        else ->
                                            if (capture.hasMicPermission()) beginCapture()
                                            else micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                            ) {
                                Icon(
                                    Icons.Rounded.Mic,
                                    contentDescription = copy(K.SETTINGS_WORDS_RECORD),
                                    tint = if (phase == WordCapturePhase.RECORDING) MaterialTheme.quill.gold
                                    else MaterialTheme.quill.brandInk,
                                )
                            }
                        }
                    },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                when {
                    phase == WordCapturePhase.RECORDING ->
                        HelpText(copy(K.SETTINGS_WORDS_LISTENING))
                    phase == WordCapturePhase.TRANSCRIBING ->
                        HelpText(copy(K.SETTINGS_WORDS_TRANSCRIBING))
                    errorKey != null ->
                        Text(
                            copy(errorKey!!),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                }
                Spacer(Modifier.height(12.dp))
                FieldLabel(copy(K.SETTINGS_WORDS_REPLACEMENT_LABEL))
                OutlinedTextField(
                    value = replacement,
                    onValueChange = { replacement = it },
                    singleLine = true,
                    placeholder = { Text(copy(K.SETTINGS_WORDS_REPLACEMENT_HINT)) },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    com.tihor.quill.ui.components.QuillGhostButton(
                        copy(K.ACTION_CANCEL), onClick = onDismiss, modifier = Modifier.weight(1f),
                    )
                    com.tihor.quill.ui.components.QuillTonalButton(
                        copy(K.SETTINGS_WORDS_SAVE),
                        onClick = { onAdd(heard.trim(), replacement.trim()) },
                        enabled = canSave,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

// ============================================================================
// C5b — Contacts vocabulary (Phase 1: opt-in import, on-device only, unwired)
// ============================================================================

@Composable
fun ContactsVocabScreen(nav: QuillNav) {
    val context = LocalContext.current
    val importer = remember { com.tihor.quill.vocab.ContactsImporter(context) }
    val store = remember { com.tihor.quill.vocab.ContactsVocabularyStore(context) }
    val scope = rememberCoroutineScope()
    val terms by store.terms.collectAsState(initial = emptyList())

    var granted by remember { mutableStateOf(importer.hasPermission()) }
    var importing by remember { mutableStateOf(false) }
    var importedCount by remember { mutableStateOf<Int?>(null) }
    var importFailed by remember { mutableStateOf(false) }
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }

    fun doImport() {
        scope.launch {
            importing = true
            importFailed = false
            importedCount = null
            importer.import().fold(
                onSuccess = { importedCount = it },
                onFailure = { importFailed = true },
            )
            importing = false
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { wasGranted ->
        granted = wasGranted
        if (wasGranted) doImport()
    }

    // Re-check on resume: the user may have just granted contacts access in system settings and
    // come back — the "permission needed" state must clear itself without a process restart.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) granted = importer.hasPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DetailScaffold(copy(K.SETTINGS_VOCAB_TITLE), nav) {
        Spacer(Modifier.height(8.dp))
        Text(
            copy(K.SETTINGS_VOCAB_BODY),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.quill.muted,
            modifier = Modifier.padding(bottom = 14.dp),
        )

        if (!granted) {
            // Denied (or never granted): honest "permission needed" state with a way out — same
            // deep-link-to-system-settings pattern as the battery row in Advanced.
            QuillListContainer {
                QuillRow(
                    leadIcon = Icons.Rounded.Contacts, leadVariant = LeadIconVariant.PLAIN,
                    title = copy(K.SETTINGS_VOCAB_PERMISSION_NEEDED),
                    trailing = { StatusPill(copy(K.VALUE_OFF), PillKind.ERROR) },
                )
            }
            Spacer(Modifier.height(14.dp))
            com.tihor.quill.ui.components.QuillTonalButton(
                copy(K.SETTINGS_VOCAB_OPEN_SETTINGS),
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.fromParts("package", context.packageName, null),
                            ),
                        )
                    }
                },
                leadingIcon = Icons.Rounded.Settings,
            )
        } else {
            QuillListContainer {
                if (terms.isEmpty()) {
                    QuillRow(
                        title = copy(K.SETTINGS_VOCAB_EMPTY_TITLE),
                        subtitle = copy(K.SETTINGS_VOCAB_EMPTY_SUB), trailing = {},
                    )
                } else {
                    QuillRow(
                        leadIcon = Icons.Rounded.Contacts, leadVariant = LeadIconVariant.PLAIN,
                        title = copy(K.SETTINGS_VOCAB_IMPORTED, terms.size),
                        trailing = { StatusPill(copy(K.VALUE_ON), PillKind.OK) },
                    )
                }
            }
            if (importFailed) {
                Text(
                    copy(K.SETTINGS_VOCAB_IMPORT_FAILED),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (terms.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                QuillListContainer {
                    terms.forEachIndexed { i, term ->
                        if (i > 0) RowDivider()
                        QuillRow(title = term, trailing = {}) // copy-exempt: user data, not UI copy
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            com.tihor.quill.ui.components.QuillTonalButton(
                if (importing) copy(K.SETTINGS_VOCAB_IMPORTING)
                else if (terms.isEmpty()) copy(K.SETTINGS_VOCAB_IMPORT)
                else copy(K.SETTINGS_VOCAB_REIMPORT),
                onClick = { doImport() },
                enabled = !importing,
                leadingIcon = Icons.Rounded.Download,
            )
            Spacer(Modifier.height(10.dp))
            if (terms.isNotEmpty()) {
                com.tihor.quill.ui.components.QuillGhostButton(
                    copy(K.SETTINGS_VOCAB_CLEAR), onClick = { showClearConfirm = true },
                )
            }
        }
    }

    if (showClearConfirm) {
        ConfirmDialog(
            icon = Icons.Rounded.DeleteOutline,
            title = copy(K.SETTINGS_VOCAB_CLEAR_CONFIRM_TITLE),
            body = copy(K.SETTINGS_VOCAB_CLEAR_CONFIRM_BODY),
            confirmLabel = copy(K.SETTINGS_VOCAB_CLEAR_CONFIRM_CTA),
            cancelLabel = copy(K.ACTION_CANCEL),
            onConfirm = { scope.launch { store.clear() }; showClearConfirm = false },
            onDismiss = { showClearConfirm = false },
        )
    }
}

// ============================================================================
// C6 — Audio & recording
// ============================================================================

private fun formatCap(seconds: Int): String = if (seconds < 60) "${seconds}s" else "${seconds / 60} min"

@Composable
private fun retentionLabel(minutes: Int): String = copy(
    when (minutes) {
        60 -> K.SETTINGS_PRIVACY_HISTORY_RETENTION_HOUR
        1_440 -> K.SETTINGS_PRIVACY_HISTORY_RETENTION_DAY
        10_080 -> K.SETTINGS_PRIVACY_HISTORY_RETENTION_WEEK
        43_200 -> K.SETTINGS_PRIVACY_HISTORY_RETENTION_MONTH
        else -> K.SETTINGS_PRIVACY_HISTORY_RETENTION_FOREVER
    },
)

// ============================================================================
// C7 — Privacy
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrivacyScreen(nav: QuillNav) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()
    val settings by repo.settings.collectAsState(initial = QuillSettings())

    DetailScaffold(copy(K.SETTINGS_PRIVACY_TITLE), nav) {
        Spacer(Modifier.height(8.dp))
        Banner(
            title = copy(K.SETTINGS_PRIVACY_BANNER_TITLE),
            text = copy(K.SETTINGS_PRIVACY_BANNER_TEXT),
            icon = Icons.Rounded.Shield, kind = PillKind.OK,
        )
        Spacer(Modifier.height(14.dp))
        val savingHistory = settings.historyEnabled && !settings.privateMode
        val modeTitle = copy(if (savingHistory) K.SETTINGS_PRIVACY_SAVING else K.SETTINGS_PRIVACY_PRIVATE_MODE)
        QuillListContainer {
            QuillRow(
                title = modeTitle,
                subtitle = copy(if (savingHistory) K.SETTINGS_PRIVACY_HISTORY_SUB else K.SETTINGS_PRIVACY_PRIVATE_MODE_SUB),
                trailing = {
                    Switch(
                        checked = savingHistory,
                        onCheckedChange = { enabled -> scope.launch { repo.setHistorySaving(enabled) } },
                        thumbContent = {
                            Icon(if (savingHistory) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        modifier = Modifier.semantics { contentDescription = modeTitle },
                    )
                },
            )
        }
        if (savingHistory) {
            FieldLabel(copy(K.SETTINGS_PRIVACY_HISTORY_RETENTION), Modifier.padding(top = 16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QuillSettings.RETENTION_OPTIONS.forEach { minutes ->
                    QuillChip(
                        retentionLabel(minutes),
                        selected = settings.historyRetentionMinutes == minutes,
                        onClick = { scope.launch { repo.setHistoryRetentionMinutes(minutes) } },
                    )
                }
            }
        }

        SectionHeader(copy(K.SETTINGS_PRIVACY_DIAGNOSTICS))
        QuillListContainer {
            // M11: opt-in, local-first crash reporting. Default OFF; keep the reporter's cached flag
            // in sync so it doesn't read DataStore at crash time.
            SwitchRow(
                copy(K.SETTINGS_PRIVACY_CRASH), settings.crashReportingEnabled,
                { on -> scope.launch { repo.setCrashReportingEnabled(on) }; CrashReporter.setEnabled(on) },
                copy(K.SETTINGS_PRIVACY_CRASH_SUB),
            )
            RowDivider()
            QuillRow(
                title = copy(K.SETTINGS_PRIVACY_EXAMPLE), subtitle = copy(K.SETTINGS_PRIVACY_EXAMPLE_SUB),
                onClick = { nav.navigate(Screen.CrashReport) }, trailing = { RowChevron() },
            )
        }
        HelpText(copy(K.SETTINGS_PRIVACY_DIAG_HELP))

    }
}

// ============================================================================
// C8 — Bubble appearance (stubbed selectors)
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BubbleAppearanceScreen(nav: QuillNav) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()
    val settings by repo.settings.collectAsState(initial = QuillSettings())

    val previewSize = when (settings.bubbleSize) {
        QuillSettings.BUBBLE_SIZE_SMALL -> 34.dp
        QuillSettings.BUBBLE_SIZE_LARGE -> 52.dp
        else -> 42.dp
    }

    DetailScaffold(copy(K.SETTINGS_BUBBLE_TITLE), nav) {
        Spacer(Modifier.height(8.dp))
        QuillCard {
            Row(
                Modifier.fillMaxWidth().padding(22.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PreviewTile(Color(0xFFF0F1F6), copy(K.SETTINGS_BUBBLE_PREVIEW_LIGHT), Color(0xFF7B867E), previewSize)
                PreviewTile(Color(0xFF15161C), copy(K.SETTINGS_BUBBLE_PREVIEW_DARK), Color(0xFF888888), previewSize)
            }
        }
        HelpText(copy(K.SETTINGS_BUBBLE_HELP), Modifier.fillMaxWidth())

        FieldLabel(copy(K.SETTINGS_BUBBLE_SIZE), Modifier.padding(top = 16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf( // copy-exempt
                QuillSettings.BUBBLE_SIZE_SMALL, QuillSettings.BUBBLE_SIZE_MEDIUM, QuillSettings.BUBBLE_SIZE_LARGE,
            ).forEach {
                QuillChip(it, selected = settings.bubbleSize == it, onClick = { scope.launch { repo.setBubbleSize(it) } })
            }
        }

        FieldLabel(copy(K.SETTINGS_BUBBLE_THEME), Modifier.padding(top = 16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf( // copy-exempt
                QuillSettings.APP_THEME_SYSTEM, QuillSettings.APP_THEME_LIGHT, QuillSettings.APP_THEME_DARK,
            ).forEach {
                QuillChip(it, selected = settings.appTheme == it, onClick = { scope.launch { repo.setAppTheme(it) } })
            }
        }
        FieldLabel(copy(K.BUBBLE_RETURN_SETTING), Modifier.padding(top = 16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 5, 10, 30, 60, 0).forEach { minutes ->
                QuillChip(
                    if (minutes == 0) copy(K.BUBBLE_RETURN_NEVER) else copy(K.BUBBLE_RETURN_MINUTES, minutes),
                    selected = settings.bubbleReturnMinutes == minutes,
                    onClick = { scope.launch { repo.setBubbleReturnMinutes(minutes) } },
                )
            }
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
            QuillRow(title = copy(K.SETTINGS_ROW_PRIVACY), leadIcon = Icons.Rounded.Shield,
                onClick = { nav.navigate(Screen.Privacy) }, trailing = { RowChevron() })
        }
        SystemSettingsLinks()

    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.PreviewTile(
    bg: Color,
    label: String,
    labelColor: Color,
    bubbleSize: androidx.compose.ui.unit.Dp,
) {
    Box(
        Modifier.weight(1f).height(74.dp).clip(RoundedCornerShape(14.dp)).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, style = MaterialTheme.typography.labelSmall, color = labelColor,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        )
        QuillBubble(BubbleState.IDLE, Icons.Rounded.Mic, size = bubbleSize, darkTheme = bg == Color(0xFF15161C))
    }
}

// ============================================================================
// C9 — Advanced
// ============================================================================

@Composable
fun AdvancedScreen(nav: QuillNav) {
    val context = LocalContext.current
    val secretStore = remember { KeystoreSecretStore(context) }
    val repo = remember { SettingsRepository(context) }
    val historyRepo = remember { HistoryRepository(context) }
    val wordsStore = remember { CustomWordsStore(context) }
    val stylesStore = remember { WritingStyleStore(context) }
    val vocabStore = remember { com.tihor.quill.vocab.ContactsVocabularyStore(context) }
    val scope = rememberCoroutineScope()
    val settings by repo.settings.collectAsState(initial = QuillSettings())
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }
    var showResetConfirm by rememberSaveable { mutableStateOf(false) }

    DetailScaffold(copy(K.SETTINGS_ADVANCED_TITLE), nav) {
        Spacer(Modifier.height(8.dp))
        QuillListContainer {
            // Endpoint override (M5), request timeout, keep-alive + battery exemption (M11) all touch the
            // STT client or the foreground-service lifecycle — honest placeholders, never fake toggles.
            // Keep-alive/battery especially must NOT look functional (hostile-OEM survival isn't wired).
            ComingSoonRow(copy(K.SETTINGS_ADVANCED_ENDPOINT), copy(K.SETTINGS_ADVANCED_ENDPOINT_HELP))
            RowDivider()
            ComingSoonRow(copy(K.SETTINGS_ADVANCED_TIMEOUT), copy(K.SETTINGS_ADVANCED_TIMEOUT_SUB))
            RowDivider()
            // M11: real keep-alive (reboot + foreground recovery) and battery-exemption controls.
            SwitchRow(
                copy(K.SETTINGS_ADVANCED_KEEPALIVE), settings.bootRestore,
                { scope.launch { repo.setBootRestore(it) } }, copy(K.SETTINGS_ADVANCED_KEEPALIVE_SUB),
            )
            RowDivider()
            QuillRow(
                title = copy(K.SETTINGS_SURVIVAL_ROW),
                onClick = { nav.navigate(Screen.OemSurvival) }, trailing = { RowChevron() },
            )
        }

        SectionHeader(copy(K.SETTINGS_ADVANCED_RESET))
        QuillListContainer {
            QuillRow(
                leadIcon = Icons.Rounded.RestartAlt, leadVariant = LeadIconVariant.PLAIN,
                title = copy(K.SETTINGS_ADVANCED_RESET_SETTINGS),
                onClick = { showResetConfirm = true }, trailing = { RowChevron() },
            )
            RowDivider()
            QuillRow(
                leadIcon = Icons.Rounded.DeleteOutline, leadVariant = LeadIconVariant.ERROR,
                title = copy(K.SETTINGS_ADVANCED_CLEAR), titleColor = MaterialTheme.colorScheme.error,
                onClick = { showClearConfirm = true }, trailing = { RowChevron() },
            )
        }
    }

    if (showResetConfirm) {
        ConfirmDialog(
            icon = Icons.Rounded.RestartAlt,
            title = copy(K.SETTINGS_ADVANCED_RESET_CONFIRM_TITLE),
            body = copy(K.SETTINGS_ADVANCED_RESET_CONFIRM_BODY),
            confirmLabel = copy(K.SETTINGS_ADVANCED_RESET_CONFIRM_CTA),
            cancelLabel = copy(K.ACTION_CANCEL),
            onConfirm = { scope.launch { repo.resetToDefaults() }; showResetConfirm = false },
            onDismiss = { showResetConfirm = false },
        )
    }

    if (showClearConfirm) {
        ConfirmDialog(
            icon = Icons.Rounded.DeleteOutline,
            title = copy(K.SETTINGS_ADVANCED_CLEAR_CONFIRM_TITLE),
            body = copy(K.SETTINGS_ADVANCED_CLEAR_CONFIRM_BODY),
            confirmLabel = copy(K.SETTINGS_ADVANCED_CLEAR_CONFIRM_CTA),
            cancelLabel = copy(K.ACTION_CANCEL),
            // Match the label: wipe keys + settings (to defaults) + history + custom words + per-app
            // style overrides. Full wipe resets onboarding, so the next launch shows onboarding (the
            // key is gone anyway).
            onConfirm = {
                scope.launch { AppDataReset.clearAll(secretStore, repo, historyRepo, wordsStore, stylesStore, vocabStore) }
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
            com.tihor.quill.ui.components.Wordmark()
            Text(
                copy(K.SETTINGS_ABOUT_VERSION, com.tihor.quill.BuildConfig.VERSION_NAME, com.tihor.quill.BuildConfig.VERSION_CODE),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.quill.faint,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        QuillListContainer {
            // Real ACTION_VIEW links when the URLs are set; disabled "Coming soon" while still empty.
            LegalRow(copy(K.SETTINGS_ABOUT_PRIVACY_POLICY), LegalLinks.PRIVACY_URL)
            RowDivider()
            LegalRow(copy(K.SETTINGS_ABOUT_TERMS), LegalLinks.TERMS_URL)
            RowDivider()
            // No trivial intent target (no OSS-licenses screen / feedback address yet) — disabled-honest.
            ComingSoonRow(copy(K.SETTINGS_ABOUT_LICENSES))
            RowDivider()
            ComingSoonRow(copy(K.SETTINGS_ABOUT_FEEDBACK))
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


/** System settings shortcuts shared by the settings index and privacy screen. */
@Composable
private fun SystemSettingsLinks() {
    val context = LocalContext.current
    var refresh by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val mic = remember(refresh) {
        androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    val accessibility = remember(refresh) { AccessibilityUtil.isServiceEnabled(context) }
    val overlay = remember(refresh) { com.tihor.quill.overlay.OverlayPermission.isGranted(context) }
    val notifications = remember(refresh) { androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val battery = remember(refresh) { BatteryOptimization.isExempt(context) }
    fun open(intent: Intent) {
        try { context.startActivity(intent) }
        catch (_: android.content.ActivityNotFoundException) {
            context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:" + context.packageName)))
        }
    }
    SectionHeader(copy(K.SETTINGS_PRIVACY_PERMISSIONS))
    QuillListContainer {
        QuillRow(title = copy(K.SETTINGS_PRIVACY_ACCESSIBILITY), leadIcon = Icons.Rounded.AccessibilityNew,
            onClick = { open(AccessibilityUtil.disclosureIntent(context)) },
            trailing = { RowValueChevron(copy(if (accessibility) K.VALUE_ON else K.VALUE_OFF)) })
        RowDivider()
        QuillRow(title = copy(K.OVERLAY_TITLE), leadIcon = Icons.Rounded.PictureInPicture,
            onClick = { open(com.tihor.quill.overlay.OverlayPermission.settingsIntent(context)) },
            trailing = { RowValueChevron(copy(if (overlay) K.VALUE_GRANTED else K.VALUE_OFF)) })
        RowDivider()
        QuillRow(title = copy(K.SETTINGS_PRIVACY_MIC), leadIcon = Icons.Rounded.Mic,
            onClick = { open(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:" + context.packageName))) },
            trailing = { RowValueChevron(copy(if (mic) K.VALUE_GRANTED else K.VALUE_OFF)) })
        RowDivider()
        QuillRow(title = copy(K.SETTINGS_NOTIFICATIONS), leadIcon = Icons.Rounded.Info,
            onClick = { open(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)) },
            trailing = { RowValueChevron(copy(if (notifications) K.VALUE_ON else K.VALUE_OFF)) })
        RowDivider()
        QuillRow(title = copy(K.SETTINGS_ADVANCED_BATTERY), leadIcon = Icons.Rounded.Settings,
            onClick = { open(BatteryOptimization.settingsIntent()) },
            trailing = { RowValueChevron(copy(if (battery) K.VALUE_GRANTED else K.VALUE_CHECK)) })
    }
}
