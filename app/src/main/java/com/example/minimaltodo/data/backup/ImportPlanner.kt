package com.example.minimaltodo.data.backup

import com.example.minimaltodo.data.local.TodoEntity

enum class ImportMode { ADD, REPLACE }

/**
 * Bereitet importierte Einträge so auf, dass sie konfliktfrei eingefügt werden können:
 * - doppelte IDs innerhalb der Datei werden aufgelöst
 * - IDs, die bereits in der Datenbank existieren, bekommen neue IDs (Modus ADD),
 *   Verweise der Kinder werden entsprechend umgeschrieben
 * - Verweise auf nicht vorhandene Parents → Element landet auf der Root-Ebene
 * - Zyklen (A → B → A) werden aufgebrochen
 * - importierte Root-Elemente werden hinter die bestehenden Root-Elemente gehängt
 */
object ImportPlanner {

    fun plan(
        imported: List<TodoEntity>,
        existingIds: Set<String>,
        rootSortStart: Long,
        newId: () -> String,
        sortStep: Long = 1000L,
    ): List<TodoEntity> {
        // 1. IDs neu vergeben, wo nötig. Mehrfach vorkommende IDs innerhalb der Datei
        //    werden über ihre Position aufgelöst: der erste Eintrag „besitzt“ die Original-ID.
        val usedIds = HashSet<String>(existingIds)
        val newIds = ArrayList<String>(imported.size)
        val remap = HashMap<String, String>() // alte ID -> neue ID (erster Treffer)
        for (item in imported) {
            val id = if (item.id in usedIds) newId().also { check(it !in usedIds) } else item.id
            usedIds += id
            newIds += id
            remap.putIfAbsent(item.id, id)
        }

        // 2. Parent-Verweise umschreiben.
        var result = imported.mapIndexed { i, item ->
            val parent = item.parentId?.let { remap[it] }?.takeIf { it != newIds[i] }
            item.copy(id = newIds[i], parentId = parent)
        }

        // 3. Zyklen aufbrechen.
        val parentOf = HashMap<String, String?>().apply { result.forEach { put(it.id, it.parentId) } }
        val broken = HashSet<String>()
        for (item in result) {
            val seen = HashSet<String>()
            var current: String? = item.id
            while (current != null) {
                if (!seen.add(current)) {
                    // Zyklus gefunden: das aktuelle Element wird zur Wurzel.
                    parentOf[current] = null
                    broken += current
                    break
                }
                current = parentOf[current]
            }
        }
        if (broken.isNotEmpty()) {
            result = result.map { if (it.id in broken) it.copy(parentId = null) else it }
        }

        // 4. Root-Elemente hinter die vorhandenen hängen (relative Reihenfolge bleibt erhalten).
        val roots = result.filter { it.parentId == null }.sortedBy { it.sortOrder }
        val rootOrder = HashMap<String, Long>()
        roots.forEachIndexed { i, r -> rootOrder[r.id] = rootSortStart + (i + 1) * sortStep }
        return result.map { item -> rootOrder[item.id]?.let { item.copy(sortOrder = it) } ?: item }
    }
}
