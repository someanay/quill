package com.tihor.quill.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tihor.quill.copy.CopyKeys as K
import com.tihor.quill.copy.copy
import com.tihor.quill.secrets.SecretStore
import com.tihor.quill.settings.SttProvider
import com.tihor.quill.ui.components.*
import com.tihor.quill.ui.theme.quill

@Composable
internal fun AddKeyStep(
    onBack: () -> Unit,
    secretStore: SecretStore,
    onVerified: () -> Unit,
    onSkip: () -> Unit,
) {
    var showOtherProviders by rememberSaveable { mutableStateOf(false) }
    Scaffold(topBar = { QuillTopBar(title = {}, onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(copy(K.KEY_STEP), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.quill.muted)
            Text(copy(K.KEY_TITLE), style = MaterialTheme.typography.headlineMedium)
            Text(copy(K.KEY_BODY), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.quill.muted)
            Spacer(Modifier.height(4.dp))
            ProviderKeySetup(secretStore, provider = SttProvider.GROQ, onSaved = onVerified)
            TextButton(onClick = { showOtherProviders = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(copy(K.KEY_OTHER_PROVIDERS), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    if (showOtherProviders) {
        AlertDialog(
            onDismissRequest = { showOtherProviders = false },
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { showOtherProviders = false }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = copy(K.A11Y_BACK))
                    }
                    Text(copy(K.KEY_OTHER_PROVIDERS_TITLE))
                }
            },
            text = { Text(copy(K.KEY_OTHER_PROVIDERS_BODY)) },
            confirmButton = {
                QuillPrimaryButton(copy(K.KEY_OTHER_PROVIDERS_DONE), onClick = {
                    showOtherProviders = false
                    onSkip()
                })
            },
        )
    }
}
