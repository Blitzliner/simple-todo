package com.example.minimaltodo.data.local

import androidx.room.TypeConverter

/**
 * Speichert Enums als Namen (TEXT). Unbekannte Werte (z. B. aus beschädigten Daten)
 * führen nicht zum Absturz, sondern fallen auf einen sicheren Standardwert zurück.
 */
class Converters {
    @TypeConverter
    fun fromTodoType(value: TodoType): String = value.name

    @TypeConverter
    fun toTodoType(value: String?): TodoType =
        TodoType.entries.firstOrNull { it.name == value } ?: TodoType.TODO

    @TypeConverter
    fun fromPriority(value: Priority): String = value.name

    @TypeConverter
    fun toPriority(value: String?): Priority =
        Priority.entries.firstOrNull { it.name == value } ?: Priority.NONE

    @TypeConverter
    fun fromSortMode(value: SortMode?): String? = value?.name

    @TypeConverter
    fun toSortMode(value: String?): SortMode? =
        SortMode.entries.firstOrNull { it.name == value }
}
