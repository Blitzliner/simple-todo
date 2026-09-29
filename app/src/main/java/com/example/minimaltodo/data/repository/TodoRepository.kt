package com.example.minimaltodo.data.repository

import com.example.minimaltodo.data.backup.BackupCodec
import com.example.minimaltodo.data.backup.ImportMode
import com.example.minimaltodo.data.backup.ImportPlanner
import com.example.minimaltodo.data.local.SortMode
import com.example.minimaltodo.data.local.TodoDao
import com.example.minimaltodo.data.local.TodoEntity
import com.example.minimaltodo.data.local.TodoType
import com.example.minimaltodo.util.Hierarchy
import com.example.minimaltodo.util.Sorting
import com.example.minimaltodo.util.TextExporter
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** Führt einen Block atomar aus (in Produktion: Room-Transaktion). */
interface TransactionRunner {
    suspend fun <R> run(block: suspend () -> R): R
}

/** Ergebnis einer Verschiebung. */
enum class MoveResult { MOVED, UNCHANGED, INVALID_TARGET, NOT_FOUND }

/**
 * Zustand vor einem Löschvorgang – ermöglicht „Rückgängig“.
 * [deleted]: gelöschte Einträge, [modified]: Originalzustand veränderter Einträge.
 */
data class DeleteSnapshot(val deleted: List<TodoEntity>, val modified: List<TodoEntity>)

/**
 * Einziger Zugang der App zu den Todo-Daten. Room ist Single Source of Truth;
 * alle mehrteiligen Änderungen laufen in einer Transaktion.
 */
