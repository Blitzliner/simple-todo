package com.example.minimaltodo.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.minimaltodo.data.local.SortMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreSettingsRepository(context: Context) : SettingsRepository {

    private val store = context.applicationContext.dataStore

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val DEFAULT_SORT = stringPreferencesKey("defaultSort")
        val HIDE_COMPLETED = booleanPreferencesKey("hideCompleted")
        val EXPORT_FORMAT = stringPreferencesKey("exportFormat")
        val ONBOARDING_DONE = booleanPreferencesKey("onboardingDone")
    }

    override val settings: Flow<AppSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            val defaults = AppSettings()
            AppSettings(
                themeMode = enumOr(p[Keys.THEME], defaults.themeMode),
                defaultSort = enumOr(p[Keys.DEFAULT_SORT], defaults.defaultSort),
                hideCompleted = p[Keys.HIDE_COMPLETED] ?: defaults.hideCompleted,
                exportFormat = enumOr(p[Keys.EXPORT_FORMAT], defaults.exportFormat),
                onboardingDone = p[Keys.ONBOARDING_DONE] ?: defaults.onboardingDone,
            )
        }

    override suspend fun setThemeMode(mode: ThemeMode) = put { it[Keys.THEME] = mode.name }
    override suspend fun setDefaultSort(mode: SortMode) = put { it[Keys.DEFAULT_SORT] = mode.name }
    override suspend fun setHideCompleted(hide: Boolean) = put { it[Keys.HIDE_COMPLETED] = hide }
    override suspend fun setExportFormat(format: ExportFormat) = put { it[Keys.EXPORT_FORMAT] = format.name }
    override suspend fun setOnboardingDone(done: Boolean) = put { it[Keys.ONBOARDING_DONE] = done }

    private suspend fun put(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        try {
            store.edit { block(it) }
        } catch (_: IOException) {
            // Einstellungen sind unkritisch – ein Schreibfehler darf die App nicht beenden.
        }
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}
