package com.example.minimaltodo.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.minimaltodo.data.local.SortMode
import com.example.minimaltodo.data.local.TodoEntity
import com.example.minimaltodo.data.local.TodoType
import com.example.minimaltodo.data.preferences.SettingsRepository
import com.example.minimaltodo.data.repository.DeleteSnapshot
import com.example.minimaltodo.data.repository.MoveResult
import com.example.minimaltodo.data.repository.TodoRepository
import com.example.minimaltodo.util.Sorting
import com.example.minimaltodo.util.TreeIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Einmalige UI-Ereignisse (Snackbar, Share Sheet, Navigation). */
sealed interface ItemEvent {
    data class Deleted(val snapshot: DeleteSnapshot, val title: String) : ItemEvent
    data class SuggestCompleteParent(val parent: TodoEntity) : ItemEvent
    data class Share(val text: String) : ItemEvent
    data class Duplicated(val copy: TodoEntity) : ItemEvent
    data class Created(val item: TodoEntity) : ItemEvent
    data class OpenEditor(val id: String) : ItemEvent
    data class OpenGroup(val id: String) : ItemEvent
    data object MoveRejected : ItemEvent
    data object Error : ItemEvent
}

/** Ein mögliches Ziel im Verschieben-Dialog. [id] == null ist die Root-Ebene. */
data class MoveTarget(val id: String?, val title: String, val depth: Int, val type: TodoType?)

data class MoveState(
    val item: TodoEntity,
    val targets: List<MoveTarget>,
    /** Innerhalb der Geschwister verschiebbar (manuelle Reihenfolge). */
    val canReorder: Boolean,
)

/**
 * Gemeinsame Aktionen für alle Listen (Home, Heute, Favoriten, Suche):
 * erledigen, favorisieren, anheften, verschieben, duplizieren, teilen, löschen.
 */
