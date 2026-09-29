package com.example.minimaltodo.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.minimaltodo.BuildConfig
import com.example.minimaltodo.R
import com.example.minimaltodo.data.backup.ImportMode
import com.example.minimaltodo.data.preferences.ExportFormat
import com.example.minimaltodo.data.preferences.ThemeMode
import com.example.minimaltodo.ui.common.appViewModel
import com.example.minimaltodo.ui.components.AppDialog
import com.example.minimaltodo.ui.components.RadioRow
import com.example.minimaltodo.ui.components.SortSelector
import com.example.minimaltodo.ui.components.sortModeLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { SettingsViewModel(it.todoRepository, it.settingsRepository, it.documentIo) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showSort by rememberSaveable { mutableStateOf(false) }
    var showFormat by rememberSaveable { mutableStateOf(false) }
    var showExport by rememberSaveable { mutableStateOf(false) }
    var showPrivacy by rememberSaveable { mutableStateOf(false) }

    // Ein Launcher pro Format, da der MIME-Typ beim Erstellen feststeht.
    val exportLaunchers = ExportFormat.entries.associateWith { format ->
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(format.mimeType)) { uri ->
            if (uri != null) viewModel.export(uri, format)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.readImport(uri)
    }

    LaunchedEffect(message) {
        val m = message ?: return@LaunchedEffect
        val text = when (m) {
            SettingsMessage.ExportDone -> context.getString(R.string.export_done)
            SettingsMessage.ExportFailed -> context.getString(R.string.export_failed)
            is SettingsMessage.ImportDone -> context.resources.getQuantityString(R.plurals.import_done, m.count, m.count)
            SettingsMessage.ImportFailed -> context.getString(R.string.import_failed)
        }
        viewModel.messageShown()
        snackbarHostState.showSnackbar(text)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            Header(stringResource(R.string.settings_appearance))
            Text(
                stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Column(Modifier.selectableGroup()) {
                listOf(
                    ThemeMode.SYSTEM to R.string.theme_system,
                    ThemeMode.LIGHT to R.string.theme_light,
                    ThemeMode.DARK to R.string.theme_dark,
                ).forEach { (mode, label) ->
                    RadioRow(
                        label = stringResource(label),
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.setTheme(mode) },
                    )
                }
            }
            HorizontalDivider()

            Header(stringResource(R.string.settings_tasks))
            ListItem(
                headlineContent = { Text(stringResource(R.string.hide_completed)) },
                supportingContent = { Text(stringResource(R.string.hide_completed_hint)) },
                trailingContent = { Switch(checked = settings.hideCompleted, onCheckedChange = null) },
                modifier = Modifier.toggleable(
                    value = settings.hideCompleted,
                    onValueChange = viewModel::setHideCompleted,
                    role = Role.Switch,
                ),
            )
            ClickableItem(
                title = stringResource(R.string.settings_default_sort),
                subtitle = sortModeLabel(settings.defaultSort),
                onClick = { showSort = true },
            )
            HorizontalDivider()

            Header(stringResource(R.string.settings_export))
            ClickableItem(
                title = stringResource(R.string.settings_export_format),
                subtitle = formatLabel(settings.exportFormat),
                onClick = { showFormat = true },
            )
            HorizontalDivider()

            Header(stringResource(R.string.settings_data))
            ClickableItem(
                title = stringResource(R.string.settings_export_action),
                subtitle = stringResource(R.string.settings_export_hint),
                onClick = { showExport = true },
                enabled = !busy,
            )
            ClickableItem(
                title = stringResource(R.string.settings_import_action),
                subtitle = stringResource(R.string.settings_import_hint),
                onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
                enabled = !busy,
            )
            HorizontalDivider()

            Header(stringResource(R.string.settings_about))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_version)) },
                supportingContent = { Text(BuildConfig.VERSION_NAME) },
            )
            ClickableItem(
                title = stringResource(R.string.settings_privacy),
                subtitle = stringResource(R.string.settings_privacy_short),
                onClick = { showPrivacy = true },
            )
        }
    }

    if (showSort) {
        SortSelector(
            current = settings.defaultSort,
            onSelect = viewModel::setDefaultSort,
            onDismiss = { showSort = false },
            title = stringResource(R.string.settings_default_sort),
        )
    }

    if (showFormat || showExport) {
        FormatDialog(
            title = stringResource(if (showExport) R.string.settings_export_action else R.string.settings_export_format),
            initial = settings.exportFormat,
            confirmLabel = stringResource(if (showExport) R.string.export_continue else R.string.ok),
            onConfirm = { format ->
                if (showExport) {
                    exportLaunchers.getValue(format).launch("minimal-todo.${format.extension}")
                } else {
                    viewModel.setExportFormat(format)
                }
                showFormat = false
                showExport = false
            },
            onDismiss = {
                showFormat = false
                showExport = false
            },
        )
    }

    pendingImport?.let { pending ->
        ImportDialog(
            count = pending.items.size,
            onConfirm = viewModel::confirmImport,
            onDismiss = viewModel::cancelImport,
        )
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text(stringResource(R.string.settings_privacy)) },
            text = { Text(stringResource(R.string.privacy_text)) },
            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun ClickableItem(title: String, subtitle: String?, onClick: () -> Unit, enabled: Boolean = true) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) { { Text(subtitle) } } else null,
        modifier = Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    )
}

@Composable
private fun formatLabel(format: ExportFormat): String = stringResource(
    when (format) {
        ExportFormat.TXT -> R.string.format_txt
        ExportFormat.MARKDOWN -> R.string.format_markdown
        ExportFormat.JSON -> R.string.format_json
    },
)

@Composable
private fun FormatDialog(
    title: String,
    initial: ExportFormat,
    confirmLabel: String,
    onConfirm: (ExportFormat) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(initial) }
    AppDialog(
        onDismiss = onDismiss,
        title = title,
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            TextButton(onClick = { onConfirm(selected) }) { Text(confirmLabel) }
        },
    ) {
        Column(Modifier.selectableGroup()) {
            ExportFormat.entries.forEach { format ->
                RadioRow(
                    label = formatLabel(format),
                    selected = selected == format,
                    onClick = { selected = format },
                    supporting = if (format == ExportFormat.JSON) stringResource(R.string.format_json_hint) else null,
                )
            }
        }
    }
}

@Composable
private fun ImportDialog(count: Int, onConfirm: (ImportMode) -> Unit, onDismiss: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf(ImportMode.ADD) }
    val context = LocalContext.current
    AppDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.import_title),
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            TextButton(onClick = { onConfirm(mode) }) { Text(stringResource(R.string.import_confirm)) }
        },
    ) {
        Text(
            text = context.resources.getQuantityString(R.plurals.import_found, count, count),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Column(
            Modifier
                .selectableGroup()
                .padding(top = 8.dp),
        ) {
            RadioRow(
                label = stringResource(R.string.import_add),
                supporting = stringResource(R.string.import_add_hint),
                selected = mode == ImportMode.ADD,
                onClick = { mode = ImportMode.ADD },
            )
            RadioRow(
                label = stringResource(R.string.import_replace),
                supporting = stringResource(R.string.import_replace_hint),
                selected = mode == ImportMode.REPLACE,
                onClick = { mode = ImportMode.REPLACE },
            )
        }
    }
}
