package com.tihor.quill.ui.screens

import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tihor.quill.copy.CopyKeys as K
import com.tihor.quill.copy.copy
import com.tihor.quill.history.HistoryEntry
import com.tihor.quill.history.HistoryRepository
import com.tihor.quill.settings.QuillSettings
import com.tihor.quill.settings.SettingsRepository
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
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** Dictation history (board D, M9). Opt-in, on-device; copy/share/delete per item + clear all. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(nav: QuillNav) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val historyRepo = remember { HistoryRepository(context) }
    val settingsRepo = remember { SettingsRepository(context) }
    val settings by settingsRepo.settings.collectAsState(initial = QuillSettings())
    val scope = rememberCoroutineScope()

    var entries by remember { mutableStateOf<List<HistoryEntry>>(emptyList()) }
    var selected by remember { mutableStateOf<HistoryEntry?>(null) }
    var showClearAll by remember { mutableStateOf(false) }

    // Drop anything past the retention window first, so the list reflects the cutoff even when no
    // new dictation has happened since (M9). Re-runs if the user changes the window in Settings.
    LaunchedEffect(settings.historyRetentionMinutes) {
        historyRepo.prune(settings.historyRetentionMinutes, System.currentTimeMillis())
        entries = historyRepo.all()
    }

    Scaffold(
        topBar = {
            QuillTopBar(
                title = { QuillTopBarTitle(copy(K.HISTORY_TITLE)) },
                actions = {
                    if (entries.isNotEmpty()) {
                        IconButton(onClick = { showClearAll = true }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = copy(K.HISTORY_CLEAR_ALL))
                        }
                    }
                },
            )
        },
        bottomBar = { QuillBottomNav(Screen.History, nav::switchTab) },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        when {
            entries.isNotEmpty() -> HistoryList(
                modifier = Modifier.padding(padding),
                entries = entries,
                onCopy = { clipboard.setText(AnnotatedString(it.text)) },
                onOpen = { selected = it },
            )
            !settings.historyEnabled -> HistoryEmpty(
                Modifier.padding(padding),
                title = copy(K.HISTORY_EMPTY_OFF_TITLE),
                body = copy(K.HISTORY_EMPTY_OFF_BODY),
                cta = copy(K.HISTORY_EMPTY_OFF_CTA),
                onCta = { nav.navigate(Screen.Privacy) },
            )
            else -> HistoryEmpty(
                Modifier.padding(padding),
                title = copy(K.HISTORY_EMPTY_ON_TITLE),
                body = copy(K.HISTORY_EMPTY_ON_BODY),
                cta = copy(K.HISTORY_EMPTY_ON_CTA),
                onCta = { nav.switchTab(Screen.Home) },
            )
        }
    }

    selected?.let { entry ->
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { selected = null }, sheetState = sheetState) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).padding(bottom = 18.dp)) {
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
                        title = copy(K.HISTORY_SHEET_COPY),
                        onClick = { clipboard.setText(AnnotatedString(entry.text)); selected = null },
                    )
                    RowDivider()
                    QuillRow(
                        leadIcon = Icons.Rounded.Share, leadVariant = LeadIconVariant.PLAIN,
                        title = copy(K.HISTORY_SHEET_SHARE),
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
                        title = copy(K.HISTORY_SHEET_DELETE), titleColor = MaterialTheme.colorScheme.error,
                        onClick = {
                            val id = entry.id
                            selected = null
                            scope.launch { historyRepo.delete(id); entries = historyRepo.all() }
                        },
                    )
                }
            }
        }
    }

    if (showClearAll) {
        ConfirmDialog(
            icon = Icons.Rounded.DeleteOutline,
            title = copy(K.HISTORY_CLEAR_TITLE),
            body = copy(K.HISTORY_CLEAR_BODY, entries.size),
            confirmLabel = copy(K.HISTORY_CLEAR_CONFIRM),
            cancelLabel = copy(K.ACTION_CANCEL),
            onConfirm = { showClearAll = false; scope.launch { historyRepo.clearAll(); entries = historyRepo.all() } },
            onDismiss = { showClearAll = false },
        )
    }
}

@Composable
private fun HistoryList(
    modifier: Modifier,
    entries: List<HistoryEntry>,
    onCopy: (HistoryEntry) -> Unit,
    onOpen: (HistoryEntry) -> Unit,
) {
    val now = remember(entries) { System.currentTimeMillis() }
    val today = copy(K.HISTORY_GROUP_TODAY)
    val yesterday = copy(K.HISTORY_GROUP_YESTERDAY)
    val grouped = remember(entries, today, yesterday) {
        entries.groupBy { dayLabel(it.createdAt, now, today, yesterday) }.toList()
    }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
    ) {
        grouped.forEach { (label, items) ->
            SectionHeader(label)
            QuillListContainer {
                items.forEachIndexed { i, entry ->
                    if (i > 0) RowDivider()
                    QuillRow(
                        title = entry.text,
                        subtitle = subtitle(entry, now),
                        onClick = { onOpen(entry) },
                        trailing = {
                            IconButton(onClick = { onCopy(entry) }) {
                                Icon(
                                    Icons.Rounded.ContentCopy, contentDescription = null,
                                    tint = MaterialTheme.quill.faint, modifier = Modifier.padding(0.dp),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryEmpty(modifier: Modifier, title: String, body: String, cta: String, onCta: () -> Unit) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PermissionIllustration(Icons.Rounded.Schedule)
            Spacer(Modifier.height(20.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.quill.text2, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            QuillTonalButton(cta, onClick = onCta)
        }
    }
}

private fun dayLabel(createdAt: Long, now: Long, today: String, yesterday: String): String {
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(createdAt).atZone(zone).toLocalDate()
    val nowDay = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return when (day) {
        nowDay -> today
        nowDay.minusDays(1) -> yesterday
        else -> day.format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))
    }
}

private fun subtitle(entry: HistoryEntry, now: Long): String {
    val rel = DateUtils.getRelativeTimeSpanString(entry.createdAt, now, DateUtils.MINUTE_IN_MILLIS).toString()
    val app = entry.appLabel ?: entry.packageName
    return if (app.isNullOrBlank()) rel else "$app · $rel"
}
