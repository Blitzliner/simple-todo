package com.example.minimaltodo.util

import com.example.minimaltodo.data.local.SortMode
import com.example.minimaltodo.data.local.TodoEntity
import com.example.minimaltodo.data.local.displayTitle
import java.text.Collator

/**
 * Sortierung von Geschwistern. Angeheftete Elemente stehen immer oben,
 * danach gilt der gewählte [SortMode]. Bei Gleichstand entscheidet die manuelle Reihenfolge.
 */
object Sorting {

    private val manual: Comparator<TodoEntity> =
        compareBy<TodoEntity> { it.sortOrder }.thenBy { it.createdAt }.thenBy { it.id }

    fun comparator(mode: SortMode): Comparator<TodoEntity> {
        val pinnedFirst = compareByDescending<TodoEntity> { it.pinned }
        val byMode: Comparator<TodoEntity> = when (mode) {
            SortMode.MANUAL -> manual
            SortMode.ALPHABETICAL -> {
                val collator = Collator.getInstance().apply { strength = Collator.SECONDARY }
                Comparator<TodoEntity> { a, b -> collator.compare(a.displayTitle, b.displayTitle) }
                    .then(manual)
            }
            SortMode.OPEN_FIRST -> compareBy<TodoEntity> { it.completed }.then(manual)
            SortMode.COMPLETED_FIRST -> compareByDescending<TodoEntity> { it.completed }.then(manual)
            SortMode.NEWEST_FIRST -> compareByDescending<TodoEntity> { it.createdAt }.then(manual)
            SortMode.OLDEST_FIRST -> compareBy<TodoEntity> { it.createdAt }.then(manual)
        }
        return pinnedFirst.then(byMode)
    }

    /** Reihenfolge für manuelles Verschieben (Pins oben, dann sortOrder). */
    val manualDisplay: Comparator<TodoEntity> = compareByDescending<TodoEntity> { it.pinned }.then(manual)
}
