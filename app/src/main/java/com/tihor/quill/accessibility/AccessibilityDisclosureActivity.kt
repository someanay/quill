package com.tihor.quill.accessibility

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tihor.quill.R
import com.tihor.quill.ui.theme.QuillTheme

/**
 * Prominent disclosure + affirmative consent shown **before** the user is sent to enable the
 * accessibility service (Play policy / spec §8). All copy here is binary-only compliance text
 * (`strings.xml`) — never soft-coded or OTA-overridable (CLAUDE.md hard rule).
 */
class AccessibilityDisclosureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QuillTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    DisclosureContent(
                        modifier = Modifier.padding(padding),
                        onAccept = {
                            startActivity(AccessibilityUtil.settingsIntent())
                            finish()
                        },
                        onDecline = { finish() },
                    )
                }
            }
        }
    }
}

@Composable
private fun DisclosureContent(
    modifier: Modifier = Modifier,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.a11y_disclosure_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            stringResource(R.string.a11y_disclosure_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onAccept) {
            Text(stringResource(R.string.a11y_disclosure_accept))
        }
        TextButton(onClick = onDecline) {
            Text(stringResource(R.string.a11y_disclosure_decline))
        }
    }
}
