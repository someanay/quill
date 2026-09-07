package com.tihor.quill.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tihor.quill.copy.CopyKeys as K
import com.tihor.quill.copy.copy
import com.tihor.quill.models.*
import com.tihor.quill.secrets.KeystoreSecretStore
import com.tihor.quill.secrets.SttKeyStore
import com.tihor.quill.settings.SttProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Fetch on entry/resume and on demand. An unavailable catalog never erases a saved model. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDropdown(
    provider: SttProvider,
    selected: String,
    onSelect: (String) -> Unit,
    customBaseUrl: String = "",
    use: ModelUse = ModelUse.DICTATION,
) {
    val context = LocalContext.current
    val keys = remember { SttKeyStore(KeystoreSecretStore(context)) }
    val fallback = remember(provider, use) {
        if (use == ModelUse.CLEANUP) listOf(com.tihor.quill.cleanup.CleanupProviders.defaultModel(provider))
            .filter { it.isNotBlank() }.map { ModelOption(it) }
        else provider.models.filter { provider != SttProvider.OPENROUTER || "whisper" in it.id || "transcribe" in it.id }
            .map { ModelOption(it.id) }
    }
    val cache = remember(provider, customBaseUrl, use) { ModelCatalogCache(context.cacheDir, "${provider.id}|$customBaseUrl|$use") }
    var models by remember(cache) { mutableStateOf(cache.read().ifEmpty { ModelCatalog.rank(fallback, use) }) }
    var expanded by remember(provider, use) { mutableStateOf(false) }
    var query by remember(provider, use) { mutableStateOf("") }
    var loading by remember(provider, use) { mutableStateOf(false) }
    var refreshed by remember(provider, customBaseUrl, use) { mutableStateOf(false) }
    var failed by remember(provider, use) { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var requestedEndpoint by remember(provider) { mutableStateOf<String?>(null) }
    var manual by remember(provider) { mutableStateOf(false) }
    var manualId by remember(selected) { mutableStateOf(selected) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(provider, customBaseUrl, use, refresh, requestedEndpoint) {
        // Custom URLs are edited in place. Only an explicit refresh may send a key to one.
        if (provider == SttProvider.CUSTOM && requestedEndpoint != customBaseUrl) return@LaunchedEffect
        loading = true
        failed = false
        try {
            delay(400) // Do not request a custom endpoint on every keystroke.
            models = ModelCatalog.fetch(provider, use, { keys.get(provider) }, customBaseUrl)
            runCatching { cache.write(models) }
            refreshed = true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            failed = true
        } finally {
            loading = false
        }
    }
    val choices = remember(models, selected) {
        if (selected.isNotBlank() && models.none { it.id == selected }) models + ModelOption(selected) else models
    }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(copy(K.SETTINGS_SPEECH_MODEL)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = true),
            modifier = Modifier.exposedDropdownSize().heightIn(max = 380.dp)) {
            OutlinedTextField(query, { query = it }, singleLine = true,
                placeholder = { Text(copy(K.MODELS_SEARCH)) }, modifier = Modifier.padding(8.dp).fillMaxWidth())
            val filtered = choices.filter { it.id.contains(query, true) || it.name.contains(query, true) }
            if (filtered.isEmpty()) Text(copy(K.MODELS_EMPTY), modifier = Modifier.padding(16.dp))
            filtered.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model.id) },
                    onClick = { onSelect(model.id); expanded = false; query = "" },
                    trailingIcon = { if (model.id == selected) Icon(Icons.Rounded.Check, contentDescription = null) },
                )
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(copy(when { provider == SttProvider.CUSTOM && requestedEndpoint != customBaseUrl -> K.MODELS_CUSTOM_REFRESH; loading -> K.MODELS_LOADING; failed -> K.MODELS_FALLBACK; refreshed -> K.MODELS_FRESH; else -> K.MODELS_FALLBACK }),
            style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f).padding(top = 12.dp, end = 8.dp))
        IconButton(onClick = { requestedEndpoint = customBaseUrl; refresh++ }, enabled = !loading) {
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Icon(Icons.Rounded.Refresh, contentDescription = copy(K.MODELS_REFRESH))
        }
    }
    HelpText(copy(K.MODELS_RANKING))
    if (provider != SttProvider.CUSTOM) {
        TextButton(onClick = { manual = !manual }) { Text(copy(K.MODELS_MANUAL)) }
        if (manual) {
            OutlinedTextField(manualId, { manualId = it }, singleLine = true,
                label = { Text(copy(K.MODELS_MANUAL)) }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { onSelect(manualId.trim()); manual = false }, enabled = manualId.isNotBlank()) {
                Text(copy(K.MODELS_SAVE))
            }
        }
    }
}
