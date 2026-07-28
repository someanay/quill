package com.tihor.quill.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.tihor.quill.copy.CopyKeys as K
import com.tihor.quill.copy.copy
import com.tihor.quill.secrets.KeystoreSecretStore
import com.tihor.quill.secrets.SttKeyStore
import com.tihor.quill.settings.QuillSettings
import com.tihor.quill.settings.SettingsRepository
import com.tihor.quill.settings.SttProvider
import com.tihor.quill.style.LaunchableApp
import com.tihor.quill.style.LaunchableApps
import com.tihor.quill.style.WritingStyle
import com.tihor.quill.style.WritingStyleResolver
import com.tihor.quill.style.WritingStyleStore
import com.tihor.quill.ui.app.QuillNav
import com.tihor.quill.ui.components.Banner
import com.tihor.quill.ui.components.HelpText
import com.tihor.quill.ui.components.PillKind
import com.tihor.quill.ui.components.QuillCard
import com.tihor.quill.ui.components.QuillListContainer
import com.tihor.quill.ui.components.QuillRow
import com.tihor.quill.ui.components.QuillTopBar
import com.tihor.quill.ui.components.QuillTopBarTitle
import com.tihor.quill.ui.components.RowDivider
import com.tihor.quill.ui.components.SectionHeader
import com.tihor.quill.ui.theme.quill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The user-facing name of a [WritingStyle], from the Copy Catalog. Shared with the Settings index row. */
@Composable
fun styleName(style: WritingStyle): String = copy(
    when (style) {
        WritingStyle.FORMAL -> K.SETTINGS_STYLE_FORMAL
        WritingStyle.NEUTRAL -> K.SETTINGS_STYLE_NEUTRAL
        WritingStyle.CASUAL -> K.SETTINGS_STYLE_CASUAL
    },
)

@Composable
private fun styleSubtitle(style: WritingStyle): String = copy(
    when (style) {
        WritingStyle.FORMAL -> K.SETTINGS_STYLE_FORMAL_SUB
        WritingStyle.NEUTRAL -> K.SETTINGS_STYLE_NEUTRAL_SUB
        WritingStyle.CASUAL -> K.SETTINGS_STYLE_CASUAL_SUB
    },
)

/**
 * Per-app writing style (scope doc §8): a global default picker plus a searchable list of every
 * **launchable** app, each assignable to a style or resettable to its default.
 *
 * Two things this screen deliberately does *not* do:
 *  - **It never turns the LLM cleanup pass on.** Styling rides on that pass, but silently opting a user
 *    into sending every dictation to a third-party model (plus the latency) from a setting that sounds
 *    cosmetic would be dishonest (§7). Instead the availability notice says what is missing and the
 *    user goes and turns it on themselves.
 *  - **It never claims style rewrites text.** [K.SETTINGS_STYLE_WORDS_PROMISE] states the §4 boundary
 *    on the screen, because that boundary is the product promise, not an implementation detail.
 *
 * The app list is read off the [android.content.pm.PackageManager] once, off the main thread; it comes
 * from the manifest's `<queries>` LAUNCHER filter, never `QUERY_ALL_PACKAGES` (§8.1).
 */
