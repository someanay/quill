package com.tihor.quill.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tihor.quill.copy.CopyKeys as K
import com.tihor.quill.copy.copy
import com.tihor.quill.secrets.SecretStore
import com.tihor.quill.ui.components.*
import com.tihor.quill.ui.theme.quill

@Composable
internal fun AddKeyStep(onBack: () -> Unit, secretStore: SecretStore, onVerified: () -> Unit) {
    Scaffold(topBar = { QuillTopBar(title = {}, onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
            .padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(copy(K.KEY_STEP), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.quill.muted)
            Text(copy(K.KEY_TITLE), style = MaterialTheme.typography.headlineMedium)
            Text(copy(K.KEY_BODY), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.quill.muted)
            Spacer(Modifier.height(8.dp))
            ProviderKeyAccordion(secretStore, onSaved = onVerified)
            HelpText(copy(K.PRIVACY_PROMISE))
        }
    }
}
