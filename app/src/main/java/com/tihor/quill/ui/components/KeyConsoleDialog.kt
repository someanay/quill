package com.tihor.quill.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.tihor.quill.copy.CopyKeys as K
import com.tihor.quill.copy.copy
import com.tihor.quill.settings.SttProvider
import com.tihor.quill.ui.theme.quill

/**
 * Provider help for Gemini, Groq and OpenRouter. Uses the same numbered instructions and reading
 * acknowledgement as inline key setup, with room to scroll on smaller screens.
 */
@Composable
fun KeyConsoleDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    // The provider whose guide is open (null = the provider list).
    var guideFor by remember { mutableStateOf<SttProvider?>(null) }
    val open: (String) -> Unit = { url ->
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        onDismiss()
    }
    Dialog(onDismissRequest = onDismiss) {
        QuillCard {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                val guide = guideFor
                if (guide == null) {
                    ProviderList(
                        onPick = { provider, url ->
                            // No guide copy (defensive) → keep the old direct-open behaviour.
                            if (provider.keyGuideStepsKey == null) open(url) else guideFor = provider
                        },
                        onDismiss = onDismiss,
                    )
                } else {
                    KeyGuide(
                        provider = guide,
                        onOpen = { guide.keyConsoleUrl?.let(open) },
                        onBack = { guideFor = null },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderList(onPick: (SttProvider, String) -> Unit, onDismiss: () -> Unit) {
    LeadIcon(Icons.Rounded.Key, LeadIconVariant.BRAND)
    Spacer(Modifier.height(12.dp))
    Text(copy(K.KEY_WHERE), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(6.dp))
    Text(
        copy(K.KEY_CONSOLE_BODY),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.quill.muted,
    )
    Spacer(Modifier.height(14.dp))
    QuillListContainer {
        val consoles = keySetupProviders.mapNotNull { p -> p.keyConsoleUrl?.let { p to it } }
        consoles.forEachIndexed { i, (provider, url) ->
            if (i > 0) RowDivider()
            QuillRow(
                title = provider.displayName, // copy-exempt (proper nouns)
                onClick = { onPick(provider, url) },
                trailing = {
                    if (provider in keySetupProviders) {
                        StatusPill(copy(if (provider == SttProvider.OPENROUTER) K.KEY_FREE_OPTIONS else K.KEY_CONSOLE_FREE), PillKind.OK)
                        Spacer(Modifier.width(8.dp))
                    }
                    RowChevron()
                },
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
        Text(copy(K.ACTION_CANCEL))
    }
}

/** Per-provider steps for getting a key, with the console link as the primary action. */
@Composable
private fun KeyGuide(provider: SttProvider, onOpen: () -> Unit, onBack: () -> Unit) {
    LeadIcon(Icons.Rounded.Key, LeadIconVariant.BRAND)
    Spacer(Modifier.height(12.dp))
    Text(copy(K.KEY_GUIDE_TITLE, provider.displayName), style = MaterialTheme.typography.titleMedium)
    if (provider.keyConsoleFree) {
        Spacer(Modifier.height(8.dp))
        StatusPill(copy(K.KEY_CONSOLE_FREE), PillKind.OK)
    }
    Spacer(Modifier.height(10.dp))
    ProviderKeyInstructions(provider)
    Spacer(Modifier.height(6.dp))
    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
        Text(copy(K.ACTION_BACK))
    }
}