class TodoRepository(
    private val dao: TodoDao,
    private val transaction: TransactionRunner,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {

    // ------------------------------------------------------------ Beobachten

    val allItems: Flow<List<TodoEntity>> = dao.observeAll()

    fun observeItem(id: String): Flow<TodoEntity?> = dao.observeById(id)
    fun observeChildren(parentId: String): Flow<List<TodoEntity>> = dao.observeChildren(parentId)
    fun observeFavorites(): Flow<List<TodoEntity>> = dao.observeFavorites()
    fun observeDueUntil(epochDay: Long): Flow<List<TodoEntity>> = dao.observeDueUntil(epochDay)

    /** Case-insensitive Suche in Titel und Inhalt. */
    fun search(query: String, limit: Int = SEARCH_LIMIT): Flow<List<TodoEntity>> =
        dao.search(searchPattern(query), limit)

    suspend fun get(id: String): TodoEntity? = dao.getById(id)
    suspend fun getAll(): List<TodoEntity> = dao.getAll()
    suspend fun hasChildren(id: String): Boolean = dao.countChildren(id) > 0

    // ------------------------------------------------------------ Erstellen / Ändern

    /**
     * Legt einen neuen Eintrag am Ende seines Parents an.
     * Existiert der Parent nicht (mehr), landet der Eintrag auf der Root-Ebene.
     */
    suspend fun create(
        type: TodoType,
        title: String,
        parentId: String? = null,
        content: String? = null,
    ): TodoEntity = transaction.run {
        val parent = parentId?.takeIf { dao.getById(it) != null }
        val now = clock()
        val item = TodoEntity(
            id = newId(),
            parentId = parent,
            type = type,
            title = title.trim(),
            content = content?.takeIf { it.isNotBlank() },
            sortOrder = dao.maxSortOrder(parent) + SORT_STEP,
            createdAt = now,
            updatedAt = now,
        )
        dao.insert(item)
        item
    }

    /**
     * Aktualisiert einen Eintrag auf Basis des aktuellen DB-Stands.
     * So überschreibt z. B. ein Editor keine parallel geänderten Felder (etwa [TodoEntity.collapsed]).
     */
    suspend fun modify(id: String, touch: Boolean = true, change: (TodoEntity) -> TodoEntity): TodoEntity? =
        transaction.run {
            val current = dao.getById(id) ?: return@run null
            val changed = change(current).copy(id = current.id, parentId = current.parentId)
            if (changed == current) return@run current
            val updated = if (touch) changed.copy(updatedAt = clock()) else changed
            dao.update(updated)
            updated
        }

    suspend fun setCompleted(id: String, completed: Boolean): TodoEntity? =
        modify(id) { if (it.type == TodoType.TODO) it.copy(completed = completed) else it }

    suspend fun setFavorite(id: String, favorite: Boolean) = modify(id) { it.copy(favorite = favorite) }
    suspend fun setPinned(id: String, pinned: Boolean) = modify(id) { it.copy(pinned = pinned) }
    suspend fun setCollapsed(id: String, collapsed: Boolean) = modify(id, touch = false) { it.copy(collapsed = collapsed) }
    suspend fun setChildSort(id: String, mode: SortMode?) = modify(id, touch = false) { it.copy(childSort = mode) }

    /**
     * Falls alle Aufgaben-Geschwister von [childId] erledigt sind und der Parent eine offene
     * Aufgabe ist, wird der Parent zurückgegeben – die UI schlägt dann vor, ihn zu erledigen.
     * Es wird nichts automatisch erledigt.
     */
    suspend fun parentReadyToComplete(childId: String): TodoEntity? {
        val child = dao.getById(childId) ?: return null
        val parent = child.parentId?.let { dao.getById(it) } ?: return null
        if (parent.type != TodoType.TODO || parent.completed) return null
        val todos = dao.getChildren(parent.id).filter { it.type == TodoType.TODO }
        return parent.takeIf { todos.isNotEmpty() && todos.all { it.completed } }
    }

    /** Klappt alle Vorfahren von [id] auf, damit das Element im Baum sichtbar ist. */
    suspend fun expandPath(id: String) = transaction.run {
        val all = dao.getAll().associateBy { it.id }
        val ancestors = Hierarchy.ancestors(id, { all[it] }, { it.parentId })
        val toUpdate = ancestors.filter { it.collapsed }.map { it.copy(collapsed = false) }
        if (toUpdate.isNotEmpty()) dao.updateAll(toUpdate)
    }

    // ------------------------------------------------------------ Verschieben

    suspend fun canMove(id: String, targetParentId: String?): Boolean {
        val parents = dao.getParentLinks().associate { it.id to it.parentId }
        return Hierarchy.canMove(id, targetParentId, parents::get)
    }

    /**
     * Hängt [id] unter [targetParentId] (null = Root), ans Ende der Zielliste.
     * Ziele im eigenen Unterbaum werden abgelehnt (Zyklenschutz).
     */
    suspend fun move(id: String, targetParentId: String?): MoveResult = transaction.run {
        val item = dao.getById(id) ?: return@run MoveResult.NOT_FOUND
        if (item.parentId == targetParentId) return@run MoveResult.UNCHANGED
        if (targetParentId != null && dao.getById(targetParentId) == null) return@run MoveResult.INVALID_TARGET
        val parents = dao.getParentLinks().associate { it.id to it.parentId }
        if (!Hierarchy.canMove(id, targetParentId, parents::get)) return@run MoveResult.INVALID_TARGET
        dao.update(
            item.copy(
                parentId = targetParentId,
                sortOrder = dao.maxSortOrder(targetParentId) + SORT_STEP,
                updatedAt = clock(),
            ),
        )
        MoveResult.MOVED
    }

    /**
     * Verschiebt [id] innerhalb seiner Geschwister um [delta] Positionen (manuelle Reihenfolge).
     * Angeheftete und normale Elemente bleiben jeweils in ihrem Block.
     * Die Geschwister werden dabei sauber neu nummeriert.
     */
    suspend fun moveWithinSiblings(id: String, delta: Int): Boolean = transaction.run {
        val item = dao.getById(id) ?: return@run false
        val siblings = siblingsOf(item.parentId).sortedWith(Sorting.manualDisplay).toMutableList()
        val from = siblings.indexOfFirst { it.id == id }
        val to = (from + delta).coerceIn(0, siblings.lastIndex)
        if (from < 0 || to == from || siblings[to].pinned != item.pinned) return@run false
        siblings.add(to, siblings.removeAt(from))
        renumberUnder(siblings, item.parentId)
        true
    }

    private suspend fun siblingsOf(parentId: String?): List<TodoEntity> =
        if (parentId == null) dao.getRootItems() else dao.getChildren(parentId)

    // ------------------------------------------------------------ Löschen

    /**
     * Löscht [id].
     * - [includeChildren] = true: der komplette Unterbaum wird gelöscht.
     * - [includeChildren] = false: die Kinder rücken an die Stelle des gelöschten Elements
     *   (neuer Parent = Parent des gelöschten Elements, bei Root → Root).
     * Gibt einen [DeleteSnapshot] für „Rückgängig“ zurück.
     */
    suspend fun delete(id: String, includeChildren: Boolean): DeleteSnapshot? = transaction.run {
        val item = dao.getById(id) ?: return@run null
        if (includeChildren) {
            val all = dao.getAll()
            val byId = all.associateBy { it.id }
            val childrenMap = all.groupBy { it.parentId }
            val ids = Hierarchy.subtreeIds(id) { pid -> childrenMap[pid].orEmpty().map { it.id } }
            ids.chunked(DELETE_CHUNK).forEach { dao.deleteByIds(it) }
            DeleteSnapshot(deleted = ids.mapNotNull { byId[it] }, modified = emptyList())
        } else {
            val children = dao.getChildren(item.id).sortedWith(Sorting.manualDisplay)
            val siblings = siblingsOf(item.parentId).sortedWith(Sorting.manualDisplay)
            val newOrder = ArrayList<TodoEntity>(siblings.size + children.size)
            for (s in siblings) {
                if (s.id == item.id) newOrder += children else newOrder += s
            }
            dao.delete(item)
            val modified = renumberUnder(newOrder, item.parentId)
            DeleteSnapshot(deleted = listOf(item), modified = modified)
        }
    }

    /** Vergibt sortOrder = 1000, 2000, … unter [parentId]; schreibt nur Geänderte, liefert deren Originale. */
    private suspend fun renumberUnder(ordered: List<TodoEntity>, parentId: String?): List<TodoEntity> {
        val originals = ArrayList<TodoEntity>()
        val updates = ArrayList<TodoEntity>()
        ordered.forEachIndexed { i, e ->
            val order = (i + 1) * SORT_STEP
            if (e.sortOrder != order || e.parentId != parentId) {
                originals += e
                updates += e.copy(sortOrder = order, parentId = parentId)
            }
        }
        if (updates.isNotEmpty()) dao.updateAll(updates)
        return originals
    }

    /** Macht einen Löschvorgang rückgängig. */
    suspend fun restore(snapshot: DeleteSnapshot) = transaction.run {
        dao.upsertAll(snapshot.deleted + snapshot.modified)
    }

    // ------------------------------------------------------------ Duplizieren

    /**
     * Dupliziert [id] mit neuer ID an das Ende desselben Parents. completed wird zurückgesetzt.
     * Bei [includeChildren] wird der gesamte Unterbaum kopiert.
     */
    suspend fun duplicate(id: String, includeChildren: Boolean = true): TodoEntity? = transaction.run {
        val item = dao.getById(id) ?: return@run null
        val now = clock()
        val copyRoot = item.copy(
            id = newId(),
            completed = false,
            sortOrder = dao.maxSortOrder(item.parentId) + SORT_STEP,
            createdAt = now,
            updatedAt = now,
        )
        val copies = arrayListOf(copyRoot)
        if (includeChildren) {
            val childrenMap = dao.getAll().groupBy { it.parentId }
            val visited = HashSet<String>().apply { add(item.id) }
            val stack = ArrayDeque<Pair<String, String>>() // Original-ID -> Kopie-ID
            stack.addLast(item.id to copyRoot.id)
            while (stack.isNotEmpty()) {
                val (originalId, copyId) = stack.removeLast()
                for (child in childrenMap[originalId].orEmpty()) {
                    if (!visited.add(child.id)) continue
                    val c = child.copy(
                        id = newId(),
                        parentId = copyId,
                        completed = false,
                        createdAt = now,
                        updatedAt = now,
                    )
                    copies += c
                    stack.addLast(child.id to c.id)
                }
            }
        }
        dao.insertAll(copies)
        copyRoot
    }

    // ------------------------------------------------------------ Teilen / Export / Import

    suspend fun shareText(id: String, defaultSort: SortMode): String =
        TextExporter(dao.getAll(), defaultSort).shareText(id)

    suspend fun exportTxt(defaultSort: SortMode): String = TextExporter(dao.getAll(), defaultSort).exportTxt()
    suspend fun exportMarkdown(defaultSort: SortMode): String = TextExporter(dao.getAll(), defaultSort).exportMarkdown()
    suspend fun exportJson(): String = BackupCodec.encode(dao.getAll().sortedBy { it.createdAt }, clock())

    /** Parst ein Backup; wirft [com.example.minimaltodo.data.backup.BackupFormatException]. */
    fun parseBackup(json: String): List<TodoEntity> = BackupCodec.decode(json, clock(), newId)

    /**
     * Importiert Einträge.
     * - [ImportMode.ADD]: vorhandene Daten bleiben, ID-Konflikte werden durch neue IDs gelöst.
     * - [ImportMode.REPLACE]: vorhandene Daten werden in derselben Transaktion ersetzt
     *   (schlägt der Import fehl, bleibt alles unverändert).
     * @return Anzahl importierter Einträge
     */
    suspend fun import(items: List<TodoEntity>, mode: ImportMode): Int = transaction.run {
        val planned = when (mode) {
            ImportMode.ADD -> ImportPlanner.plan(
                imported = items,
                existingIds = dao.getParentLinks().mapTo(HashSet()) { it.id },
                rootSortStart = dao.maxSortOrder(null),
                newId = newId,
            )
            ImportMode.REPLACE -> ImportPlanner.plan(items, emptySet(), 0L, newId)
        }
        if (mode == ImportMode.REPLACE) dao.deleteAll()
        planned.chunked(INSERT_CHUNK).forEach { dao.insertAll(it) }
        planned.size
    }

    companion object {
        const val SORT_STEP = 1000L
        const val SEARCH_LIMIT = 300
        private const val DELETE_CHUNK = 500
        private const val INSERT_CHUNK = 500

        /** Baut ein LIKE-Muster; %, _ und \ im Suchbegriff werden maskiert. */
        fun searchPattern(query: String): String {
            val escaped = query.trim().lowercase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
            return "%$escaped%"
        }
    }
}
