package com.booktracker.ui.stats

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.booktracker.data.BackupData
import com.booktracker.data.BackupFormatException
import com.booktracker.data.BackupManager
import com.booktracker.data.ReadingSettings
import com.booktracker.data.backupDestination
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

sealed interface BackupDialog {
    data object Format : BackupDialog
    data class WrongFile(val fileName: String) : BackupDialog
    data class TooNew(val fileName: String) : BackupDialog
    data class Confirm(val source: Uri, val fileName: String, val backup: BackupData) : BackupDialog
}

enum class BackupNotice { Restored, ExportFailed, ImportFailed }

data class ExportedBackup(val fileName: String, val destination: String)

data class BackupUiState(
    val lastSavedAt: Long? = null,
    /** When the restored backup was saved. Replaced by the next export or an Undo. */
    val restoredFrom: Long? = null,
    val working: Boolean = false,
    val dialog: BackupDialog? = null,
    val notice: BackupNotice? = null,
    /** Set when an export finishes; the Done screen reads it. */
    val exported: ExportedBackup? = null,
    /** True until the host has moved to the Done screen, so it moves only once. */
    val showExported: Boolean = false,
)

class BackupViewModel(
    private val backup: BackupManager,
    private val settings: ReadingSettings,
) : ViewModel() {
    private val local = MutableStateFlow(BackupUiState())

    val state: StateFlow<BackupUiState> = combine(local, settings.lastBackupAt) { state, savedAt ->
        state.copy(lastSavedAt = savedAt)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BackupUiState())

    fun export(target: Uri) = work(BackupNotice.ExportFailed) {
        backup.export(target)
        settings.setLastBackupAt(System.currentTimeMillis())
        val done = ExportedBackup(backup.fileName(target), backupDestination(target.authority))
        local.update { it.copy(restoredFrom = null, exported = done, showExported = true) }
    }

    fun exportShown() = local.update { it.copy(showExported = false) }

    fun askForFile() = local.update { it.copy(dialog = BackupDialog.Format) }

    fun dismissDialog() = local.update { it.copy(dialog = null) }

    fun inspect(source: Uri) {
        local.update { it.copy(dialog = null) }
        work(BackupNotice.ImportFailed) {
            val name = backup.fileName(source)
            val dialog = try {
                BackupDialog.Confirm(source, name, backup.preview(source))
            } catch (e: BackupFormatException) {
                if (e.tooNew) BackupDialog.TooNew(name) else BackupDialog.WrongFile(name)
            }
            local.update { it.copy(dialog = dialog) }
        }
    }

    fun confirmImport(confirm: BackupDialog.Confirm) {
        local.update { it.copy(dialog = null) }
        work(BackupNotice.ImportFailed) {
            backup.restore(confirm.source)
            local.update { it.copy(restoredFrom = confirm.backup.savedAt, notice = BackupNotice.Restored) }
        }
    }

    fun undoImport() = work(BackupNotice.ImportFailed) {
        backup.undoRestore()
        local.update { it.copy(restoredFrom = null) }
    }

    fun noticeShown() = local.update { it.copy(notice = null) }

    private fun work(failure: BackupNotice, block: suspend () -> Unit) {
        viewModelScope.launch {
            local.update { it.copy(working = true) }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(notice = failure) }
            } finally {
                local.update { it.copy(working = false) }
            }
        }
    }
}
