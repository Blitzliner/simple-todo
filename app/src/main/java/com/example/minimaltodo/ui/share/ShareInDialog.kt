package com.example.minimaltodo.ui.share

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.minimaltodo.R
import com.example.minimaltodo.data.local.TodoType
import com.example.minimaltodo.ui.components.AppDialog

/** Dialog für Inhalte, die aus anderen Apps geteilt wurden: „Als Aufgabe speichern?“ */
@Composable
fun ShareInDialog(
    sharedText: String,
    onSave: (text: String, type: TodoType) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable(sharedText) { mutableStateOf(sharedText) }
    val multiline = '\n' in text.trim()
    AppDialog(
        onDismiss = onDismiss,
        title = stringResource(if (multiline) R.string.share_in_title_text else R.string.share_in_title),
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            if (multiline) {
                TextButton(onClick = { onSave(text, TodoType.TODO) }, enabled = text.isNotBlank()) {
                    Text(stringResource(R.string.share_in_as_todo))
                }
                TextButton(onClick = { onSave(text, TodoType.TEXT) }, enabled = text.isNotBlank()) {
                    Text(stringResource(R.string.save))
                }
            } else {
                TextButton(onClick = { onSave(text, TodoType.TEXT) }, enabled = text.isNotBlank()) {
                    Text(stringResource(R.string.share_in_as_text))
                }
                TextButton(onClick = { onSave(text, TodoType.TODO) }, enabled = text.isNotBlank()) {
                    Text(stringResource(R.string.save))
                }
            }
        },
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            maxLines = 8,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        )
    }
}
