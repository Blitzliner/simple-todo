package com.example.minimaltodo.ui.search

import androidx.lifecycle.viewModelScope
import com.example.minimaltodo.data.local.TodoEntity
import com.example.minimaltodo.data.local.TodoType
import com.example.minimaltodo.data.preferences.SettingsRepository
import com.example.minimaltodo.data.repository.TodoRepository
import com.example.minimaltodo.ui.common.ItemActionsViewModel
import com.example.minimaltodo.ui.common.ItemEvent
import com.example.minimaltodo.ui.favorites.ListEntry
import com.example.minimaltodo.util.TreeIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

data class SearchUiState(
    val query: String = "",
    val searching: Boolean = false,
    val results: List<ListEntry> = emptyList(),
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    repository: TodoRepository,
    settingsRepository: SettingsRepository,
) : ItemActionsViewModel(repository, settingsRepository) {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val uiState: StateFlow<SearchUiState> = _query
        .debounce(150)
        .flatMapLatest { q ->
            if (q.isBlank()) {
                flowOf(SearchUiState(query = q))
            } else {
                combine(repository.search(q), repository.allItems) { results, all ->
                    val index = TreeIndex(all)
                    SearchUiState(q, false, results.map { ListEntry(it, index.pathOf(it.id)) })
                }
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun onQueryChange(value: String) {
        _query.value = value
    }

    /** Ergebnis öffnen: Pfad im Baum aufklappen und Editor öffnen. */
    fun open(item: TodoEntity) = launchSafe {
        repository.expandPath(item.id)
        send(if (item.type == TodoType.GROUP) ItemEvent.OpenGroup(item.id) else ItemEvent.OpenEditor(item.id))
    }
}
