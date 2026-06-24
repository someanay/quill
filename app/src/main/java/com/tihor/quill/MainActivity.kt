package com.tihor.quill

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tihor.quill.accessibility.AccessibilityDisclosureActivity
import com.tihor.quill.accessibility.AccessibilityUtil
import com.tihor.quill.overlay.OverlayPermission
import com.tihor.quill.overlay.OverlayService
import com.tihor.quill.secrets.TempApiKeyHolder
import com.tihor.quill.ui.theme.QuillTheme

/**
 * M0 launcher + M1 bubble controls. A throwaway dev surface: request the overlay permission and
 * show/hide the floating bubble. The real onboarding / settings UI arrives at M8.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QuillTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BubbleControls(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
private fun BubbleControls(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var hasOverlay by remember { mutableStateOf(OverlayPermission.isGranted(context)) }
    var a11yEnabled by remember { mutableStateOf(AccessibilityUtil.isServiceEnabled(context)) }

    // Re-check permission state whenever we return to the foreground (user may have toggled either
    // the overlay or the accessibility service in system settings).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlay = OverlayPermission.isGranted(context)
                a11yEnabled = AccessibilityUtil.isServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val overlaySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        hasOverlay = OverlayPermission.isGranted(context)
    }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text("Quill", style = MaterialTheme.typography.headlineMedium)

        Text(
            if (hasOverlay) "Overlay permission: granted" else "Overlay permission: not granted",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!hasOverlay) {
            Button(onClick = { overlaySettingsLauncher.launch(OverlayPermission.settingsIntent(context)) }) {
                Text("Grant \"display over other apps\"")
            }
        } else {
            Button(onClick = { OverlayService.start(context) }) { Text("Show bubble") }
            Button(onClick = { OverlayService.stop(context) }) { Text("Hide bubble") }
        }

        Text(
            if (a11yEnabled) "Accessibility service: enabled" else "Accessibility service: off",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = {
            context.startActivity(
                android.content.Intent(context, AccessibilityDisclosureActivity::class.java),
            )
        }) {
            Text(if (a11yEnabled) "Review accessibility setup" else "Set up accessibility")
        }

        var apiKey by remember { mutableStateOf(TempApiKeyHolder.key.orEmpty()) }
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it; TempApiKeyHolder.key = it.trim() },
            label = { Text("Groq API key (temporary — encrypted at M7)") },
            singleLine = true,
        )
        Text(
            if (TempApiKeyHolder.isSet()) "API key: set" else "API key: not set",
            style = MaterialTheme.typography.bodySmall,
        )

        Text(
            "Tap the bubble to record · tap again to stop → transcribe → insert · drag to move · long-press to dismiss",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
