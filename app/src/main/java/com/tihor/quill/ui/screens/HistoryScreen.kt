package com.tihor.quill.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tihor.quill.ui.app.QuillBottomNav
import com.tihor.quill.ui.app.QuillNav
import com.tihor.quill.ui.app.Screen
import com.tihor.quill.ui.components.LeadIconVariant
import com.tihor.quill.ui.components.PermissionIllustration
import com.tihor.quill.ui.components.QuillCard
import com.tihor.quill.ui.components.QuillListContainer
import com.tihor.quill.ui.components.QuillRow
import com.tihor.quill.ui.components.QuillTonalButton
import com.tihor.quill.ui.components.QuillTopBar
import com.tihor.quill.ui.components.QuillTopBarTitle
import com.tihor.quill.ui.components.RowDivider
import com.tihor.quill.ui.components.SectionHeader
import com.tihor.quill.ui.theme.quill

/** One saved dictation. Placeholder model until the Room store lands (M9). */
data class HistoryEntry(val text: String, val app: String, val time: String, val group: String)

private fun sampleHistory(): List<HistoryEntry> = listOf(
    HistoryEntry("Can you send me the report by Friday?", "WhatsApp", "4 min ago", "Today"),
    HistoryEntry("Running 10 late, traffic on the SV road", "Messages", "1 hr ago", "Today"),
    HistoryEntry("Add Drizzle migration for the audit table…", "Slack", "2 hr ago", "Today"),
    HistoryEntry("Reschedule the 9E delivery to next week", "Gmail", "6:12 pm", "Yesterday"),
)

/** Dictation history (board D). Opt-in, on-device; copy/share/delete per item + clear all. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(nav: QuillNav) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    // In-memory placeholder; replaced by the Room-backed store at M9.
    val entries: SnapshotStateList<HistoryEntry> = remember { sampleHistory().toMutableStateList() }
    var selected by remember { mutableStateOf<HistoryEntry?>(null) }
    var showClearAll by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            QuillTopBar(
                title = { QuillTopBarTitle("History") },
                actions = {
                    if (entries.isNotEmpty()) {
                        IconButton(onClick = { showClearAll = true }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Clear all")
                        }
                    }
                },
            )
        },
        bottomBar = { QuillBottomNav(Screen.History, nav::switchTab) },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        if (entries.isEmpty()) {
            HistoryEmpty(Modifier.padding(padding), onTry = { nav.switchTab(Screen.Home) })
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            ) {
                entries.groupBy { it.group }.forEach { (group, items) ->
                    SectionHeader(group)
                    QuillListContainer {
                        items.forEachIndexed { i, entry ->
                            if (i > 0) RowDivider()
                            QuillRow(
                                title = entry.text,
                                subtitle = "${entry.app} · ${entry.time}",
                                onClick = { selected = entry },
                                trailing = {
                                    IconButton(onClick = {
                                        clipboard.setText(AnnotatedString(entry.text))
                                    }) {
                                        Icon(
                                            Icons.Rounded.ContentCopy, contentDescription = "Copy",
                                            tint = MaterialTheme.quill.faint, modifier = Modifier.size(20.dp),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    selected?.let { entry ->
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { selected = null }, sheetState = sheetState) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 18.dp)) {
                QuillCard {
                    Text(
                        entry.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.quill.text2,
                        modifier = Modifier.padding(14.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                QuillListContainer {
                    QuillRow(
                        leadIcon = Icons.Rounded.ContentCopy, leadVariant = LeadIconVariant.PLAIN,
                        title = "Copy text",
                        onClick = { clipboard.setText(AnnotatedString(entry.text)); selected = null },
                    )
                    RowDivider()
                    QuillRow(
                        leadIcon = Icons.Rounded.Share, leadVariant = LeadIconVariant.PLAIN, title = "Share",
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"; putExtra(Intent.EXTRA_TEXT, entry.text)
                            }
                            context.startActivity(Intent.createChooser(send, null))
                            selected = null
                        },
                    )
                    RowDivider()
                    QuillRow(
                        leadIcon = Icons.Rounded.DeleteOutline, leadVariant = LeadIconVariant.ERROR,
                        title = "Delete", titleColor = MaterialTheme.colorScheme.error,
                        onClick = { entries.remove(entry); selected = null },
                    )
                }
            }
        }
    }

    if (showClearAll) {
        val count = entries.size
        ConfirmDialog(
            icon = Icons.Rounded.DeleteOutline,
            title = "Delete all history?",
            body = "This removes all $count saved dictations from this device. It can't be undone.",
            confirmLabel = "Delete all",
            onConfirm = { entries.clear(); showClearAll = false },
            onDismiss = { showClearAll = false },
        )
    }
}

@Composable
private fun HistoryEmpty(modifier: Modifier, onTry: () -> Unit) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PermissionIllustration(Icons.Rounded.Schedule)
            Spacer(Modifier.height(20.dp))
            Text("No dictations yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Once you start dictating, your transcripts show up here — saved on this phone only.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.quill.text2,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            QuillTonalButton("Try a dictation", onClick = onTry)
        }
    }
}
