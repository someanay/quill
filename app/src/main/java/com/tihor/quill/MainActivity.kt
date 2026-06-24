package com.tihor.quill

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tihor.quill.accessibility.AccessibilityDisclosureActivity
import com.tihor.quill.accessibility.AccessibilityUtil
import com.tihor.quill.overlay.OverlayPermission
import com.tihor.quill.overlay.OverlayService
import com.tihor.quill.secrets.KeystoreSecretStore
import com.tihor.quill.secrets.SecretStore
import com.tihor.quill.ui.ApiKeyActivity
import com.tihor.quill.ui.OnboardingActivity
import com.tihor.quill.ui.SettingsActivity
import com.tihor.quill.ui.components.Banner
import com.tihor.quill.ui.components.PillKind
import com.tihor.quill.ui.components.Wordmark
import com.tihor.quill.ui.theme.QuillTheme
import com.tihor.quill.ui.theme.quill

/**
 * Home / dev control surface (M7). The full onboarding flow lands at M8; this gives a wordmark home
 * with permission + key status and quick access to the key and settings screens.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { QuillTheme { HomeScreen() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen() {
    val context = LocalContext.current
    val secretStore = remember { KeystoreSecretStore(context) }

    var hasOverlay by remember { mutableStateOf(OverlayPermission.isGranted(context)) }
    var a11yEnabled by remember { mutableStateOf(AccessibilityUtil.isServiceEnabled(context)) }
    var hasKey by remember { mutableStateOf(secretStore.contains(SecretStore.ALIAS_STT_KEY)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlay = OverlayPermission.isGranted(context)
                a11yEnabled = AccessibilityUtil.isServiceEnabled(context)
                hasKey = secretStore.contains(SecretStore.ALIAS_STT_KEY)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { hasOverlay = OverlayPermission.isGranted(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Wordmark() },
                actions = {
                    IconButton(onClick = { context.startActivity(Intent(context, SettingsActivity::class.java)) }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
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
            Text("Dictate into any app", style = MaterialTheme.typography.headlineSmall)

            Button(
                onClick = { context.startActivity(Intent(context, OnboardingActivity::class.java)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Set up Quill (walk onboarding)") }
            Text(
                "Status — overlay: ${onOff(hasOverlay)} · accessibility: ${onOff(a11yEnabled)} · key: ${if (hasKey) "set" else "not set"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.quill.muted,
            )

            if (!hasKey) {
                Banner(
                    text = "Add your speech-to-text API key to start dictating.",
                    icon = Icons.Rounded.Key,
                    kind = PillKind.INFO,
                )
            }

            FilledTonalButton(
                onClick = { context.startActivity(Intent(context, ApiKeyActivity::class.java)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (hasKey) "Manage API key" else "Add API key") }

            if (!hasOverlay) {
                Button(
                    onClick = { overlayLauncher.launch(OverlayPermission.settingsIntent(context)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Grant \"display over other apps\"") }
            } else {
                Button(onClick = { OverlayService.start(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Show bubble")
                }
                OutlinedButton(onClick = { OverlayService.stop(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Hide bubble")
                }
            }

            OutlinedButton(
                onClick = { context.startActivity(Intent(context, AccessibilityDisclosureActivity::class.java)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (a11yEnabled) "Accessibility: enabled" else "Set up accessibility") }

            Text(
                "Tap the bubble to record · tap again to stop → transcribe → insert · long-press to dismiss",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.quill.faint,
            )
        }
    }
}

private fun onOff(b: Boolean) = if (b) "on" else "off"