@Composable
fun WritingStyleScreen(nav: QuillNav) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val styleStore = remember { WritingStyleStore(context) }
    val scope = rememberCoroutineScope()
    val settings by repo.settings.collectAsState(initial = QuillSettings())
    val overrides by styleStore.overrides.collectAsState(initial = emptyMap())

    var apps by remember { mutableStateOf<List<LaunchableApp>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var assigning by remember { mutableStateOf<LaunchableApp?>(null) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { LaunchableApps.query(context) }
    }

    // §7: styling only fires when cleanup is ON *and* a Groq key exists (cleanup is Groq-only). Show
    // whichever is missing rather than letting the user configure something that silently never runs.
    val groqKeySaved = remember { SttKeyStore(KeystoreSecretStore(context)).has(SttProvider.GROQ) }
    val unavailableKey = when {
        !settings.cleanupEnabled -> K.SETTINGS_STYLE_NEEDS_CLEANUP
        !groqKeySaved && settings.provider != SttProvider.GROQ -> K.SETTINGS_STYLE_NEEDS_GROQ
        else -> null
    }

    val filtered = apps.orEmpty().filter {
        query.isBlank() || it.label.contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            QuillTopBar(title = { QuillTopBarTitle(copy(K.SETTINGS_STYLE_TITLE)) }, onBack = nav::back)
        },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            item {
                Text(
                    copy(K.SETTINGS_STYLE_INTRO),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.quill.muted,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            if (unavailableKey != null) {
                item {
                    Banner(text = copy(unavailableKey), icon = Icons.Rounded.Info, kind = PillKind.INFO)
                }
            }
            item {
                SectionHeader(copy(K.SETTINGS_STYLE_DEFAULT_SECTION))
                QuillListContainer {
                    WritingStyle.entries.forEachIndexed { i, style ->
                        if (i > 0) RowDivider()
                        StyleChoiceRow(
                            style = style,
                            selected = settings.writingStyleDefault == style,
                            onClick = { scope.launch { repo.setWritingStyleDefault(style) } },
                        )
                    }
                }
                HelpText(copy(K.SETTINGS_STYLE_WORDS_PROMISE))
            }
            item {
                SectionHeader(copy(K.SETTINGS_STYLE_PER_APP_SECTION))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(copy(K.SETTINGS_STYLE_SEARCH_HINT)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )
            }
            when {
                apps == null -> item { EmptyNote(copy(K.SETTINGS_STYLE_LOADING)) }
                filtered.isEmpty() -> item { EmptyNote(copy(K.SETTINGS_STYLE_NO_RESULTS)) }
                else -> items(filtered, key = { it.packageName }) { app ->
                    val resolved = WritingStyleResolver.resolve(
                        packageName = app.packageName,
                        overrides = overrides,
                        globalDefault = settings.writingStyleDefault,
                    )
                    QuillRow(
                        title = app.label,
                        subtitle = app.packageName,
                        onClick = { assigning = app },
                        trailing = {
                            Text(
                                styleName(resolved),
                                style = MaterialTheme.typography.bodyMedium,
                                // An explicit override reads as chosen; an inherited default reads muted.
                                color = if (app.packageName in overrides) MaterialTheme.quill.brandInk
                                else MaterialTheme.quill.muted,
                            )
                        },
                    )
                }
            }
            item { HelpText(copy(K.SETTINGS_STYLE_HELP)) }
        }
    }

    assigning?.let { app ->
        AssignStyleDialog(
            app = app,
            override = overrides[app.packageName],
            // What "use the default" would land on for this app: its curated default, else the global
            // one. Computed with the SAME resolver the dictation path uses, with this app's override
            // removed — so the dialog can never promise a fallback that resolution wouldn't deliver.
            defaultStyle = WritingStyleResolver.resolve(
                packageName = app.packageName,
                overrides = emptyMap(),
                globalDefault = settings.writingStyleDefault,
            ),
            onPick = { style ->
                scope.launch { styleStore.setOverride(app.packageName, style) }
                assigning = null
            },
            onReset = {
                scope.launch { styleStore.clearOverride(app.packageName) }
                assigning = null
            },
            onDismiss = { assigning = null },
        )
    }
}

@Composable
private fun StyleChoiceRow(style: WritingStyle, selected: Boolean, onClick: () -> Unit) {
    QuillRow(
        title = styleName(style),
        subtitle = styleSubtitle(style),
        onClick = onClick,
        trailing = {
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = copy(K.A11Y_SELECTED), tint = MaterialTheme.quill.brandInk)
            }
        },
    )
}

@Composable
private fun EmptyNote(text: String) {
    QuillListContainer { QuillRow(title = text, titleColor = MaterialTheme.quill.muted, trailing = {}) }
}

/**
 * Assign a style to one app, or reset it. "Use the default" is a distinct choice from picking the
 * style that default happens to resolve to today: it **deletes** the override, so the app keeps
 * tracking its curated default (and any future improvement to it) instead of freezing on a value.
 */
@Composable
private fun AssignStyleDialog(
    app: LaunchableApp,
    override: WritingStyle?,
    defaultStyle: WritingStyle,
    onPick: (WritingStyle) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        QuillCard {
            Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    copy(K.SETTINGS_STYLE_ASSIGN_TITLE),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.quill.muted,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Text(
                    app.label, // copy-exempt: the app's own name, from the package manager
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                )
                WritingStyle.entries.forEach { style ->
                    StyleChoiceRow(style = style, selected = override == style, onClick = { onPick(style) })
                }
                RowDivider()
                QuillRow(
                    title = copy(K.SETTINGS_STYLE_USE_DEFAULT),
                    // Name the style the reset lands on, and whether it's Quill's guess for this app
                    // or just the global default — otherwise "default" is a word with no content.
                    subtitle = styleName(defaultStyle),
                    titleColor = if (override == null) MaterialTheme.quill.brandInk
                    else MaterialTheme.colorScheme.onSurface,
                    onClick = onReset,
                    trailing = {
                        if (override == null) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = copy(K.A11Y_SELECTED),
                                tint = MaterialTheme.quill.brandInk,
                            )
                        }
                    },
                )
            }
        }
    }
}
