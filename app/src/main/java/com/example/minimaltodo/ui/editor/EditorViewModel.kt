package com.example.minimaltodo.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.minimaltodo.data.local.Priority
import com.example.minimaltodo.data.local.TodoEntity
import com.example.minimaltodo.data.local.TodoType
import com.example.minimaltodo.data.repository.TodoRepository
import com.example.minimaltodo.util.Sorting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Bearbeitbare Felder eines Eintrags. */
data class EditorDraft(
    val title: String = "",
    val content: String = "",
    val priority: Priority = Priority.NONE,
    val dueDate: Long? = null,
    val dueTime: Int? = null,
    val favorite: Boolean = false,
    val pinned: Boolean = false,
) {
    companion object {
        fun from(e: TodoEntity) = EditorDraft(
            title = e.title,
            content = e.content.orEmpty(),
            priority = e.priority,
            dueDate = e.dueDate,
            dueTime = e.dueTime,
            favorite = e.favorite,
            pinned = e.pinned,
        )
    }
}

data class EditorUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val type: TodoType = TodoType.TODO,
    val completed: Boolean = false,
    val original: EditorDraft = EditorDraft(),
    val draft: EditorDraft = EditorDraft(),
    val saved: Boolean = false,
    val error: Boolean = false,
) {
    val isDirty: Boolean get() = !loading && draft != original

    /** Aufgaben und Gruppen brauchen einen Titel; ein Text braucht Titel oder Inhalt. */
    val canSave: Boolean
        get() = !loading && !notFound && when (type) {
            TodoType.TEXT -> draft.title.isNotBlank() || draft.content.isNotBlank()
            else -> draft.title.isNotBlank()
        }
}

class EditorViewModel(
    private val repository: TodoRepository,
    val itemId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    /** Direkte Unteraufgaben (nur für Aufgaben relevant). */
    val subtasks: StateFlow<List<TodoEntity>> = repository.observeChildren(itemId)
        .map { list -> list.filter { it.type == TodoType.TODO }.sortedWith(Sorting.manualDisplay) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val item = try {
                repository.get(itemId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _state.value = if (item == null) {
                EditorUiState(loading = false, notFound = true)
            } else {
                val draft = EditorDraft.from(item)
                EditorUiState(
                    loading = false,
                    type = item.type,
                    completed = item.completed,
                    original = draft,
                    draft = draft,
                )
            }
        }
    }

    fun update(change: (EditorDraft) -> EditorDraft) {
        _state.update { it.copy(draft = change(it.draft)) }
    }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        val d = current.draft
        viewModelScope.launch {
            try {
                repository.modify(itemId) {
                    it.copy(
                        title = d.title.trim(),
                        content = d.content.trimEnd().ifBlank { null },
                        priority = d.priority,
                        dueDate = d.dueDate,
                        dueTime = d.dueTime.takeIf { d.dueDate != null },
                        favorite = d.favorite,
                        pinned = d.pinned,
                    )
                }
                _state.update { it.copy(original = it.draft, saved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = true) }
            }
        }
    }

    fun errorShown() = _state.update { it.copy(error = false) }

    fun setCompleted(completed: Boolean) {
        viewModelScope.launch {
            try {
                repository.setCompleted(itemId, completed)
                _state.update { it.copy(completed = completed) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(error = true) }
            }
        }
    }

    fun addSubtask(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            try {
                repository.create(TodoType.TODO, title, itemId)
                repository.setCollapsed(itemId, false)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(error = true) }
            }
        }
    }

    fun toggleSubtask(subtask: TodoEntity) {
        viewModelScope.launch {
            try {
                repository.setCompleted(subtask.id, !subtask.completed)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(error = true) }
            }
        }
    }
}
