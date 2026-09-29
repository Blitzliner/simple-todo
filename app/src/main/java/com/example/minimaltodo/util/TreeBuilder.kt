package com.example.minimaltodo.util

import com.example.minimaltodo.data.local.SortMode
import com.example.minimaltodo.data.local.TodoEntity
import com.example.minimaltodo.data.local.TodoType

/** Eine sichtbare Zeile im Baum. */
data class TreeRow(
    val item: TodoEntity,
    val depth: Int,
    /** Anzahl direkter Kinder (auch ausgeblendete). */
    val childCount: Int,
    /** Direkte Aufgaben-Kinder gesamt / erledigt – für die Anzeige „2/5“. */
    val todoTotal: Int,
    val todoDone: Int,
) {
    val hasChildren: Boolean get() = childCount > 0
    val expanded: Boolean get() = hasChildren && !item.collapsed
}

/** Indizierte Sicht auf eine flache Liste von Einträgen. */
class TreeIndex(items: List<TodoEntity>) {
    val byId: Map<String, TodoEntity> = items.associateBy { it.id }

    /** Kinder je Parent-ID (null = Root). Verwaiste Elemente landen auf der Root-Ebene. */
    val children: Map<String?, List<TodoEntity>> = HashMap<String?, MutableList<TodoEntity>>().also { map ->
        for (item in items) {
            val key = item.parentId?.takeIf { it in byId && it != item.id }
            map.getOrPut(key) { ArrayList() }.add(item)
        }
    }

    fun childrenOf(parentId: String?): List<TodoEntity> = children[parentId].orEmpty()

    fun sortedChildren(parentId: String?, defaultSort: SortMode): List<TodoEntity> {
        val list = childrenOf(parentId)
        if (list.size < 2) return list
        val mode = parentId?.let { byId[it]?.childSort } ?: defaultSort
        return list.sortedWith(Sorting.comparator(mode))
    }

    /** Vorfahren von [id] vom Root bis zum Parent. */
    fun ancestors(id: String): List<TodoEntity> =
        Hierarchy.ancestors(id, { byId[it] }, { it.parentId })

    /** Lesbarer Pfad, z. B. „Haus / Garten“. Leer für Root-Elemente. */
    fun pathOf(id: String): String = ancestors(id).joinToString(" / ") { it.titleForPath() }

    private fun TodoEntity.titleForPath(): String = title.ifBlank { "…" }
}

object TreeBuilder {

    /**
     * Baut die sichtbaren Zeilen des Baums (Tiefensuche, nur aufgeklappte Äste).
     * Kosten: O(n) für den Index + Sortierung nur der tatsächlich sichtbaren Geschwisterlisten.
     *
     * @param rootId null = gesamte Liste, sonst nur der Inhalt dieser Gruppe.
     * @param hideCompleted erledigte Aufgaben (inkl. ihrer Unteraufgaben) ausblenden.
     */
    fun build(
        items: List<TodoEntity>,
        rootId: String?,
        defaultSort: SortMode,
        hideCompleted: Boolean,
    ): List<TreeRow> = build(TreeIndex(items), rootId, defaultSort, hideCompleted)

    fun build(
        index: TreeIndex,
        rootId: String?,
        defaultSort: SortMode,
        hideCompleted: Boolean,
    ): List<TreeRow> {
        val result = ArrayList<TreeRow>()
        val visited = HashSet<String>()
        val stack = ArrayDeque<Pair<TodoEntity, Int>>()

        fun pushChildren(parentId: String?, depth: Int) {
            val kids = index.sortedChildren(parentId, defaultSort)
            for (i in kids.indices.reversed()) {
                val kid = kids[i]
                if (hideCompleted && kid.type == TodoType.TODO && kid.completed) continue
                stack.addLast(kid to depth)
            }
        }

        pushChildren(rootId, 0)
        while (stack.isNotEmpty()) {
            val (item, depth) = stack.removeLast()
            if (!visited.add(item.id)) continue
            val kids = index.childrenOf(item.id)
            var total = 0
            var done = 0
            for (k in kids) {
                if (k.type == TodoType.TODO) {
                    total++
                    if (k.completed) done++
                }
            }
            val row = TreeRow(item, depth, kids.size, total, done)
            result += row
            if (row.expanded) pushChildren(item.id, depth + 1)
        }
        return result
    }
}
