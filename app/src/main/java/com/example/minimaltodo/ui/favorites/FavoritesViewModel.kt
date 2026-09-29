package com.example.minimaltodo.ui.favorites

import androidx.lifecycle.viewModelScope
import com.example.minimaltodo.data.local.TodoEntity
import com.example.minimaltodo.data.preferences.SettingsRepository
import com.example.minimaltodo.data.repository.TodoRepository
import com.example.minimaltodo.ui.common.ItemActionsViewModel
import com.example.minimaltodo.util.TreeIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/** Ein Element in einer flachen Liste mit Pfadangabe („Haus / Garten“). */
data class ListEntry(val item: TodoEntity, val path: String)

data class FavoritesUiState(val loading: Boolean = true, val entries: List<ListEntry> = emptyList())

class FavoritesViewModel(
    repository: TodoRepository,
    settingsRepository: SettingsRepository,
) : ItemActionsViewModel(repository, settingsRepository) {

    val uiState: StateFlow<FavoritesUiState> =
        combine(repository.observeFavorites(), repository.allItems) { favorites, all ->
            val index = TreeIndex(all)
            FavoritesUiState(false, favorites.map { ListEntry(it, index.pathOf(it.id)) })
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FavoritesUiState())
}
