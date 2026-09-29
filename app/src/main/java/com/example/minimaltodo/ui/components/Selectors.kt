package com.example.minimaltodo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.minimaltodo.R
import com.example.minimaltodo.data.local.Priority
import com.example.minimaltodo.util.DateUtils

/** Keine / Niedrig / Mittel / Hoch als Segmented Buttons. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrioritySelector(selected: Priority, onSelect: (Priority) -> Unit, modifier: Modifier = Modifier) {
    val options = listOf(
        Priority.NONE to R.string.priority_none,
        Priority.LOW to R.string.priority_low,
        Priority.MEDIUM to R.string.priority_medium,
        Priority.HIGH to R.string.priority_high,
    )
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (priority, label) ->
            SegmentedButton(
                selected = priority == selected,
                onClick = { onSelect(priority) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(stringResource(label), maxLines = 1) }
        }
    }
}

/**
 * Fälligkeit: Kein Datum / Heute / Morgen / Datum wählen, plus optionale Uhrzeit.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DueDateSelector(
    dueDate: Long?,
    dueTime: Int?,
    today: Long,
    onDateChange: (Long?) -> Unit,
    onTimeChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    val isCustom = dueDate != null && dueDate != today && dueDate != today + 1

    Column(modifier) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = dueDate == null,
                onClick = { onDateChange(null) },
                label = { Text(stringResource(R.string.due_none)) },
            )
            FilterChip(
                selected = dueDate == today,
                onClick = { onDateChange(today) },
                label = { Text(stringResource(R.string.date_today)) },
            )
            FilterChip(
                selected = dueDate == today + 1,
                onClick = { onDateChange(today + 1) },
                label = { Text(stringResource(R.string.date_tomorrow)) },
            )
            FilterChip(
                selected = isCustom,
                onClick = { showDatePicker = true },
                label = {
                    Text(if (isCustom && dueDate != null) DateUtils.formatDate(dueDate) else stringResource(R.string.due_pick))
                },
            )
        }
        if (dueDate != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (dueTime == null) {
                    FilterChip(
                        selected = false,
                        onClick = { showTimePicker = true },
                        label = { Text(stringResource(R.string.time_add)) },
                    )
                } else {
                    val clearLabel = stringResource(R.string.time_remove)
                    InputChip(
                        selected = true,
                        onClick = { showTimePicker = true },
                        label = { Text(stringResource(R.string.time_label, DateUtils.formatTime(dueTime))) },
                    )
                    IconButton(onClick = { onTimeChange(null) }) {
                        Icon(Icons.Filled.Close, contentDescription = clearLabel)
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = DateUtils.utcMillisFromEpochDay(dueDate ?: today),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDateChange(DateUtils.epochDayFromUtcMillis(it)) }
                    showDatePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        ) {
            DatePicker(state = state)
        }
    }

    if (showTimePicker) {
        val initial = dueTime ?: (9 * 60)
        val context = LocalContext.current
        val state = rememberTimePickerState(
            initialHour = initial / 60,
            initialMinute = initial % 60,
            is24Hour = android.text.format.DateFormat.is24HourFormat(context),
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.time_title)) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    onTimeChange(state.hour * 60 + state.minute)
                    showTimePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
