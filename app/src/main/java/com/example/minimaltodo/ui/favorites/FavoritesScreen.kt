package com.example.minimaltodo.ui.favorites

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
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
import com.example.minimaltodo.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onOpenEditor: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenSearch: () -> Unit,
) {
    val viewModel = appViewModel { FavoritesViewModel(it.todoRepository, it.settingsRepository) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val today = remember { DateUtils.today() }
    var menuTarget by remember { mutableStateOf<TodoEntity?>(null) }

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
                title = { Text(stringResource(R.string.nav_favorites)) },
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
            state.entries.isEmpty() -> EmptyState(
                title = stringResource(R.string.empty_favorites),
                message = stringResource(R.string.empty_favorites_message),
                icon = AppIcons.StarOutline,
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp)) {
                items(state.entries, key = { it.item.id }, contentType = { it.item.type }) { entry ->
                    ItemRow(
                        item = entry.item,
                        today = today,
                        actions = actions,
                        path = entry.path,
                        modifier = Modifier.animateItem(),
                    )
                }
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
