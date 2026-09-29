package com.example.minimaltodo.testutil

import com.example.minimaltodo.data.local.ParentLink
import com.example.minimaltodo.data.local.Priority
import com.example.minimaltodo.data.local.TodoDao
import com.example.minimaltodo.data.local.TodoEntity
import com.example.minimaltodo.data.local.TodoType
import com.example.minimaltodo.data.repository.TransactionRunner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-Memory-Implementierung des DAO für schnelle JVM-Tests (Repository/ViewModel). */
class FakeTodoDao : TodoDao {

    val state = MutableStateFlow<Map<String, TodoEntity>>(emptyMap())
    private val items get() = state.value.values

    private fun sorted(list: Collection<TodoEntity>) =
        list.sortedWith(compareByDescending<TodoEntity> { it.pinned }.thenBy { it.sortOrder })

    private fun mutate(block: (MutableMap<String, TodoEntity>) -> Unit) {
        state.value = LinkedHashMap(state.value).also(block)
    }

    override suspend fun getById(id: String) = state.value[id]
    override fun observeById(id: String): Flow<TodoEntity?> = state.map { it[id] }

    override suspend fun insert(todo: TodoEntity) = mutate {
        require(todo.id !in it) { "UNIQUE constraint failed: ${todo.id}" }
        it[todo.id] = todo
    }

    override suspend fun insertAll(todos: List<TodoEntity>) = mutate { map ->
        todos.forEach { require(it.id !in map) { "UNIQUE constraint failed: ${it.id}" } }
        todos.forEach { map[it.id] = it }
    }

    override suspend fun upsertAll(todos: List<TodoEntity>) = mutate { map -> todos.forEach { map[it.id] = it } }
    override suspend fun update(todo: TodoEntity) = mutate { if (todo.id in it) it[todo.id] = todo }
    override suspend fun updateAll(todos: List<TodoEntity>) = mutate { map ->
        todos.forEach { if (it.id in map) map[it.id] = it }
    }
    override suspend fun delete(todo: TodoEntity) = mutate { it.remove(todo.id) }
    override suspend fun deleteByIds(ids: List<String>) = mutate { map -> ids.forEach { map.remove(it) } }
    override suspend fun deleteAll() = mutate { it.clear() }

    override suspend fun getChildren(parentId: String) = sorted(items.filter { it.parentId == parentId })
    override suspend fun getRootItems() = sorted(items.filter { it.parentId == null })
    override fun observeChildren(parentId: String) = state.map { m -> sorted(m.values.filter { it.parentId == parentId }) }
    override fun observeRootItems() = state.map { m -> sorted(m.values.filter { it.parentId == null }) }
    override suspend fun countChildren(parentId: String) = items.count { it.parentId == parentId }
    override suspend fun maxSortOrder(parentId: String?) =
        items.filter { it.parentId == parentId }.maxOfOrNull { it.sortOrder } ?: 0L

    override suspend fun getAll() = items.toList()
    override fun observeAll() = state.map { it.values.toList() }
    override suspend fun getParentLinks() = items.map { ParentLink(it.id, it.parentId) }
    override suspend fun count() = items.size

    override fun search(pattern: String, limit: Int): Flow<List<TodoEntity>> {
        // LIKE '%x%' mit Escape '\' nachbilden
        val needle = pattern.removePrefix("%").removeSuffix("%")
            .replace("\\%", "%").replace("\\_", "_").replace("\\\\", "\\")
        return state.map { m -> m.values.filter { it.searchText.contains(needle) }.take(limit) }
    }

    override fun observeOpenTodos() = state.map { m -> m.values.filter { it.type == TodoType.TODO && !it.completed } }
    override fun observeCompletedTodos() = state.map { m -> m.values.filter { it.type == TodoType.TODO && it.completed } }
    override fun observeFavorites() = state.map { m -> m.values.filter { it.favorite } }
    override fun observePinned() = state.map { m -> m.values.filter { it.pinned } }
    override fun observeDueUntil(epochDay: Long) = state.map { m ->
        m.values.filter { it.type == TodoType.TODO && !it.completed && it.dueDate != null && it.dueDate!! <= epochDay }
            .sortedBy { it.dueDate }
    }
    override fun observeWithDueDate() = state.map { m -> m.values.filter { it.dueDate != null } }
    override fun observeHighPriority() = state.map { m -> m.values.filter { it.priority == Priority.HIGH && !it.completed } }
}

object DirectTransactionRunner : TransactionRunner {
    override suspend fun <R> run(block: suspend () -> R): R = block()
}

/** Deterministische IDs und Uhrzeit für Tests. */
class TestIds {
    private var next = 0
    fun newId(): String = "id-${++next}"
}

class TestClock(var now: Long = 1_000L) {
    fun tick(): Long = now++
}
