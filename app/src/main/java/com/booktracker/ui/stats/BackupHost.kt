package com.booktracker.ui.stats

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.booktracker.data.BackupData
import com.booktracker.ui.library.AnimatedTick
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val BackupButtonShape = RoundedCornerShape(12.dp)
private val BackupDateFormat = DateTimeFormatter.ofPattern("d MMM yyyy")
private const val ANY_FILE = "*/*"

private fun formatDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(BackupDateFormat)

/**
 * Holds the file pickers, dialogs and snackbar outside the Stats list, so they survive the card
 * scrolling away. Every file is offered in the picker: a filter could grey out a real backup on
 * providers that report the wrong type, and the wrong-file dialog explains anything else.
 */
@Composable
fun BackupHost(
    model: BackupViewModel,
    onExported: () -> Unit,
    onDataReplaced: () -> Unit,
    content: @Composable (backupCard: @Composable () -> Unit) -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val latestOnExported by rememberUpdatedState(onExported)
    val latestOnDataReplaced by rememberUpdatedState(onDataReplaced)
    val snackbar = remember { SnackbarHostState() }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> if (uri != null) model.export(uri) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) model.inspect(uri) }

    LaunchedEffect(state.showExported) {
        if (state.showExported) {
            model.exportShown()
            latestOnExported()
        }
    }
    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        // Cleared only afterwards: clearing it restarts this effect, which would cancel the snackbar.
        try {
            when (notice) {
                BackupNotice.ExportFailed -> snackbar.showSnackbar("Couldn't save the backup. Try again.")
                BackupNotice.ImportFailed -> snackbar.showSnackbar("Couldn't import the backup. Your data is unchanged.")
                BackupNotice.Restored -> {
                    latestOnDataReplaced()
                    val result = snackbar.showSnackbar(
                        "Backup restored",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) model.undoImport()
                }
            }
        } finally {
            model.noticeShown()
        }
    }

    Box(Modifier.fillMaxSize()) {
        content {
            BackupCard(
                state = state,
                onExport = { exportLauncher.launch("FolioFlow-backup-${LocalDate.now()}.zip") },
                onImport = model::askForFile,
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    when (val dialog = state.dialog) {
        null -> Unit
        BackupDialog.Format -> FormatDialog(
            onChoose = {
                model.dismissDialog()
                importLauncher.launch(arrayOf(ANY_FILE))
            },
            onDismiss = model::dismissDialog,
        )
        is BackupDialog.WrongFile -> RetryDialog(
            title = "This file can't be imported",
            message = "${dialog.fileName} isn't a Folio Flow backup. Choose a .zip file made with Export backup.",
            onRetry = { importLauncher.launch(arrayOf(ANY_FILE)) },
            onDismiss = model::dismissDialog,
        )
        is BackupDialog.TooNew -> RetryDialog(
            title = "Update Folio Flow first",
            message = "${dialog.fileName} was made by a newer version of the app. " +
                "Update Folio Flow, then import it again.",
            onRetry = { importLauncher.launch(arrayOf(ANY_FILE)) },
            onDismiss = model::dismissDialog,
        )
        is BackupDialog.Confirm -> ConfirmDialog(
            fileName = dialog.fileName,
            backup = dialog.backup,
            onReplace = { model.confirmImport(dialog) },
            onDismiss = model::dismissDialog,
        )
    }
}

@Composable
private fun BackupCard(state: BackupUiState, onExport: () -> Unit, onImport: () -> Unit) {
    val status = when {
        state.working -> "Working on your backup"
        state.restoredFrom != null -> "Restored from the ${formatDate(state.restoredFrom)} backup"
        state.lastSavedAt != null -> "Last saved ${formatDate(state.lastSavedAt)}"
        else -> "Not backed up yet"
    }
    PaperCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Backup", style = MaterialTheme.typography.titleMedium)
            Text(
                "Books, sessions, notes, covers and goals, saved as one file you keep.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                status,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Button(
                onClick = onExport,
                enabled = !state.working,
                shape = BackupButtonShape,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(48.dp),
            ) { Text("Export backup") }
            OutlinedButton(
                onClick = onImport,
                enabled = !state.working,
                shape = BackupButtonShape,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Import backup") }
        }
    }
}

@Composable
private fun FormatDialog(onChoose: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import a backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Choose a .zip backup that was made with Export backup in Folio Flow.")
                FileBox {
                    Text(
                        "The file looks like this",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("FolioFlow-backup-${LocalDate.now()}.zip", style = MaterialTheme.typography.labelLarge)
                }
                Text("Other files, such as PDF, Excel, CSV or photos, can't be imported.")
            }
        },
        confirmButton = { TextButton(onClick = onChoose) { Text("Choose file") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RetryDialog(title: String, message: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message)
                Text("Nothing in the app has changed.")
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onRetry()
                },
            ) { Text("Choose another") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ConfirmDialog(fileName: String, backup: BackupData, onReplace: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Replace your data?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FileBox {
                    Text(fileName, style = MaterialTheme.typography.labelLarge)
                    BackupFact("Saved ${formatDate(backup.savedAt)}")
                    BackupFact(
                        "${counted(backup.books.size, "book")}, ${counted(backup.sessions.size, "session")}, " +
                            counted(backup.notes.size, "note"),
                    )
                }
                Text("Everything now in the app is replaced by this backup. You can undo this right after.")
            }
        },
        confirmButton = {
            TextButton(onClick = onReplace) { Text("Replace", color = MaterialTheme.colorScheme.tertiary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun counted(count: Int, noun: String) = if (count == 1) "1 $noun" else "$count ${noun}s"

@Composable
private fun BackupFact(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun FileBox(content: @Composable () -> Unit) {
    Surface(
        shape = BackupButtonShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
    }
}

/** Stays until Done, unlike the Add a book tick, because this one says where the file went. */
@Composable
fun BackupExportedScreen(exported: ExportedBackup, onDone: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedTick(Modifier.padding(bottom = 16.dp).size(112.dp))
            Text("Backup saved", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                exported.fileName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                "Saved to ${exported.destination}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onDone,
                shape = BackupButtonShape,
                modifier = Modifier.padding(top = 24.dp).width(160.dp).height(48.dp),
            ) { Text("Done") }
        }
    }
}
