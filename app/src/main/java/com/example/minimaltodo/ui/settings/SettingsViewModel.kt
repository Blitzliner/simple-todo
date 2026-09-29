package com.example.minimaltodo.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.minimaltodo.data.backup.ImportMode
import com.example.minimaltodo.data.local.SortMode
import com.example.minimaltodo.data.local.TodoEntity
import com.example.minimaltodo.data.preferences.AppSettings
import com.example.minimaltodo.data.preferences.ExportFormat
import com.example.minimaltodo.data.preferences.SettingsRepository
import com.example.minimaltodo.data.preferences.ThemeMode
import com.example.minimaltodo.data.repository.TodoRepository
import com.example.minimaltodo.util.DocumentIo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SettingsMessage {
    data object ExportDone : SettingsMessage
    data object ExportFailed : SettingsMessage
    data class ImportDone(val count: Int) : SettingsMessage
    data object ImportFailed : SettingsMessage
}

/** Eine gelesene, noch nicht angewendete Backup-Datei. */
data class PendingImport(val items: List<TodoEntity>)

class SettingsViewModel(
    private val repository: TodoRepository,
    private val settingsRepository: SettingsRepository,
    private val documents: DocumentIo,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _message = MutableStateFlow<SettingsMessage?>(null)
    val message: StateFlow<SettingsMessage?> = _message.asStateFlow()

    private val _pendingImport = MutableStateFlow<PendingImport?>(null)
    val pendingImport: StateFlow<PendingImport?> = _pendingImport.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    fun setDefaultSort(mode: SortMode) = viewModelScope.launch { settingsRepository.setDefaultSort(mode) }
    fun setHideCompleted(hide: Boolean) = viewModelScope.launch { settingsRepository.setHideCompleted(hide) }
    fun setExportFormat(format: ExportFormat) = viewModelScope.launch { settingsRepository.setExportFormat(format) }

    fun export(uri: Uri, format: ExportFormat) = work {
        try {
            val sort = settingsRepository.settings.first().defaultSort
            val text = withContext(Dispatchers.Default) {
                when (format) {
                    ExportFormat.TXT -> repository.exportTxt(sort)
                    ExportFormat.MARKDOWN -> repository.exportMarkdown(sort)
                    ExportFormat.JSON -> repository.exportJson()
                }
            }
            documents.writeText(uri, text)
            _message.value = SettingsMessage.ExportDone
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _message.value = SettingsMessage.ExportFailed
        }
    }

    /** Liest und prüft eine Backup-Datei; anschließend fragt die UI nach dem Modus. */
    fun readImport(uri: Uri) = work {
        try {
            val text = documents.readText(uri)
            val items = withContext(Dispatchers.Default) { repository.parseBackup(text) }
            _pendingImport.value = PendingImport(items)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Auch OutOfMemory-nahe Fälle, beschädigtes JSON etc. → verständliche Meldung
            _message.value = SettingsMessage.ImportFailed
        }
    }

    fun confirmImport(mode: ImportMode) {
        val pending = _pendingImport.value ?: return
        _pendingImport.value = null
        work {
            try {
                val count = repository.import(pending.items, mode)
                _message.value = SettingsMessage.ImportDone(count)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _message.value = SettingsMessage.ImportFailed
            }
        }
    }

    fun cancelImport() {
        _pendingImport.value = null
    }

    fun messageShown() {
        _message.value = null
    }

    private fun work(block: suspend () -> Unit) = viewModelScope.launch {
        _busy.value = true
        try {
            block()
        } finally {
            _busy.value = false
        }
    }
}
