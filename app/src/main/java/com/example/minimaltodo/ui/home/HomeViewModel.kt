package com.example.minimaltodo.ui.home

import androidx.lifecycle.viewModelScope
import com.example.minimaltodo.data.local.SortMode
import com.example.minimaltodo.data.local.TodoEntity
import com.example.minimaltodo.data.preferences.SettingsRepository
import com.example.minimaltodo.data.repository.TodoRepository
import com.example.minimaltodo.ui.common.ItemActionsViewModel
import com.example.minimaltodo.util.TreeBuilder
import com.example.minimaltodo.util.TreeIndex
import com.example.minimaltodo.util.TreeRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val loading: Boolean = true,
    val rows: List<TreeRow> = emptyList(),
    /** Geöffnete Gruppe (null = Gesamtliste). */
    val root: TodoEntity? = null,
    /** Die geöffnete Gruppe existiert nicht mehr (z. B. gelöscht). */
    val rootMissing: Boolean = false,
    val sortMode: SortMode = SortMode.OPEN_FIRST,
    val hideCompleted: Boolean = false,
    /** Es gibt keinerlei Einträge auf dieser Ebene (auch keine ausgeblendeten). */
    val isEmpty: Boolean = false,
)

/** Hauptliste als Baum. [rootId] != null zeigt nur den Inhalt einer Gruppe. */
class HomeViewModel(
    repository: TodoRepository,
    settingsRepository: SettingsRepository,
    val rootId: String? = null,
) : ItemActionsViewModel(repository, settingsRepository) {

    val uiState: StateFlow<HomeUiState> =
        combine(repository.allItems, settingsRepository.settings) { items, settings ->
            val index = TreeIndex(items)
            val root = rootId?.let { index.byId[it] }
            val rows = TreeBuilder.build(index, rootId, settings.defaultSort, settings.hideCompleted)
            HomeUiState(
                loading = false,
                rows = rows,
                root = root,
                rootMissing = rootId != null && root == null,
                sortMode = root?.childSort ?: settings.defaultSort,
                hideCompleted = settings.hideCompleted,
                isEmpty = index.childrenOf(rootId).isEmpty(),
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Sortierung der aktuellen Ebene: Root → Standard-Sortierung, Gruppe → eigene Sortierung. */
    fun setSort(mode: SortMode) = launchSafe {
        if (rootId == null) settingsRepository.setDefaultSort(mode) else repository.setChildSort(rootId, mode)
    }

    fun setHideCompleted(hide: Boolean) = launchSafe { settingsRepository.setHideCompleted(hide) }
}
