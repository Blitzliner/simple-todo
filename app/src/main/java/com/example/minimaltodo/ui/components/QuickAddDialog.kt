package com.example.minimaltodo.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.minimaltodo.R
import com.example.minimaltodo.data.local.TodoType

/**
 * Schnellerfassung: Text eingeben → Enter → fertig.
 * Standardtyp ist „Aufgabe“. [onSave] erhält (Typ, Text, Details öffnen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddDialog(
    onDismiss: () -> Unit,
    onSave: (type: TodoType, text: String, openDetails: Boolean) -> Unit,
    parentTitle: String? = null,
    initialType: TodoType = TodoType.TODO,
    initialText: String = "",
) {
    var text by rememberSaveable { mutableStateOf(initialText) }
    var type by rememberSaveable { mutableStateOf(initialType) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    fun save(openDetails: Boolean) {
        if (text.isNotBlank()) {
            onSave(type, text, openDetails)
            onDismiss()
        }
    }

    AppDialog(
        onDismiss = onDismiss,
        title = if (parentTitle != null) {
            stringResource(R.string.quick_add_in, parentTitle)
        } else {
            stringResource(R.string.quick_add_title)
        },
        buttons = {
            TextButton(onClick = { save(openDetails = true) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.details))
            }
            TextButton(onClick = { save(openDetails = false) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .focusRequester(focusRequester),
            placeholder = { Text(stringResource(R.string.quick_add_placeholder)) },
            trailingIcon = { VoiceInputButton(current = text, onTextChange = { text = it }) },
            singleLine = type != TodoType.TEXT,
            minLines = if (type == TodoType.TEXT) 3 else 1,
            maxLines = if (type == TodoType.TEXT) 8 else 1,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = if (type == TodoType.TEXT) ImeAction.Default else ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { save(openDetails = false) }),
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 16.dp),
        ) {
            val options = listOf(
                TodoType.TODO to R.string.type_todo,
                TodoType.TEXT to R.string.type_text,
                TodoType.GROUP to R.string.type_group,
            )
            options.forEachIndexed { index, (option, label) ->
                SegmentedButton(
                    selected = type == option,
                    onClick = { type = option },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) { Text(stringResource(label), maxLines = 1) }
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
}
