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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tihor.quill.overlay.OverlayPermission
import com.tihor.quill.overlay.OverlayService
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

    val overlaySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // Re-check after returning from the system overlay-permission screen.
        hasOverlay = OverlayPermission.isGranted(context)
    }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
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
            Button(onClick = { OverlayService.start(context) }) {
                Text("Show bubble")
            }
            Button(onClick = { OverlayService.stop(context) }) {
                Text("Hide bubble")
            }
            Text(
                "Tap the bubble to cycle states · drag to move (snaps to edge) · long-press to dismiss",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
