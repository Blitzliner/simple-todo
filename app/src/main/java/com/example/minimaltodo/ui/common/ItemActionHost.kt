package com.example.minimaltodo.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.minimaltodo.R
import com.example.minimaltodo.data.local.TodoEntity
import com.example.minimaltodo.data.local.TodoType
import com.example.minimaltodo.data.local.displayTitle
import com.example.minimaltodo.ui.components.ContextMenu
import com.example.minimaltodo.ui.components.DeleteDialog
import com.example.minimaltodo.ui.components.MenuAction
import com.example.minimaltodo.ui.components.MoveDialog
import com.example.minimaltodo.ui.components.QuickAddDialog
import com.example.minimaltodo.ui.components.SortSelector
import kotlinx.coroutines.launch

/**
 * Bündelt alles rund um Aktionen auf Einträgen, damit Home, Heute, Favoriten und Suche
 * dasselbe Verhalten haben: Kontextmenü, Verschieben, Löschen (mit Rückgängig),
 * Hinzufügen in Gruppen, Sortierung, Teilen und Snackbar-Meldungen.
 */
@Composable
fun ItemActionHost(
    viewModel: ItemActionsViewModel,
    snackbarHostState: SnackbarHostState,
    menuTarget: TodoEntity?,
    onMenuDismiss: () -> Unit,
    onOpenEditor: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val openEditor by rememberUpdatedState(onOpenEditor)
    val openGroup by rememberUpdatedState(onOpenGroup)

    var addTarget by remember { mutableStateOf<TodoEntity?>(null) }
    var sortTarget by remember { mutableStateOf<TodoEntity?>(null) }
    val moveState by viewModel.moveState.collectAsStateWithLifecycle()
    val pendingDelete by viewModel.pendingDelete.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ItemEvent.OpenEditor -> openEditor(event.id)
                is ItemEvent.OpenGroup -> openGroup(event.id)
                is ItemEvent.Share -> shareText(context, event.text)
                is ItemEvent.Created -> Unit
                else -> scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    when (event) {
                        is ItemEvent.Deleted -> {
                            val result = snackbarHostState.showSnackbar(
                                message = context.getString(R.string.snackbar_deleted, event.title.ifBlank { "…" }),
                                actionLabel = context.getString(R.string.undo),
                                duration = SnackbarDuration.Long,
                            )
                            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(event.snapshot)
                        }
                        is ItemEvent.SuggestCompleteParent -> {
                            val result = snackbarHostState.showSnackbar(
                                message = context.getString(R.string.snackbar_all_subtasks_done, event.parent.title),
                                actionLabel = context.getString(R.string.action_mark_done_short),
                                duration = SnackbarDuration.Long,
                            )
                            if (result == SnackbarResult.ActionPerformed) viewModel.setCompleted(event.parent, true)
                        }
                        is ItemEvent.Duplicated -> snackbarHostState.showSnackbar(context.getString(R.string.snackbar_duplicated))
                        ItemEvent.MoveRejected -> snackbarHostState.showSnackbar(context.getString(R.string.snackbar_move_rejected))
                        ItemEvent.Error -> snackbarHostState.showSnackbar(context.getString(R.string.error_generic))
                        else -> Unit
                    }
                }
            }
        }
    }

    menuTarget?.let { item ->
        ContextMenu(
            item = item,
            onDismiss = onMenuDismiss,
            onAction = { action ->
                onMenuDismiss()
                when (action) {
                    MenuAction.TOGGLE_DONE -> viewModel.toggleCompleted(item)
                    MenuAction.OPEN_GROUP -> onOpenGroup(item.id)
                    MenuAction.ADD_INSIDE -> addTarget = item
                    MenuAction.EDIT -> onOpenEditor(item.id)
                    MenuAction.MOVE -> viewModel.openMove(item)
                    MenuAction.DUPLICATE -> viewModel.duplicate(item)
                    MenuAction.FAVORITE -> viewModel.toggleFavorite(item)
                    MenuAction.PIN -> viewModel.togglePinned(item)
                    MenuAction.SORT -> sortTarget = item
                    MenuAction.SHARE -> viewModel.share(item)
                    MenuAction.DELETE -> viewModel.requestDelete(item)
                }
            },
        )
    }

    addTarget?.let { parent ->
        QuickAddDialog(
            parentTitle = parent.displayTitle,
            onDismiss = { addTarget = null },
            onSave = { type, text, details -> viewModel.create(type, text, parent.id, openEditor = details) },
        )
    }

    sortTarget?.let { item ->
        SortSelector(
            current = item.childSort ?: com.example.minimaltodo.data.local.SortMode.OPEN_FIRST,
            onSelect = { viewModel.setChildSort(item, it) },
            onDismiss = { sortTarget = null },
        )
    }

    moveState?.let { state ->
        MoveDialog(
            state = state,
            onMoveTo = viewModel::moveTo,
            onMoveBy = viewModel::moveBy,
            onDismiss = viewModel::dismissMove,
        )
    }

    pendingDelete?.let { item ->
        DeleteDialog(
            item = item,
            onDelete = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
}

/** Öffnet das native Android Share Sheet. */
fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    try {
        context.startActivity(Intent.createChooser(send, null))
    } catch (_: ActivityNotFoundException) {
        // Kein Ziel installiert – nichts zu tun.
    }
}

/** Standard-Klickverhalten für Einträge in flachen Listen. */
fun openItem(item: TodoEntity, onOpenEditor: (String) -> Unit, onOpenGroup: (String) -> Unit) {
    if (item.type == TodoType.GROUP) onOpenGroup(item.id) else onOpenEditor(item.id)
}
