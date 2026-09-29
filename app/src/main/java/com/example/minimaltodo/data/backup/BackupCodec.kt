package com.example.minimaltodo.data.backup

import com.example.minimaltodo.data.local.Priority
import com.example.minimaltodo.data.local.SortMode
import com.example.minimaltodo.data.local.TodoEntity
import com.example.minimaltodo.data.local.TodoType
import com.example.minimaltodo.util.DateUtils

/** Fehler beim Lesen eines Backups. Die Nachricht ist nur für Logs gedacht, nicht für die UI. */
class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Stabiles, versioniertes JSON-Backup-Format.
 *
 * ```
 * { "format": "minimal-todo", "version": 1, "exportedAt": 123, "items": [ {…}, … ] }
 * ```
 * Neue Felder dürfen in späteren Versionen ergänzt werden; unbekannte Felder werden ignoriert.
 */
object BackupCodec {

    const val VERSION = 1
    const val FORMAT = "minimal-todo"

    fun encode(items: List<TodoEntity>, exportedAt: Long): String {
        val root = LinkedHashMap<String, Any?>()
        root["format"] = FORMAT
        root["version"] = VERSION
        root["exportedAt"] = exportedAt
        root["items"] = items.map { encodeItem(it) }
        return Json.stringify(root)
    }

    private fun encodeItem(t: TodoEntity): Map<String, Any?> = linkedMapOf(
        "id" to t.id,
        "parentId" to t.parentId,
        "type" to t.type.name,
        "title" to t.title,
        "content" to t.content,
        "completed" to t.completed,
        "favorite" to t.favorite,
        "pinned" to t.pinned,
        "priority" to t.priority.name,
        "dueDate" to t.dueDate,
        "dueTime" to t.dueTime,
        "repeatRule" to t.repeatRule,
        "sortOrder" to t.sortOrder,
        "createdAt" to t.createdAt,
        "updatedAt" to t.updatedAt,
        "childSort" to t.childSort?.name,
        "collapsed" to t.collapsed,
    )

    /**
     * Liest ein Backup. Wirft [BackupFormatException] bei ungültigem JSON, fremdem Format
     * oder einer neueren, nicht unterstützten Version. Einzelne fehlerhafte Felder werden
     * tolerant mit Standardwerten ersetzt, damit möglichst viele Daten gerettet werden.
     */
    fun decode(text: String, now: Long, newId: () -> String): List<TodoEntity> {
        val root = try {
            Json.parse(text)
        } catch (e: Json.ParseException) {
            throw BackupFormatException("Ungültiges JSON: ${e.message}", e)
        } catch (e: StackOverflowError) {
            throw BackupFormatException("Ungültiges JSON")
        }
        if (root !is Map<*, *>) throw BackupFormatException("JSON-Objekt erwartet")
        val format = root["format"]
        if (format != null && format != FORMAT) throw BackupFormatException("Fremdes Format: $format")
        val version = (root["version"] as? Number)?.toLong()
            ?: throw BackupFormatException("Versionsangabe fehlt")
        if (version < 1 || version > VERSION) throw BackupFormatException("Nicht unterstützte Version $version")
        val items = root["items"] as? List<*> ?: throw BackupFormatException("'items' fehlt")

        return items.mapIndexedNotNull { index, raw ->
            val m = raw as? Map<*, *> ?: return@mapIndexedNotNull null
            decodeItem(m, index, now, newId)
        }
    }

    private fun decodeItem(m: Map<*, *>, index: Int, now: Long, newId: () -> String): TodoEntity {
        fun str(key: String): String? = m[key] as? String
        fun bool(key: String): Boolean = m[key] as? Boolean ?: false
        fun long(key: String): Long? = when (val v = m[key]) {
            is Long -> v
            is Double -> if (v.isFinite()) v.toLong() else null
            is Int -> v.toLong()
            else -> null
        }

        val type = TodoType.entries.firstOrNull { it.name == str("type") } ?: TodoType.TODO
        val createdAt = long("createdAt")?.takeIf { it >= 0 } ?: now
        return TodoEntity(
            id = str("id")?.takeIf { it.isNotBlank() && it.length <= 100 } ?: newId(),
            parentId = str("parentId")?.takeIf { it.isNotBlank() },
            type = type,
            title = str("title").orEmpty(),
            content = str("content"),
            completed = type == TodoType.TODO && bool("completed"),
            favorite = bool("favorite"),
            pinned = bool("pinned"),
            priority = Priority.entries.firstOrNull { it.name == str("priority") } ?: Priority.NONE,
            dueDate = long("dueDate")?.takeIf { DateUtils.isValidEpochDay(it) },
            dueTime = long("dueTime")?.toInt()?.takeIf { DateUtils.isValidMinuteOfDay(it) }
                ?.takeIf { long("dueDate") != null },
            repeatRule = str("repeatRule"),
            sortOrder = long("sortOrder") ?: ((index + 1) * 1000L),
            createdAt = createdAt,
            updatedAt = long("updatedAt")?.takeIf { it >= 0 } ?: createdAt,
            childSort = SortMode.entries.firstOrNull { it.name == str("childSort") },
            collapsed = bool("collapsed"),
        )
    }
}
