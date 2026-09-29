package com.example.minimaltodo.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.minimaltodo.R
import com.example.minimaltodo.data.local.TodoEntity
import com.example.minimaltodo.data.local.TodoType
import com.example.minimaltodo.ui.common.ItemActionHost
import com.example.minimaltodo.ui.common.appViewModel
import com.example.minimaltodo.ui.components.EmptyState
import com.example.minimaltodo.ui.components.QuickAddDialog
import com.example.minimaltodo.ui.components.RowActions
import com.example.minimaltodo.ui.components.SortSelector
import com.example.minimaltodo.ui.components.TreeItemRow
import com.example.minimaltodo.ui.components.rememberVoiceInput
import com.example.minimaltodo.util.SpeechText
import com.example.minimaltodo.ui.theme.AppIcons
import com.example.minimaltodo.util.DateUtils

/**
 * Hauptliste als Baum. Mit [rootId] wird eine einzelne Gruppe „geöffnet“ angezeigt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    rootId: String?,
    onOpenEditor: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: (() -> Unit)?,
) {
    val viewModel = appViewModel(key = "home-$rootId") { HomeViewModel(it.todoRepository, it.settingsRepository, rootId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val today = remember { DateUtils.today() }

    var menuTarget by remember { mutableStateOf<TodoEntity?>(null) }
    var showQuickAdd by rememberSaveable { mutableStateOf(false) }
    var spokenText by rememberSaveable { mutableStateOf("") }
    // Sprechen → Dialog öffnet sich mit dem erkannten Text → Enter → fertig.
    val startVoice = rememberVoiceInput { spoken ->
        spokenText = SpeechText.merge("", spoken)
        showQuickAdd = true
    }
    var showSort by rememberSaveable { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }

    // Geöffnete Gruppe wurde gelöscht → zurück.
    LaunchedEffect(state.rootMissing) {
        if (state.rootMissing) onBack?.invoke()
    }

    val actions = remember(viewModel) {
        RowActions(
            onToggleComplete = viewModel::toggleCompleted,
            onToggleExpand = viewModel::toggleExpanded,
            onOpen = { item -> if (item.type == TodoType.GROUP) onOpenGroup(item.id) else onOpenEditor(item.id) },
            onLongPress = { menuTarget = it },
        )
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.root?.title ?: stringResource(R.string.home_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search))
                    }
                    IconButton(onClick = { showOverflow = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sort_title)) },
                            leadingIcon = { Icon(AppIcons.Sort, contentDescription = null) },
                            onClick = {
                                showOverflow = false
                                showSort = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.hide_completed)) },
                            leadingIcon = {
                                if (state.hideCompleted) Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.a11y_active))
                            },
                            onClick = {
                                showOverflow = false
                                viewModel.setHideCompleted(!state.hideCompleted)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_title)) },
                            leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                            onClick = {
                                showOverflow = false
                                onOpenSettings()
                            },
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (startVoice != null) {
                    SmallFloatingActionButton(
                        onClick = startVoice,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Icon(AppIcons.Mic, contentDescription = stringResource(R.string.voice_add))
                    }
                }
                FloatingActionButton(onClick = {
                    spokenText = ""
                    showQuickAdd = true
                }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.loading -> Unit
            state.isEmpty -> EmptyState(
                title = stringResource(if (rootId == null) R.string.empty_home_title else R.string.empty_group_title),
                message = stringResource(R.string.empty_home_message),
                modifier = Modifier.padding(padding),
            )
            state.rows.isEmpty() -> EmptyState(
                title = stringResource(R.string.empty_all_done),
                message = stringResource(R.string.empty_all_done_message),
                icon = Icons.Filled.Check,
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 152.dp,
                ),
            ) {
                items(
                    items = state.rows,
                    key = { it.item.id },
                    contentType = { it.item.type },
                ) { row ->
                    TreeItemRow(row = row, today = today, actions = actions, modifier = Modifier.animateItem())
                }
            }
        }
    }

    if (showQuickAdd) {
        QuickAddDialog(
            parentTitle = state.root?.title,
            initialText = spokenText,
            onDismiss = {
                showQuickAdd = false
                spokenText = ""
            },
            onSave = { type, text, details -> viewModel.create(type, text, rootId, openEditor = details) },
        )
    }

    if (showSort) {
        SortSelector(
            current = state.sortMode,
            onSelect = viewModel::setSort,
            onDismiss = { showSort = false },
        )
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
