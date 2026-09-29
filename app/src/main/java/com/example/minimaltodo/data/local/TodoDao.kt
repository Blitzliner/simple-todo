package com.example.minimaltodo.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {

    // ---- Einzelnes Element ----

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun getById(id: String): TodoEntity?

    @Query("SELECT * FROM todos WHERE id = :id")
    fun observeById(id: String): Flow<TodoEntity?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(todo: TodoEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(todos: List<TodoEntity>)

    @Upsert
    suspend fun upsertAll(todos: List<TodoEntity>)

    @Update
    suspend fun update(todo: TodoEntity)

    @Update
    suspend fun updateAll(todos: List<TodoEntity>)

    @Delete
    suspend fun delete(todo: TodoEntity)

    /** Maximal ~500 IDs pro Aufruf (SQLite-Variablenlimit) – das Repository teilt auf. */
    @Query("DELETE FROM todos WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM todos")
    suspend fun deleteAll()

    // ---- Kinder ----

    @Query("SELECT * FROM todos WHERE parentId = :parentId ORDER BY pinned DESC, sortOrder ASC")
    suspend fun getChildren(parentId: String): List<TodoEntity>

    @Query("SELECT * FROM todos WHERE parentId IS NULL ORDER BY pinned DESC, sortOrder ASC")
    suspend fun getRootItems(): List<TodoEntity>

    @Query("SELECT * FROM todos WHERE parentId = :parentId ORDER BY pinned DESC, sortOrder ASC")
    fun observeChildren(parentId: String): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE parentId IS NULL ORDER BY pinned DESC, sortOrder ASC")
    fun observeRootItems(): Flow<List<TodoEntity>>

    @Query("SELECT COUNT(*) FROM todos WHERE parentId = :parentId")
    suspend fun countChildren(parentId: String): Int

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM todos WHERE parentId IS :parentId")
    suspend fun maxSortOrder(parentId: String?): Long

    // ---- Gesamtbestand ----

    @Query("SELECT * FROM todos")
    suspend fun getAll(): List<TodoEntity>

    @Query("SELECT * FROM todos")
    fun observeAll(): Flow<List<TodoEntity>>

    @Query("SELECT id, parentId FROM todos")
    suspend fun getParentLinks(): List<ParentLink>

    @Query("SELECT COUNT(*) FROM todos")
    suspend fun count(): Int

    // ---- Suche (case-insensitiv über den normalisierten searchText) ----

    @Query(
        "SELECT * FROM todos WHERE searchText LIKE :pattern ESCAPE '\\' " +
            "ORDER BY completed ASC, updatedAt DESC LIMIT :limit",
    )
    fun search(pattern: String, limit: Int): Flow<List<TodoEntity>>

    // ---- Filter ----

    @Query("SELECT * FROM todos WHERE type = 'TODO' AND completed = 0 ORDER BY sortOrder")
    fun observeOpenTodos(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE type = 'TODO' AND completed = 1 ORDER BY updatedAt DESC")
    fun observeCompletedTodos(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE favorite = 1 ORDER BY pinned DESC, completed ASC, title COLLATE NOCASE")
    fun observeFavorites(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE pinned = 1 ORDER BY sortOrder")
    fun observePinned(): Flow<List<TodoEntity>>

    /** Offene Aufgaben, die bis einschließlich [epochDay] fällig sind (heute + überfällig). */
    @Query(
        "SELECT * FROM todos WHERE type = 'TODO' AND completed = 0 AND dueDate IS NOT NULL " +
            "AND dueDate <= :epochDay ORDER BY dueDate ASC, dueTime IS NULL, dueTime ASC, sortOrder ASC",
    )
    fun observeDueUntil(epochDay: Long): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE dueDate IS NOT NULL ORDER BY dueDate ASC, dueTime IS NULL, dueTime ASC")
    fun observeWithDueDate(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE priority = 'HIGH' AND completed = 0 ORDER BY sortOrder")
    fun observeHighPriority(): Flow<List<TodoEntity>>
}
