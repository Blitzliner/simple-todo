package com.example.minimaltodo.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.minimaltodo.R
import com.example.minimaltodo.data.local.TodoType
import com.example.minimaltodo.data.local.displayTitle
import com.example.minimaltodo.ui.common.MoveState
import com.example.minimaltodo.ui.theme.AppIcons

/**
 * Verschieben: nach oben/unten innerhalb der Liste oder in eine andere Gruppe / Aufgabe / Root.
 * Ziele im eigenen Unterbaum werden gar nicht erst angeboten.
 */
@Composable
fun MoveDialog(
    state: MoveState,
    onMoveTo: (String?) -> Unit,
    onMoveBy: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf("") }
    val targets = remember(state, filter) {
        if (filter.isBlank()) state.targets
        else state.targets.filter { it.id == null || it.title.contains(filter.trim(), ignoreCase = true) }
    }
    val currentParent = state.item.parentId

    AppDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.move_title, state.item.displayTitle),
        buttons = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        if (state.canReorder) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { onMoveBy(-1) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null)
                    Text(stringResource(R.string.move_up), maxLines = 1)
                }
                OutlinedButton(onClick = { onMoveBy(1) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                    Text(stringResource(R.string.move_down), maxLines = 1)
                }
            }
        }
        Text(
            text = stringResource(R.string.move_into),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
        )
        if (state.targets.size > 8) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.move_filter)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
            )
        }
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),
        ) {
            items(targets, key = { it.id ?: "__root__" }) { target ->
                val isCurrent = target.id == currentParent
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(enabled = !isCurrent, role = Role.Button) { onMoveTo(target.id) }
                        .semantics { selected = isCurrent }
                        .padding(start = 24.dp + (if (filter.isBlank()) 16.dp * (target.depth - 1).coerceIn(0, 8) else 0.dp), end = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = when (target.type) {
                            null -> Icons.Filled.Home
                            TodoType.GROUP -> AppIcons.Folder
                            else -> Icons.Filled.Check
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = if (target.id == null) stringResource(R.string.move_root) else target.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isCurrent) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (isCurrent) {
                        Text(
                            stringResource(R.string.move_current),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
