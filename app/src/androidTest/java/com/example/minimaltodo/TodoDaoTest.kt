package com.example.minimaltodo

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.minimaltodo.data.local.AppDatabase
import com.example.minimaltodo.data.local.Priority
import com.example.minimaltodo.data.local.TodoDao
import com.example.minimaltodo.data.local.TodoEntity
import com.example.minimaltodo.data.local.TodoType
import com.example.minimaltodo.data.repository.TodoRepository
import com.example.minimaltodo.data.repository.TransactionRunner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Room-Tests gegen eine echte (In-Memory-)SQLite-Datenbank. */
@RunWith(AndroidJUnit4::class)
class TodoDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TodoDao
    private lateinit var repo: TodoRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.todoDao()
        repo = TodoRepository(
            dao,
            object : TransactionRunner {
                override suspend fun <R> run(block: suspend () -> R): R = db.withTransaction { block() }
            },
        )
    }

    @After
    fun tearDown() = db.close()

    private fun entity(
        id: String,
        parent: String? = null,
        type: TodoType = TodoType.TODO,
        title: String = id,
        sort: Long = 1000,
        completed: Boolean = false,
        favorite: Boolean = false,
        due: Long? = null,
        content: String? = null,
    ) = TodoEntity(
        id = id, parentId = parent, type = type, title = title, content = content, completed = completed,
        favorite = favorite, dueDate = due, sortOrder = sort, createdAt = 1, updatedAt = 1,
    )

    @Test
    fun insertGetUpdateDelete() = runTest {
        dao.insert(entity("a", priority = Priority.HIGH))
        assertEquals(Priority.HIGH, dao.getById("a")?.priority)
        dao.update(dao.getById("a")!!.copy(title = "Neu"))
        assertEquals("Neu", dao.getById("a")?.title)
        dao.delete(dao.getById("a")!!)
        assertNull(dao.getById("a"))
    }

    private fun entity(id: String, priority: Priority) = entity(id).copy(priority = priority)

    @Test
    fun childrenAndRootItems_sortedByPinAndOrder() = runTest {
        dao.insertAll(
            listOf(
                entity("r2", sort = 2000),
                entity("r1", sort = 1000),
                entity("g", type = TodoType.GROUP, sort = 3000),
                entity("c1", parent = "g", sort = 2000),
                entity("c2", parent = "g", sort = 1000).copy(pinned = true),
            ),
        )
        assertEquals(listOf("r1", "r2", "g"), dao.getRootItems().map { it.id })
        assertEquals(listOf("c2", "c1"), dao.getChildren("g").map { it.id })
        assertEquals(listOf("c2", "c1"), dao.observeChildren("g").first().map { it.id })
        assertEquals(3000L, dao.maxSortOrder(null))
        assertEquals(2000L, dao.maxSortOrder("g"))
        assertEquals(0L, dao.maxSortOrder("leer"))
    }

    @Test
    fun deepHierarchy_rootGroupGroupTodo() = runTest {
        val g1 = repo.create(TodoType.GROUP, "Root-Gruppe")
        val g2 = repo.create(TodoType.GROUP, "Untergruppe", g1.id)
        val t = repo.create(TodoType.TODO, "Aufgabe", g2.id)
        assertEquals(g2.id, dao.getById(t.id)?.parentId)
        assertEquals(g1.id, dao.getById(g2.id)?.parentId)
        assertEquals(listOf(g1.id), dao.getRootItems().map { it.id })
    }

    @Test
    fun search_isCaseInsensitiveInTitleAndContent() = runTest {
        dao.insertAll(
            listOf(
                entity("a", title = "Äpfel kaufen"),
                entity("b", title = "Notiz", content = "Rasen MÄHEN", type = TodoType.TEXT),
                entity("c", title = "Anderes"),
            ),
        )
        assertEquals(listOf("a"), dao.search(TodoRepository.searchPattern("äpfel"), 50).first().map { it.id })
        assertEquals(listOf("b"), dao.search(TodoRepository.searchPattern("mähen"), 50).first().map { it.id })
        assertTrue(dao.search(TodoRepository.searchPattern("xyz"), 50).first().isEmpty())
    }

    @Test
    fun search_escapesWildcards() = runTest {
        dao.insertAll(listOf(entity("a", title = "100% fertig"), entity("b", title = "1000 fertig")))
        assertEquals(listOf("a"), dao.search(TodoRepository.searchPattern("0%"), 50).first().map { it.id })
    }

    @Test
    fun favorites() = runTest {
        dao.insertAll(listOf(entity("a", favorite = true), entity("b"), entity("g", type = TodoType.GROUP, favorite = true)))
        assertEquals(setOf("a", "g"), dao.observeFavorites().first().map { it.id }.toSet())
    }

    @Test
    fun dueDates_todayAndOverdue() = runTest {
        val today = 20_000L
        dao.insertAll(
            listOf(
                entity("overdue", due = today - 3),
                entity("today", due = today),
                entity("tomorrow", due = today + 1),
                entity("doneOverdue", due = today - 1, completed = true),
                entity("noDate"),
            ),
        )
        assertEquals(listOf("overdue", "today"), dao.observeDueUntil(today).first().map { it.id })
        assertEquals(4, dao.observeWithDueDate().first().size)
    }

    @Test
    fun filters_openCompletedHighPriority() = runTest {
        dao.insertAll(
            listOf(
                entity("open"),
                entity("done", completed = true),
                entity("high").copy(priority = Priority.HIGH),
            ),
        )
        assertEquals(setOf("open", "high"), dao.observeOpenTodos().first().map { it.id }.toSet())
        assertEquals(listOf("done"), dao.observeCompletedTodos().first().map { it.id })
        assertEquals(listOf("high"), dao.observeHighPriority().first().map { it.id })
    }

    @Test
    fun deleteGroupWithChildren_andOnlyGroup() = runTest {
        val a = repo.create(TodoType.GROUP, "A")
        val b = repo.create(TodoType.GROUP, "B", a.id)
        val c = repo.create(TodoType.TODO, "C", b.id)
        repo.delete(b.id, includeChildren = false)
        assertEquals(a.id, dao.getById(c.id)?.parentId)

        repo.delete(a.id, includeChildren = true)
        assertEquals(0, dao.count())
    }

    @Test
    fun largeDataset_insertAndQuery() = runTest {
        val items = (0 until 10_000).map { entity("id$it", title = "Eintrag $it", sort = it.toLong()) }
        items.chunked(500).forEach { dao.insertAll(it) }
        assertEquals(10_000, dao.observeAll().first().size)
        assertEquals(1, dao.search(TodoRepository.searchPattern("eintrag 9999"), 50).first().size)
    }
}
