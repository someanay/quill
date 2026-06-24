package com.tihor.quill.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tihor.quill.secrets.KeystoreSecretStore
import com.tihor.quill.secrets.SecretStore
import com.tihor.quill.settings.SttProvider
import com.tihor.quill.stt.KeyValidation
import com.tihor.quill.stt.KeyValidator
import com.tihor.quill.ui.components.Banner
import com.tihor.quill.ui.components.PillKind
import com.tihor.quill.ui.components.StatusPill
import com.tihor.quill.ui.theme.QuillTheme
import com.tihor.quill.ui.theme.quill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** API-key entry (spec §3.1 ApiKeyActivity, M7). Stores into the Keystore-backed [SecretStore]. */
class ApiKeyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { QuillTheme { ApiKeyScreen(onBack = { finish() }) } }
    }
}

private enum class KeyStatus { UNSET, SAVED, CHECKING, VALID, REJECTED, UNVERIFIED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiKeyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val secretStore = remember { KeystoreSecretStore(context) }
    val validator = remember { KeyValidator(context.cacheDir) }
    val scope = rememberCoroutineScope()

    var key by remember { mutableStateOf(secretStore.get(SecretStore.ALIAS_STT_KEY).orEmpty()) }
    var reveal by remember { mutableStateOf(false) }
    var status by remember {
        mutableStateOf(if (secretStore.contains(SecretStore.ALIAS_STT_KEY)) KeyStatus.SAVED else KeyStatus.UNSET)
    }
    var message by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Speech-to-text key") }) },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Groq API key", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Your key is encrypted on this device (Android Keystore) and sent only to your provider.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.quill.muted,
                    )
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it; status = KeyStatus.UNSET; message = null },
                        singleLine = true,
                        label = { Text("API key") },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                        visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { reveal = !reveal }) {
                                Icon(
                                    if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (reveal) "Hide key" else "Show key",
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    when (status) {
                        KeyStatus.VALID -> StatusPill("Valid", PillKind.OK)
                        KeyStatus.SAVED -> StatusPill("Saved", PillKind.INFO)
                        KeyStatus.CHECKING -> Text("Checking your key…", color = MaterialTheme.quill.muted)
                        KeyStatus.REJECTED -> StatusPill("Rejected", PillKind.ERROR)
                        KeyStatus.UNVERIFIED -> StatusPill("Saved (unverified)", PillKind.INFO)
                        KeyStatus.UNSET -> Unit
                    }
                    message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.quill.muted) }

                    Button(
                        onClick = {
                            secretStore.put(SecretStore.ALIAS_STT_KEY, key.trim())
                            status = KeyStatus.CHECKING
                            message = null
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    validator.validate(SttProvider.GROQ, key.trim())
                                }
                                when (result) {
                                    KeyValidation.Valid -> { status = KeyStatus.VALID; message = null }
                                    KeyValidation.Rejected -> {
                                        status = KeyStatus.REJECTED
                                        message = "That key was rejected (401). Check you copied it fully."
                                    }
                                    is KeyValidation.Unverified -> {
                                        status = KeyStatus.UNVERIFIED
                                        message = "Saved, but couldn't verify (${result.reason})."
                                    }
                                }
                            }
                        },
                        enabled = key.isNotBlank() && status != KeyStatus.CHECKING,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Save & verify") }

                    if (secretStore.contains(SecretStore.ALIAS_STT_KEY)) {
                        TextButton(onClick = {
                            secretStore.clear(SecretStore.ALIAS_STT_KEY)
                            key = ""; status = KeyStatus.UNSET; message = null
                        }) { Text("Remove key") }
                    }
                }
            }

            Banner(
                text = "Quill stores no accounts and runs no server. Audio goes only to the provider whose key you supply.",
                icon = Icons.Rounded.Lock,
                kind = PillKind.INFO,
            )
        }
    }
}
