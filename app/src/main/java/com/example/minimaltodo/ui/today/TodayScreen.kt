package com.example.minimaltodo.ui.today

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.minimaltodo.R
import com.example.minimaltodo.data.local.TodoEntity
import com.example.minimaltodo.ui.common.ItemActionHost
import com.example.minimaltodo.ui.common.appViewModel
import com.example.minimaltodo.ui.common.openItem
import com.example.minimaltodo.ui.components.EmptyState
import com.example.minimaltodo.ui.components.ItemRow
import com.example.minimaltodo.ui.components.RowActions
import com.example.minimaltodo.ui.theme.AppIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onOpenEditor: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenSearch: () -> Unit,
) {
    val viewModel = appViewModel { TodayViewModel(it.todoRepository, it.settingsRepository) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var menuTarget by remember { mutableStateOf<TodoEntity?>(null) }
    val overdueColor = MaterialTheme.colorScheme.error

    val actions = remember(viewModel) {
        RowActions(
            onToggleComplete = viewModel::toggleCompleted,
            onToggleExpand = {},
            onOpen = { openItem(it, onOpenEditor, onOpenGroup) },
            onLongPress = { menuTarget = it },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_today)) },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.loading -> Unit
            state.isEmpty -> EmptyState(
                title = stringResource(R.string.empty_today),
                icon = AppIcons.Today,
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp)) {
                section(R.string.section_overdue, state.overdue, state.today, actions, overdueColor)
                section(R.string.section_today, state.dueToday, state.today, actions, Color.Unspecified)
            }
        }
    }

    ItemActionHost(
        viewModel = viewModel,
        snackbarHostState = snackbarHostState,
        menuTarget = menuTarget,
        onMenuDismiss = { menuTarget = null },
        onOpenEditor = onOpenEditor,
        onOpenGroup = onOpenGroup,
    )
}

private fun LazyListScope.section(
    title: Int,
    items: List<TodoEntity>,
    today: Long,
    actions: RowActions,
    color: Color,
) {
    if (items.isEmpty()) return
    item(key = "header-$title", contentType = "header") {
        SectionHeader(stringResource(title), color)
    }
    items(items, key = { it.id }, contentType = { it.type }) { item ->
        ItemRow(item = item, today = today, actions = actions, modifier = Modifier.animateItem())
    }
}

@Composable
fun SectionHeader(text: String, color: Color = Color.Unspecified) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = if (color == Color.Unspecified) MaterialTheme.colorScheme.primary else color,
        modifier = Modifier
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}