abstract class ItemActionsViewModel(
    protected val repository: TodoRepository,
    protected val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val eventChannel = Channel<ItemEvent>(Channel.BUFFERED)
    val events: Flow<ItemEvent> = eventChannel.receiveAsFlow()

    private val _pendingDelete = MutableStateFlow<TodoEntity?>(null)
    /** Element mit Kindern, für das die Lösch-Rückfrage angezeigt wird. */
    val pendingDelete: StateFlow<TodoEntity?> = _pendingDelete.asStateFlow()

    private val _moveState = MutableStateFlow<MoveState?>(null)
    val moveState: StateFlow<MoveState?> = _moveState.asStateFlow()

    protected fun send(event: ItemEvent) {
        eventChannel.trySend(event)
    }

    /** Startet eine Aktion; Fehler werden als verständliche Meldung statt Absturz gemeldet. */
    protected fun launchSafe(block: suspend () -> Unit): Job = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            send(ItemEvent.Error)
        }
    }

    // ---------------------------------------------------------------- Erstellen

    fun create(type: TodoType, title: String, parentId: String?, openEditor: Boolean = false) = launchSafe {
        if (title.isBlank()) return@launchSafe
        val item = when (type) {
            // Ein Text ist eine Notiz: die Eingabe wird zum Inhalt, ein Titel ist optional.
            TodoType.TEXT -> repository.create(type, "", parentId, content = title.trim())
            // Mehrzeilige Eingabe: erste Zeile = Titel, Rest = Notiz.
            else -> {
                val lines = title.trim().lines()
                repository.create(type, lines.first(), parentId, content = lines.drop(1).joinToString("\n").trim())
            }
        }
        parentId?.let { repository.setCollapsed(it, false) }
        send(ItemEvent.Created(item))
        if (openEditor) send(ItemEvent.OpenEditor(item.id))
    }

    // ---------------------------------------------------------------- Status

    fun setCompleted(item: TodoEntity, completed: Boolean) = launchSafe {
        repository.setCompleted(item.id, completed)
        if (completed) {
            repository.parentReadyToComplete(item.id)?.let { send(ItemEvent.SuggestCompleteParent(it)) }
        }
    }

    fun toggleCompleted(item: TodoEntity) = setCompleted(item, !item.completed)
    fun toggleFavorite(item: TodoEntity) = launchSafe { repository.setFavorite(item.id, !item.favorite) }
    fun togglePinned(item: TodoEntity) = launchSafe { repository.setPinned(item.id, !item.pinned) }
    fun toggleExpanded(item: TodoEntity) = launchSafe { repository.setCollapsed(item.id, !item.collapsed) }
    fun setChildSort(item: TodoEntity, mode: SortMode) = launchSafe { repository.setChildSort(item.id, mode) }

    // ---------------------------------------------------------------- Duplizieren / Teilen

    fun duplicate(item: TodoEntity, includeChildren: Boolean = true) = launchSafe {
        repository.duplicate(item.id, includeChildren)?.let { send(ItemEvent.Duplicated(it)) }
    }

    fun share(item: TodoEntity) = launchSafe {
        val sort = settingsRepository.settings.first().defaultSort
        val text = repository.shareText(item.id, sort)
        if (text.isNotBlank()) send(ItemEvent.Share(text))
    }

    // ---------------------------------------------------------------- Löschen

    /** Elemente mit Kindern → Rückfrage; sonst sofort löschen (mit „Rückgängig“). */
    fun requestDelete(item: TodoEntity) = launchSafe {
        if (repository.hasChildren(item.id)) {
            _pendingDelete.value = item
        } else {
            deleteNow(item, includeChildren = true)
        }
    }

    fun confirmDelete(includeChildren: Boolean) {
        val item = _pendingDelete.value ?: return
        _pendingDelete.value = null
        launchSafe { deleteNow(item, includeChildren) }
    }

    fun cancelDelete() {
        _pendingDelete.value = null
    }

    private suspend fun deleteNow(item: TodoEntity, includeChildren: Boolean) {
        repository.delete(item.id, includeChildren)?.let {
            send(ItemEvent.Deleted(it, item.title))
        }
    }

    fun undoDelete(snapshot: DeleteSnapshot) = launchSafe { repository.restore(snapshot) }

    // ---------------------------------------------------------------- Verschieben

    fun openMove(item: TodoEntity) = launchSafe {
        val all = repository.getAll()
        val index = TreeIndex(all)
        val current = index.byId[item.id] ?: return@launchSafe
        val targets = ArrayList<MoveTarget>()
        targets += MoveTarget(null, "", 0, null)
        // Tiefensuche über mögliche Container (Gruppen und Aufgaben), eigener Unterbaum ausgeschlossen.
        val stack = ArrayDeque<Pair<TodoEntity, Int>>()
        fun push(parentId: String?, depth: Int) {
            index.childrenOf(parentId).sortedWith(Sorting.manualDisplay).asReversed().forEach {
                if (it.id != item.id && it.type != TodoType.TEXT) stack.addLast(it to depth)
            }
        }
        push(null, 1)
        val visited = HashSet<String>()
        while (stack.isNotEmpty()) {
            val (e, depth) = stack.removeLast()
            if (!visited.add(e.id)) continue
            targets += MoveTarget(e.id, e.title, depth, e.type)
            push(e.id, depth + 1)
        }
        val parentSort = current.parentId?.let { index.byId[it]?.childSort }
            ?: settingsRepository.settings.first().defaultSort
        _moveState.value = MoveState(current, targets, canReorder = parentSort == SortMode.MANUAL)
    }

    fun moveTo(targetId: String?) {
        val item = _moveState.value?.item ?: return
        _moveState.value = null
        launchSafe {
            when (repository.move(item.id, targetId)) {
                MoveResult.INVALID_TARGET, MoveResult.NOT_FOUND -> send(ItemEvent.MoveRejected)
                MoveResult.MOVED -> targetId?.let { repository.setCollapsed(it, false) }
                MoveResult.UNCHANGED -> Unit
            }
        }
    }

    fun moveBy(delta: Int) {
        val item = _moveState.value?.item ?: return
        _moveState.value = null
        launchSafe { repository.moveWithinSiblings(item.id, delta) }
    }

    fun dismissMove() {
        _moveState.value = null
    }
}
