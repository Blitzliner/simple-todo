package com.example.minimaltodo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.minimaltodo.data.local.TodoType
import com.example.minimaltodo.data.preferences.AppSettings
import com.example.minimaltodo.data.preferences.SettingsRepository
import com.example.minimaltodo.data.repository.TodoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** App-weiter Zustand: Einstellungen (Theme, Onboarding) und per Share Sheet empfangener Text. */
class MainViewModel(
    private val repository: TodoRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /** null, solange die Einstellungen noch geladen werden. */
    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _sharedText = MutableStateFlow<String?>(null)
    val sharedText: StateFlow<String?> = _sharedText.asStateFlow()

    private val _message = MutableStateFlow<SharedMessage?>(null)
    val message: StateFlow<SharedMessage?> = _message.asStateFlow()

    enum class SharedMessage { SAVED, ERROR }

    fun onTextShared(text: String?) {
        val cleaned = text?.trim()?.take(MAX_SHARED_LENGTH)
        if (!cleaned.isNullOrEmpty()) _sharedText.value = cleaned
    }

    fun dismissShared() {
        _sharedText.value = null
    }

    /** Speichert den geteilten Text als Aufgabe (erste Zeile = Titel, Rest = Notiz) oder als Text. */
    fun saveShared(text: String, type: TodoType) {
        _sharedText.value = null
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                if (type == TodoType.TEXT) {
                    repository.create(TodoType.TEXT, "", null, content = trimmed)
                } else {
                    val lines = trimmed.lines()
                    repository.create(type, lines.first(), null, lines.drop(1).joinToString("\n").trim())
                }
                _message.value = SharedMessage.SAVED
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _message.value = SharedMessage.ERROR
            }
        }
    }

    fun messageShown() {
        _message.value = null
    }

    fun completeOnboarding() {
        viewModelScope.launch { settingsRepository.setOnboardingDone(true) }
    }

    private companion object {
        const val MAX_SHARED_LENGTH = 20_000
    }
}
