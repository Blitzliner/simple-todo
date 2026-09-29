package com.example.minimaltodo.data.preferences

import com.example.minimaltodo.data.local.SortMode
import kotlinx.coroutines.flow.Flow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ExportFormat(val extension: String, val mimeType: String) {
    TXT("txt", "text/plain"),
    MARKDOWN("md", "text/markdown"),
    JSON("json", "application/json"),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Sortierung der Root-Ebene und aller Gruppen ohne eigene Sortierung. */
    val defaultSort: SortMode = SortMode.OPEN_FIRST,
    val hideCompleted: Boolean = false,
    val exportFormat: ExportFormat = ExportFormat.JSON,
    val onboardingDone: Boolean = false,
)

/** Einstellungen (keine Todo-Daten!). */
interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDefaultSort(mode: SortMode)
    suspend fun setHideCompleted(hide: Boolean)
    suspend fun setExportFormat(format: ExportFormat)
    suspend fun setOnboardingDone(done: Boolean)
}
