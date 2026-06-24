package com.tihor.quill.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tihor.quill.settings.QuillSettings
import com.tihor.quill.settings.SettingsRepository
import com.tihor.quill.settings.SttProvider
import com.tihor.quill.ui.components.SectionHeader
import com.tihor.quill.ui.theme.QuillTheme
import com.tihor.quill.ui.theme.quill
import kotlinx.coroutines.launch

/** Settings (spec §3.1 SettingsActivity, M7): provider / model / language / private mode / cap. */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { QuillTheme { SettingsScreen(onBack = { finish() }) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()
    val settings by repo.settings.collectAsState(initial = QuillSettings())

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionHeader("Speech & model")
            SettingsCard {
                NavRow(
                    icon = Icons.Rounded.Key,
                    title = "Speech-to-text key",
                    value = "Groq",
                    onClick = { context.startActivity(Intent(context, ApiKeyActivity::class.java)) },
                )
            }

            SectionHeader("Provider")
            SettingsCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Provider", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Only the v1-shipping provider(s) are selectable (Groq); others slot in later.
                        SttProvider.selectable().forEach { p ->
                            FilterChip(
                                selected = settings.provider == p,
                                onClick = { scope.launch { repo.setProvider(p) } },
                                label = { Text(p.displayName) },
                            )
                        }
                    }
                    Text(
                        "Model: ${settings.sttModel}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.quill.muted,
                    )
                    OutlinedTextField(
                        value = settings.languageHint.orEmpty(),
                        onValueChange = { scope.launch { repo.setLanguageHint(it) } },
                        singleLine = true,
                        label = { Text("Language hint (optional — blank = auto-detect)") },
                        keyboardOptions = KeyboardOptions.Default,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            SectionHeader("Audio & recording")
            SettingsCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Max recording length", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(60, 120, 300).forEach { secs ->
                            FilterChip(
                                selected = settings.sessionCapSeconds == secs,
                                onClick = { scope.launch { repo.setSessionCapSeconds(secs) } },
                                label = { Text(if (secs < 60) "${secs}s" else "${secs / 60} min") },
                            )
                        }
                    }
                }
            }

            SectionHeader("Privacy")
            SettingsCard {
                ToggleRow(
                    title = "Private mode",
                    subtitle = "Don't save any dictation history",
                    checked = settings.privateMode,
                    onCheckedChange = { scope.launch { repo.setPrivateMode(it) } },
                )
                ToggleRow(
                    title = "Clean up transcripts",
                    subtitle = "Remove filler, fix punctuation (P1)",
                    checked = settings.cleanupEnabled,
                    onCheckedChange = { scope.launch { repo.setCleanupEnabled(it) } },
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) { content() }
}

@Composable
private fun NavRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.quill.brandInk, modifier = Modifier.size(20.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.quill.muted)
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.quill.faint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.quill.muted)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
