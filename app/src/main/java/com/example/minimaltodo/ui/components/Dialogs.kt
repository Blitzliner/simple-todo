package com.example.minimaltodo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.minimaltodo.R
import com.example.minimaltodo.data.local.SortMode
import com.example.minimaltodo.data.local.TodoEntity
import com.example.minimaltodo.data.local.TodoType
import com.example.minimaltodo.data.local.displayTitle

/**
 * Schlichter Material-3-Dialog mit frei gestaltbarem Inhalt.
 * (AlertDialog misst Inhalte intrinsisch – das verträgt sich nicht mit LazyColumn.)
 */
@Composable
fun AppDialog(
    onDismiss: () -> Unit,
    title: String,
    buttons: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.widthIn(min = 280.dp, max = 560.dp),
        ) {
            Column(Modifier.padding(top = 24.dp, bottom = 16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Column(
                    Modifier
                        .padding(top = 16.dp)
                        .weight(1f, fill = false),
                ) { content() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) { buttons() }
            }
        }
    }
}

/** Radio-Zeile mit großer Touch-Fläche. */
@Composable
fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, supporting: String? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun sortModeLabel(mode: SortMode): String = stringResource(
    when (mode) {
        SortMode.MANUAL -> R.string.sort_manual
        SortMode.ALPHABETICAL -> R.string.sort_alphabetical
        SortMode.OPEN_FIRST -> R.string.sort_open_first
        SortMode.COMPLETED_FIRST -> R.string.sort_completed_first
        SortMode.NEWEST_FIRST -> R.string.sort_newest_first
        SortMode.OLDEST_FIRST -> R.string.sort_oldest_first
    },
)

/** Auswahl der Sortierung. */
@Composable
fun SortSelector(current: SortMode, onSelect: (SortMode) -> Unit, onDismiss: () -> Unit, title: String = stringResource(R.string.sort_title)) {
    AppDialog(
        onDismiss = onDismiss,
        title = title,
        buttons = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        Column(
            Modifier
                .selectableGroup()
                .verticalScroll(rememberScrollState()),
        ) {
            SortMode.entries.forEach { mode ->
                RadioRow(
                    label = sortModeLabel(mode),
                    selected = mode == current,
                    onClick = {
                        onSelect(mode)
                        onDismiss()
                    },
                    supporting = if (mode == SortMode.MANUAL || mode == SortMode.OPEN_FIRST) {
                        stringResource(R.string.sort_manual_hint)
                    } else {
                        null
                    },
                )
            }
        }
    }
}

/**
 * Rückfrage beim Löschen eines Elements mit Inhalt:
 * Abbrechen / Nur Gruppe löschen / Gruppe und Inhalte löschen.
 */
@Composable
fun DeleteDialog(item: TodoEntity, onDelete: (includeChildren: Boolean) -> Unit, onDismiss: () -> Unit) {
    val isGroup = item.type == TodoType.GROUP
    AppDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.delete_title, item.displayTitle),
        buttons = {},
    ) {
        Text(
            text = stringResource(R.string.delete_children_question),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp),
            horizontalAlignment = Alignment.End,
        ) {
            TextButton(onClick = { onDelete(false) }) {
                Text(stringResource(if (isGroup) R.string.delete_only_group else R.string.delete_only_item))
            }
            TextButton(onClick = { onDelete(true) }) {
                Text(
                    stringResource(if (isGroup) R.string.delete_group_and_content else R.string.delete_item_and_content),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    }
}

/** „Änderungen verwerfen?“ */
@Composable
fun DiscardChangesDialog(onDiscard: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.discard_title)) },
        confirmButton = { TextButton(onClick = onDiscard) { Text(stringResource(R.string.discard)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
