package com.example.minimaltodo.util

/**
 * Reine Funktionen zur Wahrung der Baum-Integrität.
 */
object Hierarchy {

    /**
     * Prüft, ob [itemId] unter [targetParentId] gehängt werden darf.
     * Verboten ist ein Ziel, das das Element selbst ist oder in seinem Unterbaum liegt
     * (sonst entstünde ein Zyklus wie A → B → C → A).
     * [parentOf] liefert den Parent einer ID (null = Root / unbekannt).
     */
    fun canMove(itemId: String, targetParentId: String?, parentOf: (String) -> String?): Boolean {
        if (targetParentId == null) return true
        var current: String? = targetParentId
        val visited = HashSet<String>()
        while (current != null) {
            if (current == itemId) return false
            if (!visited.add(current)) return false // bestehender Zyklus → sicherheitshalber verbieten
            current = parentOf(current)
        }
        return true
    }

    /** Alle IDs des Unterbaums von [rootId] inklusive [rootId] selbst (zyklensicher). */
    fun subtreeIds(rootId: String, childrenOf: (String) -> List<String>): List<String> {
        val result = ArrayList<String>()
        val visited = HashSet<String>()
        val stack = ArrayDeque<String>()
        stack.addLast(rootId)
        while (stack.isNotEmpty()) {
            val id = stack.removeLast()
            if (!visited.add(id)) continue
            result += id
            childrenOf(id).forEach { stack.addLast(it) }
        }
        return result
    }

    /** Pfad der Vorfahren von [id]: vom Root bis zum direkten Parent (ohne [id]). */
    fun <T> ancestors(id: String, byId: (String) -> T?, parentIdOf: (T) -> String?): List<T> {
        val result = ArrayList<T>()
        val visited = HashSet<String>().apply { add(id) }
        var parentId = byId(id)?.let(parentIdOf)
        while (parentId != null && visited.add(parentId)) {
            val parent = byId(parentId) ?: break
            result += parent
            parentId = parentIdOf(parent)
        }
        result.reverse()
        return result
    }
}
