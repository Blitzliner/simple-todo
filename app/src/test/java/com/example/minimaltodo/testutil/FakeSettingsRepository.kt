package com.example.minimaltodo.testutil

import com.example.minimaltodo.data.local.SortMode
import com.example.minimaltodo.data.preferences.AppSettings
import com.example.minimaltodo.data.preferences.ExportFormat
import com.example.minimaltodo.data.preferences.SettingsRepository
import com.example.minimaltodo.data.preferences.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    override val settings = MutableStateFlow(initial)
    override suspend fun setThemeMode(mode: ThemeMode) = settings.update { it.copy(themeMode = mode) }
    override suspend fun setDefaultSort(mode: SortMode) = settings.update { it.copy(defaultSort = mode) }
    override suspend fun setHideCompleted(hide: Boolean) = settings.update { it.copy(hideCompleted = hide) }
    override suspend fun setExportFormat(format: ExportFormat) = settings.update { it.copy(exportFormat = format) }
    override suspend fun setOnboardingDone(done: Boolean) = settings.update { it.copy(onboardingDone = done) }
}
