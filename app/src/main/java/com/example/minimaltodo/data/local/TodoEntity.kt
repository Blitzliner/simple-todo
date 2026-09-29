package com.example.minimaltodo.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Art eines Eintrags. */
enum class TodoType { TODO, TEXT, GROUP }

/** Dezente Priorität einer Aufgabe. */
enum class Priority { NONE, LOW, MEDIUM, HIGH }

/** Sortierung der Kinder eines Parents (oder der Root-Ebene). */
enum class SortMode { MANUAL, ALPHABETICAL, OPEN_FIRST, COMPLETED_FIRST, NEWEST_FIRST, OLDEST_FIRST }

/**
 * Ein Eintrag der Liste: Aufgabe, freier Text oder Gruppe.
 *
 * - [parentId] == null bedeutet Root-Ebene. Jeder Typ darf Kinder haben
 *   (Gruppen und Aufgaben mit Unteraufgaben).
 * - [dueDate] ist ein Epoch-Day (Tage seit 1970-01-01, zeitzonenunabhängig).
 * - [dueTime] sind Minuten seit Mitternacht (0..1439).
 * - [repeatRule] ist für Version 1.1 reserviert (z. B. "DAILY", "WEEKLY").
 * - [childSort] ist die Sortierung der Kinder; null = Standard aus den Einstellungen.
 * - [collapsed] merkt sich, ob die Kinder im Baum eingeklappt sind.
 */
@Entity(
    tableName = "todos",
    indices = [
        Index("parentId"),
        Index("type"),
        Index("completed"),
        Index("favorite"),
        Index("dueDate"),
    ],
)
data class TodoEntity(
    @PrimaryKey
    val id: String,
    val parentId: String?,
    val type: TodoType,
    val title: String,
    val content: String? = null,
    val completed: Boolean = false,
    val favorite: Boolean = false,
    val pinned: Boolean = false,
    val priority: Priority = Priority.NONE,
    val dueDate: Long? = null,
    val dueTime: Int? = null,
    val repeatRule: String? = null,
    val sortOrder: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val childSort: SortMode? = null,
    val collapsed: Boolean = false,
) {
    /**
     * Klein geschriebener Suchtext aus Titel und Inhalt.
     * Wird bei jeder Instanziierung (auch bei copy()) automatisch neu berechnet,
     * damit die Suche per LIKE auch für Umlaute case-insensitiv funktioniert
     * (SQLite-LIKE ist nur für ASCII case-insensitiv).
     */
    var searchText: String = searchTextOf(title, content)

    companion object {
        fun searchTextOf(title: String, content: String?): String =
            (title + "\n" + content.orEmpty()).lowercase()
    }
}

/** Anzeigetitel: Titel oder – bei Texten ohne Titel – die erste Zeile des Inhalts. */
val TodoEntity.displayTitle: String
    get() = title.ifBlank { content?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim().orEmpty() }

/** Leichtgewichtige Projektion für Hierarchie-Prüfungen. */
data class ParentLink(val id: String, val parentId: String?)
