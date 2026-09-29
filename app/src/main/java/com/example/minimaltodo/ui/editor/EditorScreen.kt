package com.example.minimaltodo.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.minimaltodo.R
import com.example.minimaltodo.data.local.TodoEntity
import com.example.minimaltodo.data.local.TodoType
import com.example.minimaltodo.ui.components.DiscardChangesDialog
import com.example.minimaltodo.ui.components.DueDateSelector
import com.example.minimaltodo.ui.components.EmptyState
import com.example.minimaltodo.ui.components.PrioritySelector
import com.example.minimaltodo.ui.components.VoiceInputButton
import com.example.minimaltodo.ui.common.appViewModel
import com.example.minimaltodo.ui.theme.AppIcons
import com.example.minimaltodo.util.DateUtils

/**
 * Editor für alle drei Typen – TodoEditor, TextEditor und GroupEditor teilen sich
 * Rahmen, Speichern-Logik und die Rückfrage bei ungespeicherten Änderungen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    itemId: String,
    onClose: () -> Unit,
    onOpenEditor: (String) -> Unit,
) {
    val viewModel = appViewModel(key = "editor-$itemId") { EditorViewModel(it.todoRepository, itemId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val subtasks by viewModel.subtasks.collectAsStateWithLifecycle()
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val requestClose: () -> Unit = {
        if (state.isDirty) {
            showDiscard = true
        } else {
            onClose()
        }
    }

    BackHandler(enabled = state.isDirty) { showDiscard = true }

    LaunchedEffect(state.saved) {
        if (state.saved) onClose()
    }
    LaunchedEffect(state.error) {
        if (state.error) {
            snackbarHostState.showSnackbar(context.getString(R.string.error_generic))
            viewModel.errorShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when (state.type) {
                                TodoType.TODO -> R.string.editor_title_todo
                                TodoType.TEXT -> R.string.editor_title_text
                                TodoType.GROUP -> R.string.editor_title_group
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = requestClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = state.canSave) {
                        Text(stringResource(R.string.save))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.loading -> Unit
            state.notFound -> EmptyState(
                title = stringResource(R.string.editor_not_found),
                modifier = Modifier.padding(padding),
            )
            else -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (state.type) {
                        TodoType.TODO -> TodoEditor(
                            state = state,
                            subtasks = subtasks,
                            onChange = viewModel::update,
                            onCompletedChange = viewModel::setCompleted,
                            onAddSubtask = viewModel::addSubtask,
                            onToggleSubtask = viewModel::toggleSubtask,
                            onOpenSubtask = { sub -> onOpenEditor(sub.id) },
                            onSave = viewModel::save,
                        )
                        TodoType.TEXT -> TextEditor(state, viewModel::update)
                        TodoType.GROUP -> GroupEditor(state, viewModel::update, viewModel::save)
                    }
                    FlagSwitches(state.draft, viewModel::update)
                    Button(
                        onClick = viewModel::save,
                        enabled = state.canSave,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(vertical = 8.dp),
                    ) { Text(stringResource(R.string.save)) }
                }
            }
        }
    }

    if (showDiscard) {
        DiscardChangesDialog(
            onDiscard = {
                showDiscard = false
                onClose()
            },
            onDismiss = { showDiscard = false },
        )
    }
}

@Composable
private fun ColumnScope.TodoEditor(
    state: EditorUiState,
    subtasks: List<TodoEntity>,
    onChange: ((EditorDraft) -> EditorDraft) -> Unit,
    onCompletedChange: (Boolean) -> Unit,
    onAddSubtask: (String) -> Unit,
    onToggleSubtask: (TodoEntity) -> Unit,
    onOpenSubtask: (TodoEntity) -> Unit,
    onSave: () -> Unit,
) {
    val draft = state.draft
    val today = remember { DateUtils.today() }

    CheckRow(
        label = stringResource(R.string.editor_completed),
        checked = state.completed,
        onCheckedChange = onCompletedChange,
    )
    OutlinedTextField(
        value = draft.title,
        onValueChange = { v -> onChange { it.copy(title = v) } },
        label = { Text(stringResource(R.string.field_title)) },
        singleLine = true,
        isError = draft.title.isBlank(),
        trailingIcon = { VoiceInputButton(draft.title, { v -> onChange { it.copy(title = v) } }) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSave() }),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.content,
        onValueChange = { v -> onChange { it.copy(content = v) } },
        label = { Text(stringResource(R.string.field_note)) },
        trailingIcon = { VoiceInputButton(draft.content, { v -> onChange { it.copy(content = v) } }) },
        minLines = 2,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )

    SectionLabel(stringResource(R.string.field_priority))
    PrioritySelector(selected = draft.priority, onSelect = { p -> onChange { it.copy(priority = p) } })

    SectionLabel(stringResource(R.string.field_due))
    DueDateSelector(
        dueDate = draft.dueDate,
        dueTime = draft.dueTime,
        today = today,
        onDateChange = { d -> onChange { it.copy(dueDate = d, dueTime = if (d == null) null else it.dueTime) } },
        onTimeChange = { t -> onChange { it.copy(dueTime = t) } },
    )

    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    SectionLabel(stringResource(R.string.subtasks_title))
    subtasks.forEach { sub ->
        SubtaskRow(sub, onToggle = { onToggleSubtask(sub) }, onOpen = { onOpenSubtask(sub) })
    }
    var newSubtask by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = newSubtask,
        onValueChange = { newSubtask = it },
        placeholder = { Text(stringResource(R.string.subtask_add)) },
        trailingIcon = { VoiceInputButton(newSubtask, { newSubtask = it }) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            onAddSubtask(newSubtask)
            newSubtask = ""
        }),
        modifier = Modifier.fillMaxWidth(),
    )
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
}

@Composable
private fun TextEditor(state: EditorUiState, onChange: ((EditorDraft) -> EditorDraft) -> Unit) {
    val draft = state.draft
    OutlinedTextField(
        value = draft.title,
        onValueChange = { v -> onChange { it.copy(title = v) } },
        label = { Text(stringResource(R.string.field_title_optional)) },
        trailingIcon = { VoiceInputButton(draft.title, { v -> onChange { it.copy(title = v) } }) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.content,
        onValueChange = { v -> onChange { it.copy(content = v) } },
        label = { Text(stringResource(R.string.field_text)) },
        trailingIcon = { VoiceInputButton(draft.content, { v -> onChange { it.copy(content = v) } }) },
        minLines = 8,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp),
    )
}

@Composable
private fun GroupEditor(state: EditorUiState, onChange: ((EditorDraft) -> EditorDraft) -> Unit, onSave: () -> Unit) {
    OutlinedTextField(
        value = state.draft.title,
        onValueChange = { v -> onChange { it.copy(title = v) } },
        label = { Text(stringResource(R.string.field_name)) },
        trailingIcon = { VoiceInputButton(state.draft.title, { v -> onChange { it.copy(title = v) } }) },
        singleLine = true,
        isError = state.draft.title.isBlank(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSave() }),
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        stringResource(R.string.group_editor_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun FlagSwitches(draft: EditorDraft, onChange: ((EditorDraft) -> EditorDraft) -> Unit) {
    SwitchRow(
        icon = AppIcons.StarOutline,
        label = stringResource(R.string.favorite),
        checked = draft.favorite,
        onCheckedChange = { v -> onChange { it.copy(favorite = v) } },
    )
    SwitchRow(
        icon = AppIcons.Pin,
        label = stringResource(R.string.pinned),
        checked = draft.pinned,
        onCheckedChange = { v -> onChange { it.copy(pinned = v) } },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(top = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun SwitchRow(icon: ImageVector, label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Checkbox),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SubtaskRow(item: TodoEntity, onToggle: () -> Unit, onOpen: () -> Unit) {
    val checkLabel = stringResource(R.string.a11y_done_checkbox, item.title)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(
            checked = item.completed,
            onCheckedChange = { onToggle() },
            modifier = Modifier.semantics { contentDescription = checkLabel },
        )
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (item.completed) TextDecoration.LineThrough else null,
            color = if (item.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .clickable(onClickLabel = stringResource(R.string.a11y_edit), onClick = onOpen)
                .padding(vertical = 12.dp),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}
