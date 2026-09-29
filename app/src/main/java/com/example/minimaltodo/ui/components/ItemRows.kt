package com.example.minimaltodo.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.minimaltodo.R
import com.example.minimaltodo.data.local.Priority
import com.example.minimaltodo.data.local.TodoEntity
import com.example.minimaltodo.data.local.TodoType
import com.example.minimaltodo.data.local.displayTitle
import com.example.minimaltodo.ui.theme.AppIcons
import com.example.minimaltodo.util.DateUtils
import com.example.minimaltodo.util.TreeRow

/** Callbacks für Zeilen – als stabile Klasse, um unnötige Recompositions zu vermeiden. */
@Immutable
class RowActions(
    val onToggleComplete: (TodoEntity) -> Unit,
    val onToggleExpand: (TodoEntity) -> Unit,
    val onOpen: (TodoEntity) -> Unit,
    val onLongPress: (TodoEntity) -> Unit,
)

private const val MAX_INDENT_LEVELS = 10

/** Zeile im Baum (Home). */
@Composable
fun TreeItemRow(row: TreeRow, today: Long, actions: RowActions, modifier: Modifier = Modifier) {
    ItemRow(
        item = row.item,
        today = today,
        actions = actions,
        modifier = modifier,
        depth = row.depth,
        showExpander = true,
        hasChildren = row.hasChildren,
        expanded = row.expanded,
        todoTotal = row.todoTotal,
        todoDone = row.todoDone,
    )
}

/**
 * Gemeinsame Darstellung für Aufgabe (TodoRow), Text (TextRow) und Gruppe (GroupRow).
 * [path] wird – falls gesetzt – klein über dem Titel angezeigt (Suche, Favoriten).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItemRow(
    item: TodoEntity,
    today: Long,
    actions: RowActions,
    modifier: Modifier = Modifier,
    depth: Int = 0,
    showExpander: Boolean = false,
    hasChildren: Boolean = false,
    expanded: Boolean = false,
    todoTotal: Int = 0,
    todoDone: Int = 0,
    path: String? = null,
) {
    val openLabel = stringResource(
        when (item.type) {
            TodoType.GROUP -> if (hasChildren) R.string.a11y_toggle_group else R.string.a11y_open_group
            else -> R.string.a11y_edit
        },
    )
    val menuLabel = stringResource(R.string.a11y_actions)
    val onClick: () -> Unit = when {
        item.type == TodoType.GROUP && hasChildren && showExpander -> { { actions.onToggleExpand(item) } }
        else -> { { actions.onOpen(item) } }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClickLabel = openLabel,
                onLongClickLabel = menuLabel,
                onLongClick = { actions.onLongPress(item) },
                onClick = onClick,
            )
            .heightIn(min = 52.dp)
            .padding(start = 4.dp + 20.dp * depth.coerceAtMost(MAX_INDENT_LEVELS), end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showExpander) {
            Expander(visible = hasChildren, expanded = expanded, onClick = { actions.onToggleExpand(item) })
        } else {
            Spacer(Modifier.width(8.dp))
        }

        when (item.type) {
            TodoType.TODO -> {
                val checkLabel = stringResource(R.string.a11y_done_checkbox, item.displayTitle)
                Checkbox(
                    checked = item.completed,
                    onCheckedChange = { actions.onToggleComplete(item) },
                    modifier = Modifier.semantics { contentDescription = checkLabel },
                )
            }
            TodoType.TEXT -> LeadingIcon { Icon(AppIcons.Notes, contentDescription = stringResource(R.string.type_text)) }
            TodoType.GROUP -> LeadingIcon {
                Icon(
                    AppIcons.Folder,
                    contentDescription = stringResource(R.string.type_group),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            if (!path.isNullOrEmpty()) {
                Text(
                    text = path,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ItemTitle(item)
            MetaLine(item, today, todoTotal, todoDone)
        }

        Badges(item)
    }
}

@Composable
private fun LeadingIcon(content: @Composable () -> Unit) {
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
        ) { content() }
    }
}

@Composable
private fun Expander(visible: Boolean, expanded: Boolean, onClick: () -> Unit) {
    if (!visible) {
        Spacer(Modifier.width(36.dp))
        return
    }
    val label = stringResource(if (expanded) R.string.a11y_collapse else R.string.a11y_expand)
    Box(
        modifier = Modifier
            .size(width = 36.dp, height = 48.dp)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics {
                contentDescription = label
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ItemTitle(item: TodoEntity) {
    val done = item.type == TodoType.TODO && item.completed
    val colors = MaterialTheme.colorScheme
    when (item.type) {
        TodoType.TEXT -> {
            val hasTitle = item.title.isNotBlank()
            if (hasTitle) {
                Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val preview = item.content?.trim().orEmpty()
            if (preview.isNotEmpty()) {
                Text(
                    text = preview,
                    style = if (hasTitle) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    color = if (hasTitle) colors.onSurfaceVariant else colors.onSurface,
                    maxLines = if (hasTitle) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        else -> Text(
            text = item.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (item.type == TodoType.GROUP) FontWeight.SemiBold else null,
            color = if (done) colors.onSurfaceVariant else colors.onSurface,
            textDecoration = if (done) TextDecoration.LineThrough else null,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Dezente Zusatzinfos: Fälligkeit, Priorität, Fortschritt. Alles auch als Text lesbar. */
