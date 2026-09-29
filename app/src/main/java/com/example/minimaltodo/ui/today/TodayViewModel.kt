package com.example.minimaltodo.ui.today

import androidx.lifecycle.viewModelScope
import com.example.minimaltodo.data.local.TodoEntity
import com.example.minimaltodo.data.preferences.SettingsRepository
import com.example.minimaltodo.data.repository.TodoRepository
import com.example.minimaltodo.ui.common.ItemActionsViewModel
import com.example.minimaltodo.util.DateUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class TodayUiState(
    val loading: Boolean = true,
    val today: Long = 0,
    val overdue: List<TodoEntity> = emptyList(),
    val dueToday: List<TodoEntity> = emptyList(),
) {
    val isEmpty: Boolean get() = overdue.isEmpty() && dueToday.isEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    repository: TodoRepository,
    settingsRepository: SettingsRepository,
    todayProvider: () -> Long = { DateUtils.today() },
) : ItemActionsViewModel(repository, settingsRepository) {

    /** Aktualisiert sich um Mitternacht (Prüfung jede Minute). */
    private val today: Flow<Long> = flow {
        while (true) {
            emit(todayProvider())
            delay(60_000)
        }
    }.distinctUntilChanged()

    val uiState: StateFlow<TodayUiState> = today
        .flatMapLatest { day ->
            repository.observeDueUntil(day).map { items ->
                TodayUiState(
                    loading = false,
                    today = day,
                    overdue = items.filter { (it.dueDate ?: day) < day },
                    dueToday = items.filter { it.dueDate == day },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())
}
