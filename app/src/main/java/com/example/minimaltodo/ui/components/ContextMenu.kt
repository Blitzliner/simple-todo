package com.example.minimaltodo.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.minimaltodo.R
import com.example.minimaltodo.data.local.TodoEntity
import com.example.minimaltodo.data.local.TodoType
import com.example.minimaltodo.data.local.displayTitle
import com.example.minimaltodo.ui.theme.AppIcons

enum class MenuAction {
    TOGGLE_DONE, OPEN_GROUP, ADD_INSIDE, EDIT, MOVE, DUPLICATE, FAVORITE, PIN, SORT, SHARE, DELETE
}

/** Kontextmenü (Long Press) als Bottom Sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextMenu(item: TodoEntity, onAction: (MenuAction) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
        ) {
            Text(
                text = item.displayTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            HorizontalDivider()

            fun entry(action: MenuAction) = { onAction(action) }

            if (item.type == TodoType.TODO) {
                MenuEntry(
                    Icons.Filled.Check,
                    stringResource(if (item.completed) R.string.action_mark_open else R.string.action_mark_done),
                    entry(MenuAction.TOGGLE_DONE),
                )
            }
            if (item.type == TodoType.GROUP) {
                MenuEntry(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.action_open_group), entry(MenuAction.OPEN_GROUP))
            }
            if (item.type != TodoType.TEXT) {
                MenuEntry(
                    Icons.Filled.Add,
                    stringResource(if (item.type == TodoType.GROUP) R.string.action_add_inside else R.string.action_add_subtask),
                    entry(MenuAction.ADD_INSIDE),
                )
            }
            MenuEntry(Icons.Filled.Edit, stringResource(R.string.action_edit), entry(MenuAction.EDIT))
            MenuEntry(AppIcons.Move, stringResource(R.string.action_move), entry(MenuAction.MOVE))
            MenuEntry(AppIcons.Copy, stringResource(R.string.action_duplicate), entry(MenuAction.DUPLICATE))
            MenuEntry(
                if (item.favorite) AppIcons.StarFilled else AppIcons.StarOutline,
                stringResource(if (item.favorite) R.string.action_unfavorite else R.string.action_favorite),
                entry(MenuAction.FAVORITE),
            )
            MenuEntry(
                AppIcons.Pin,
                stringResource(if (item.pinned) R.string.action_unpin else R.string.action_pin),
                entry(MenuAction.PIN),
            )
            if (item.type != TodoType.TEXT) {
                MenuEntry(AppIcons.Sort, stringResource(R.string.action_sort_children), entry(MenuAction.SORT))
            }
            MenuEntry(Icons.Filled.Share, stringResource(R.string.action_share), entry(MenuAction.SHARE))
            MenuEntry(
                Icons.Filled.Delete,
                stringResource(R.string.action_delete),
                entry(MenuAction.DELETE),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun MenuEntry(icon: ImageVector, label: String, onClick: () -> Unit, color: Color = Color.Unspecified) {
    ListItem(
        headlineContent = { Text(label, color = color) },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}