@Composable
private fun MetaLine(item: TodoEntity, today: Long, todoTotal: Int, todoDone: Int) {
    val due = item.dueDate
    val showDue = due != null && item.type == TodoType.TODO
    val showPriority = item.priority != Priority.NONE && item.type == TodoType.TODO && !item.completed
    val showProgress = todoTotal > 0
    if (!showDue && !showPriority && !showProgress) return

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showPriority) PriorityLabel(item.priority)
        if (showDue && due != null) {
            val overdue = !item.completed && DateUtils.isOverdue(due, today)
            val dateText = relativeDate(due, today)
            val text = buildString {
                append(dateText)
                item.dueTime?.let { append(", ").append(DateUtils.formatTime(it)) }
            }
            val overdueLabel = stringResource(R.string.overdue)
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = if (overdue) Modifier.semantics { stateDescription = overdueLabel } else Modifier,
            )
        }
        if (showProgress) {
            val description = pluralStringResource(R.plurals.a11y_progress, todoTotal, todoDone, todoTotal)
            Text(
                text = "$todoDone/$todoTotal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { contentDescription = description },
            )
        }
    }
}

@Composable
fun PriorityLabel(priority: Priority) {
    val (marks, label) = when (priority) {
        Priority.HIGH -> "!!!" to R.string.priority_high
        Priority.MEDIUM -> "!!" to R.string.priority_medium
        Priority.LOW -> "!" to R.string.priority_low
        Priority.NONE -> return
    }
    val description = stringResource(R.string.a11y_priority, stringResource(label))
    Text(
        text = marks,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Bold,
        color = if (priority == Priority.HIGH) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { contentDescription = description },
    )
}

@Composable
fun relativeDate(epochDay: Long, today: Long): String = when (epochDay - today) {
    0L -> stringResource(R.string.date_today)
    1L -> stringResource(R.string.date_tomorrow)
    -1L -> stringResource(R.string.date_yesterday)
    else -> DateUtils.formatDate(epochDay)
}

@Composable
private fun Badges(item: TodoEntity) {
    if (!item.pinned && !item.favorite) return
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val tint = MaterialTheme.colorScheme.onSurfaceVariant
        if (item.pinned) {
            Icon(AppIcons.Pin, stringResource(R.string.pinned), Modifier.size(16.dp), tint = tint)
        }
        if (item.favorite) {
            Icon(AppIcons.StarFilled, stringResource(R.string.favorite), Modifier.size(16.dp), tint = tint)
        }
    }
}
