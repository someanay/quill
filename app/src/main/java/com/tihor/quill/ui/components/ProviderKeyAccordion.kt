package com.tihor.quill.ui.components

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tihor.quill.copy.CopyKeys as K
import com.tihor.quill.copy.copy
import com.tihor.quill.models.*
import com.tihor.quill.secrets.*
import com.tihor.quill.settings.*
import com.tihor.quill.stt.*
import com.tihor.quill.ui.theme.quill
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val keySetupProviders = listOf(SttProvider.GEMINI, SttProvider.GROQ, SttProvider.OPENROUTER)

@Composable
fun ProviderKeyAccordion(
    secretStore: SecretStore,
    onSaved: () -> Unit,
    initialProvider: SttProvider = SttProvider.GEMINI,
    activateSpeechProvider: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { context.getSharedPreferences("key_setup", Context.MODE_PRIVATE) }
    var expandedId by rememberSaveable {
        mutableStateOf(preferences.getString("expanded", initialProvider.id).orEmpty())
    }
    val draftStore = remember(secretStore) { KeySetupDrafts(secretStore) }
    val drafts = remember(draftStore) { mutableStateMapOf<String, String>().apply {
        keySetupProviders.forEach { put(it.id, draftStore.read(it)) }
    } }
    val keys = remember(secretStore) { SttKeyStore(secretStore) }
    val repository = remember { SettingsRepository(context) }
    var saving by remember { mutableStateOf(false) }
    var storageError by remember { mutableStateOf(false) }
    val clipboard = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    var canPaste by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(clipboard, lifecycle) {
        fun refresh() {
            // Metadata only. The clipboard payload is read exclusively by the Paste action.
            canPaste = clipboard.hasPrimaryClip() && clipboard.primaryClipDescription?.let {
                it.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) || it.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
            } == true
        }
        val listener = ClipboardManager.OnPrimaryClipChangedListener { refresh() }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        clipboard.addPrimaryClipChangedListener(listener)
        lifecycle.addObserver(observer)
        refresh()
        onDispose { clipboard.removePrimaryClipChangedListener(listener); lifecycle.removeObserver(observer) }
    }
    Text(copy(K.KEY_CHOOSE), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(12.dp))
    QuillCard {
        keySetupProviders.forEachIndexed { index, provider ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.quill.lineStrong)
            val expanded = expandedId == provider.id
            val angle by animateFloatAsState(if (expanded) 180f else 0f, label = "keyAccordion") // copy-exempt
            val description = copy(if (expanded) K.KEY_EXPANDED else K.KEY_COLLAPSED)
            Row(Modifier.fillMaxWidth().semantics { stateDescription = description }
                .clickable(enabled = !saving, role = Role.Button) {
                    expandedId = if (expanded) "" else provider.id
                    preferences.edit().putString("expanded", expandedId).commit()
                }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(provider.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(copy(if (provider == SttProvider.GEMINI) K.KEY_EASY else K.KEY_MORE_STEPS),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.quill.muted)
                }
                StatusPill(copy(if (provider == SttProvider.OPENROUTER) K.KEY_FREE_OPTIONS else K.KEY_CONSOLE_FREE), PillKind.OK)
                Icon(Icons.Rounded.ExpandMore, null, Modifier.padding(start = 8.dp).rotate(angle))
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProviderKeyInstructions(provider)
                    HelpText(copy(if (provider == SttProvider.OPENROUTER) K.KEY_ROUTER_NOTE else K.KEY_FREE_NOTE))
                    val value = drafts[provider.id].orEmpty()
                    val candidate = value.trim()
                    val plausible = KeySetupValidation.plausible(provider, candidate)
                    var result by remember(provider, candidate) { mutableStateOf<KeyValidation?>(null) }
                    var checking by remember(provider, candidate) { mutableStateOf(false) }
                    var retry by remember { mutableIntStateOf(0) }
                    var reveal by remember { mutableStateOf(false) }
                    val verifiedMessage = copy(K.KEY_VERIFIED)
                    LaunchedEffect(provider, candidate, retry) {
                        if (!plausible) return@LaunchedEffect
                        delay(650)
                        checking = true
                        try {
                            result = KeySetupValidation.validate(provider, candidate)
                            if (result == KeyValidation.Valid) Toast.makeText(context, verifiedMessage, Toast.LENGTH_SHORT).show()
                        } finally { checking = false }
                    }
                    val update: (String) -> Unit = { text ->
                        if (text.length <= 4096) {
                            drafts[provider.id] = text
                            storageError = runCatching { draftStore.write(provider, text) }.isFailure
                        }
                    }
                    if (value.isEmpty() && keys.has(provider)) {
                        TextButton(onClick = { keys.get(provider)?.let(update) }, enabled = !saving) {
                            Text(copy(K.KEY_USE_SAVED))
                        }
                    }
                    OutlinedTextField(value = value, onValueChange = update, enabled = !saving,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(copy(K.KEY_FIELD_LABEL)) }, placeholder = { Text(provider.keyMask) },
                        leadingIcon = { Icon(Icons.Rounded.Key, null) },
                        visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                        isError = result == KeyValidation.Rejected,
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(enabled = canPaste && !saving, onClick = {
                                clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()?.let(update)
                            }) { Text(copy(K.KEY_PASTE)) }
                            IconButton(onClick = { reveal = !reveal }) {
                                Icon(if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    copy(if (reveal) K.A11Y_HIDE_KEY else K.A11Y_SHOW_KEY))
                            }
                            }
                        })
                        if (result == KeyValidation.Valid) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.quill.ok)
                                Spacer(Modifier.width(6.dp))
                                Text(verifiedMessage, color = MaterialTheme.quill.ok, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    if (value.isNotEmpty() && !plausible) HelpText(copy(K.KEY_FORMAT_HELP, provider.keyMask))
                    if (checking) { LinearProgressIndicator(Modifier.fillMaxWidth()); HelpText(copy(K.KEY_VERIFYING)) }
                    if (result == KeyValidation.Rejected) HelpText(copy(K.KEY_ERROR_INVALID), bad = true)
                    if (result is KeyValidation.Unverified) {
                        HelpText(copy(K.KEY_UNVERIFIED), bad = true)
                        TextButton(onClick = { retry++ }, enabled = !checking) { Text(copy(K.KEY_RETRY)) }
                    }
                    QuillPrimaryButton(copy(K.KEY_SAVE_PROVIDER, provider.displayName), enabled = result == KeyValidation.Valid && !saving,
                        onClick = {
                            saving = true
                            scope.launch {
                                try {
                                    val models = try { if (activateSpeechProvider) ModelCatalog.fetch(provider, ModelUse.DICTATION, { candidate }) else emptyList() }
                                        catch (cancel: CancellationException) { throw cancel }
                                        catch (_: Exception) { emptyList() }
                                    keys.add(provider, candidate)
                                    if (activateSpeechProvider) {
                                        repository.setProvider(provider)
                                        repository.setSttModel(FreeModelPolicy.preferred(models, provider.defaultModel, ModelUse.DICTATION))
                                    }
                                    draftStore.clear(provider)
                                    drafts[provider.id] = ""
                                    storageError = false
                                    onSaved()
                                } catch (cancel: CancellationException) { throw cancel }
                                catch (_: Exception) { storageError = true }
                                finally { saving = false }
                            }
                        })
                    if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
    }
    if (storageError) HelpText(copy(K.KEY_STORAGE_ERROR), bad = true)
}

@Composable
fun ProviderKeyInstructions(provider: SttProvider) {
    val context = LocalContext.current
    var read by rememberSaveable(provider.id) { mutableStateOf(false) }
    provider.keyGuideStepsKey?.let { key ->
        copy(key).lines().filter { it.isNotBlank() }.forEachIndexed { index, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text((index + 1).toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.quill.brandInk)
                Text(step.replace(Regex("^\\d+[.)]\\s*"), ""), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    Row(Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { read = !read }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = read, onCheckedChange = { read = it })
        Text(copy(K.KEY_READ_STEPS), style = MaterialTheme.typography.labelMedium)
    }
    val browserError = copy(K.KEY_BROWSER_ERROR)
    TextButton(enabled = read, onClick = {
        val url = when (provider) {
            SttProvider.GEMINI -> "https://aistudio.google.com/api-keys"
            SttProvider.GROQ -> "https://console.groq.com/home"
            else -> "https://openrouter.ai/sign-up"
        }
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(context, browserError, Toast.LENGTH_LONG).show() }
    }) { Text(copy(K.KEY_GUIDE_OPEN, provider.displayName)) }
}
