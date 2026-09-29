package com.example.minimaltodo.ui.search

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.minimaltodo.R
import com.example.minimaltodo.data.local.TodoEntity
import com.example.minimaltodo.ui.common.ItemActionHost
import com.example.minimaltodo.ui.common.appViewModel
import com.example.minimaltodo.ui.components.EmptyState
import com.example.minimaltodo.ui.components.ItemRow
import com.example.minimaltodo.ui.components.RowActions
import com.example.minimaltodo.ui.components.VoiceInputButton
import com.example.minimaltodo.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenEditor: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
) {
    val viewModel = appViewModel { SearchViewModel(it.todoRepository, it.settingsRepository) }
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val today = remember { DateUtils.today() }
    var menuTarget by remember { mutableStateOf<TodoEntity?>(null) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var requestedFocus by rememberSaveable { mutableStateOf(false) }

    val actions = remember(viewModel) {
        RowActions(
            onToggleComplete = viewModel::toggleCompleted,
            onToggleExpand = {},
            onOpen = { viewModel.open(it) },
            onLongPress = { menuTarget = it },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                title = {
                    TextField(
                        value = query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = { Text(stringResource(R.string.search_placeholder)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onQueryChange("") }) {
                                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.search_clear))
                                }
                            } else {
                                VoiceInputButton(current = query, onTextChange = viewModel::onQueryChange)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            query.isBlank() -> EmptyState(
                title = stringResource(R.string.search_hint),
                icon = Icons.Filled.Search,
                modifier = Modifier.padding(padding),
            )
            state.query.isNotBlank() && state.results.isEmpty() -> EmptyState(
                title = stringResource(R.string.search_no_results),
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp)) {
                items(state.results, key = { it.item.id }, contentType = { it.item.type }) { entry ->
                    ItemRow(item = entry.item, today = today, actions = actions, path = entry.path)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!requestedFocus) {
            requestedFocus = true
            focusRequester.requestFocus()
            keyboard?.show()
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
