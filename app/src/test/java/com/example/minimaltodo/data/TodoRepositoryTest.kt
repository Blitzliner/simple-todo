package com.example.minimaltodo.data

import com.example.minimaltodo.data.backup.ImportMode
import com.example.minimaltodo.data.local.TodoEntity
import com.example.minimaltodo.data.local.TodoType
import com.example.minimaltodo.data.repository.MoveResult
import com.example.minimaltodo.data.repository.TodoRepository
import com.example.minimaltodo.testutil.DirectTransactionRunner
import com.example.minimaltodo.testutil.FakeTodoDao
import com.example.minimaltodo.testutil.TestClock
import com.example.minimaltodo.testutil.TestIds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoRepositoryTest {

    private val dao = FakeTodoDao()
    private val clock = TestClock()
    private val ids = TestIds()
    private val repo = TodoRepository(dao, DirectTransactionRunner, clock::tick, ids::newId)

    private fun all() = dao.state.value
    private fun item(id: String) = all().getValue(id)

    @Test
    fun create_assignsIncreasingSortOrderPerParent() = runBlocking {
        val a = repo.create(TodoType.TODO, "A")
        val b = repo.create(TodoType.TODO, "B")
        val g = repo.create(TodoType.GROUP, "G")
        val g1 = repo.create(TodoType.TODO, "G1", parentId = g.id)
        assertTrue(b.sortOrder > a.sortOrder)
        assertTrue(g.sortOrder > b.sortOrder)
        assertEquals(TodoRepository.SORT_STEP, g1.sortOrder)
        assertEquals(g.id, g1.parentId)
    }

    @Test
    fun create_withUnknownParent_fallsBackToRoot() = runBlocking {
        val t = repo.create(TodoType.TODO, "x", parentId = "missing")
        assertNull(t.parentId)
    }

    @Test
    fun create_trimsTitle_andTextWithoutTitleIsAllowed() = runBlocking {
        val t = repo.create(TodoType.TODO, "  Milch  ")
        val text = repo.create(TodoType.TEXT, "", content = "Nur Inhalt")
        assertEquals("Milch", t.title)
        assertEquals("", text.title)
        assertEquals("Nur Inhalt", text.content)
    }

    @Test
    fun nestedHierarchy_rootGroupGroupTodo() = runBlocking {
        val g1 = repo.create(TodoType.GROUP, "Haus")
        val g2 = repo.create(TodoType.GROUP, "Einkauf", g1.id)
        val t = repo.create(TodoType.TODO, "Schrauben", g2.id)
        assertEquals(listOf(g1.id), dao.getRootItems().map { it.id })
        assertEquals(listOf(g2.id), dao.getChildren(g1.id).map { it.id })
        assertEquals(listOf(t.id), dao.getChildren(g2.id).map { it.id })
    }

    @Test
    fun modify_updatesTimestamp_andKeepsParent() = runBlocking {
        val t = repo.create(TodoType.TODO, "A")
        val updated = repo.modify(t.id) { it.copy(title = "B", parentId = "hack") }!!
        assertEquals("B", updated.title)
        assertNull(updated.parentId)
        assertTrue(updated.updatedAt > t.updatedAt)
        assertEquals("b\n", item(t.id).searchText)
    }

    @Test
    fun setCompleted_onlyAffectsTodos() = runBlocking {
        val t = repo.create(TodoType.TODO, "A")
        val g = repo.create(TodoType.GROUP, "G")
        repo.setCompleted(t.id, true)
        repo.setCompleted(g.id, true)
        assertTrue(item(t.id).completed)
        assertFalse(item(g.id).completed)
    }

    @Test
    fun parentReadyToComplete_onlyWhenAllSubtasksDone() = runBlocking {
        val p = repo.create(TodoType.TODO, "Urlaub")
        val a = repo.create(TodoType.TODO, "Hotel", p.id)
        val b = repo.create(TodoType.TODO, "Flug", p.id)
        repo.setCompleted(a.id, true)
        assertNull(repo.parentReadyToComplete(a.id))
        repo.setCompleted(b.id, true)
        assertEquals(p.id, repo.parentReadyToComplete(b.id)?.id)
        // nicht automatisch erledigt
        assertFalse(item(p.id).completed)
    }

    // ------------------------------------------------------------ Löschen

    @Test
    fun delete_groupWithChildren_removesWholeSubtree() = runBlocking {
        val keep = repo.create(TodoType.TODO, "keep")
        val g = repo.create(TodoType.GROUP, "G")
        val g2 = repo.create(TodoType.GROUP, "G2", g.id)
        repo.create(TodoType.TODO, "t1", g.id)
        repo.create(TodoType.TODO, "t2", g2.id)
        val snapshot = repo.delete(g.id, includeChildren = true)!!
        assertEquals(setOf(keep.id), all().keys)
        assertEquals(4, snapshot.deleted.size)

        repo.restore(snapshot)
        assertEquals(5, all().size)
    }

    @Test
    fun delete_onlyGroup_movesChildrenToParent() = runBlocking {
        // A └── B └── C   →  B löschen → C ist Kind von A
        val a = repo.create(TodoType.GROUP, "A")
        val b = repo.create(TodoType.GROUP, "B", a.id)
        val c = repo.create(TodoType.TODO, "C", b.id)
        repo.delete(b.id, includeChildren = false)
        assertNull(all()[b.id])
        assertEquals(a.id, item(c.id).parentId)
    }

    @Test
    fun delete_onlyRootGroup_childrenBecomeRoot_atGroupPosition() = runBlocking {
        val x = repo.create(TodoType.TODO, "X")
        val b = repo.create(TodoType.GROUP, "B")
        val y = repo.create(TodoType.TODO, "Y")
        val c1 = repo.create(TodoType.TODO, "C1", b.id)
        val c2 = repo.create(TodoType.TODO, "C2", b.id)
        val snapshot = repo.delete(b.id, includeChildren = false)!!
        assertNull(item(c1.id).parentId)
        assertEquals(listOf(x.id, c1.id, c2.id, y.id), dao.getRootItems().map { it.id })

        repo.restore(snapshot)
        assertEquals(b.id, item(c1.id).parentId)
        assertEquals(listOf(x.id, b.id, y.id), dao.getRootItems().map { it.id })
    }

    // ------------------------------------------------------------ Verschieben

    @Test
    fun move_intoOtherGroup_andBackToRoot() = runBlocking {
        val g = repo.create(TodoType.GROUP, "G")
        val existing = repo.create(TodoType.TODO, "E", g.id)
        val t = repo.create(TodoType.TODO, "T")
        assertEquals(MoveResult.MOVED, repo.move(t.id, g.id))
        assertEquals(g.id, item(t.id).parentId)
        assertTrue(item(t.id).sortOrder > existing.sortOrder)
        assertEquals(MoveResult.UNCHANGED, repo.move(t.id, g.id))
        assertEquals(MoveResult.MOVED, repo.move(t.id, null))
        assertNull(item(t.id).parentId)
    }

    @Test
    fun move_preventsSelfParentAndCycles() = runBlocking {
        val a = repo.create(TodoType.GROUP, "A")
        val b = repo.create(TodoType.GROUP, "B", a.id)
        val c = repo.create(TodoType.GROUP, "C", b.id)
        assertEquals(MoveResult.INVALID_TARGET, repo.move(a.id, a.id))
        assertEquals(MoveResult.INVALID_TARGET, repo.move(a.id, b.id))
        assertEquals(MoveResult.INVALID_TARGET, repo.move(a.id, c.id))
        assertFalse(repo.canMove(a.id, c.id))
        assertTrue(repo.canMove(c.id, null))
        assertNull(item(a.id).parentId)
    }

    @Test
    fun move_toMissingTarget_isRejected() = runBlocking {
        val a = repo.create(TodoType.TODO, "A")
        assertEquals(MoveResult.INVALID_TARGET, repo.move(a.id, "nope"))
        assertEquals(MoveResult.NOT_FOUND, repo.move("nope", null))
    }

    @Test
    fun moveWithinSiblings_reordersAndRenumbers() = runBlocking {
        val a = repo.create(TodoType.TODO, "A")
        val b = repo.create(TodoType.TODO, "B")
        val c = repo.create(TodoType.TODO, "C")
        assertTrue(repo.moveWithinSiblings(c.id, -1))
        assertEquals(listOf(a.id, c.id, b.id), dao.getRootItems().map { it.id })
        assertFalse(repo.moveWithinSiblings(a.id, -1))
        assertEquals(listOf(1000L, 2000L, 3000L), dao.getRootItems().map { it.sortOrder })
    }

    // ------------------------------------------------------------ Duplizieren

    @Test
    fun duplicate_copiesSubtreeWithNewIds() = runBlocking {
        val g = repo.create(TodoType.GROUP, "G")
        val t = repo.create(TodoType.TODO, "T", g.id)
        repo.setCompleted(t.id, true)
        val sub = repo.create(TodoType.GROUP, "Sub", g.id)
        repo.create(TodoType.TODO, "S1", sub.id)

        val copy = repo.duplicate(g.id)!!
        assertNotEquals(g.id, copy.id)
        assertNull(copy.parentId)
        assertTrue(copy.sortOrder > g.sortOrder)
        assertEquals(8, all().size)
        val copiedChildren = dao.getChildren(copy.id)
        assertEquals(setOf("T", "Sub"), copiedChildren.map { it.title }.toSet())
        assertTrue(copiedChildren.none { it.completed })
        val copiedSub = copiedChildren.first { it.title == "Sub" }
        assertEquals(listOf("S1"), dao.getChildren(copiedSub.id).map { it.title })
        // Original unverändert
        assertTrue(item(t.id).completed)
    }

    @Test
    fun duplicate_withoutChildren() = runBlocking {
        val g = repo.create(TodoType.GROUP, "G")
        repo.create(TodoType.TODO, "T", g.id)
        val copy = repo.duplicate(g.id, includeChildren = false)!!
        assertEquals(0, dao.countChildren(copy.id))
    }

    // ------------------------------------------------------------ Suche

    @Test
    fun search_isCaseInsensitive_includingUmlauts_andContent() = runBlocking {
        repo.create(TodoType.TODO, "Äpfel kaufen")
        repo.create(TodoType.TEXT, "Notiz", content = "Der RASEN muss gemäht werden")
        repo.create(TodoType.TODO, "100% fertig")
        assertEquals(listOf("Äpfel kaufen"), repo.search("äPFEL").first().map { it.title })
        assertEquals(listOf("Notiz"), repo.search("rasen").first().map { it.title })
        assertEquals(listOf("100% fertig"), repo.search("0%").first().map { it.title })
    }

    @Test
    fun searchPattern_escapesWildcards() {
        assertEquals("%a\\%b\\_c\\\\%", TodoRepository.searchPattern(" A%b_c\\ "))
    }

    // ------------------------------------------------------------ Import

    @Test
    fun import_add_resolvesIdConflicts_andKeepsExistingData() = runBlocking {
        val existing = repo.create(TodoType.TODO, "Bestehend")
        val backup = repo.exportJson()
        val parsed = repo.parseBackup(backup)
        assertEquals(1, repo.import(parsed, ImportMode.ADD))
        assertEquals(2, all().size)
        assertEquals("Bestehend", item(existing.id).title)
        assertTrue(all().values.all { it.title == "Bestehend" })
    }

    @Test
    fun import_add_preservesHierarchyWithRemappedIds() = runBlocking {
        val g = repo.create(TodoType.GROUP, "G")
        repo.create(TodoType.TODO, "T", g.id)
        val parsed = repo.parseBackup(repo.exportJson())
        repo.import(parsed, ImportMode.ADD)
        val groups = all().values.filter { it.type == TodoType.GROUP }
        assertEquals(2, groups.size)
        groups.forEach { assertEquals(1, dao.countChildren(it.id)) }
    }

    @Test
    fun import_replace_replacesAllData() = runBlocking {
        repo.create(TodoType.TODO, "Alt")
        val backup = """{"version":1,"exportedAt":1,"items":[{"id":"x","type":"TODO","title":"Neu"}]}"""
        repo.import(repo.parseBackup(backup), ImportMode.REPLACE)
        assertEquals(listOf("Neu"), all().values.map { it.title })
    }

    @Test
    fun shareText_usesSubtree() = runBlocking {
        val g = repo.create(TodoType.GROUP, "Einkauf")
        repo.create(TodoType.TODO, "Milch", g.id)
        assertEquals("Einkauf\n\n☐ Milch", repo.shareText(g.id, com.example.minimaltodo.data.local.SortMode.MANUAL))
    }

    @Test
    fun expandPath_expandsAllAncestors() = runBlocking {
        val a = repo.create(TodoType.GROUP, "A")
        val b = repo.create(TodoType.GROUP, "B", a.id)
        val c = repo.create(TodoType.TODO, "C", b.id)
        repo.setCollapsed(a.id, true)
        repo.setCollapsed(b.id, true)
        repo.expandPath(c.id)
        assertFalse(item(a.id).collapsed)
        assertFalse(item(b.id).collapsed)
    }

    @Suppress("unused")
    private fun TodoEntity.dump() = "$title($id)<-$parentId"

    @Test
    fun get_returnsNullForMissing() = runBlocking {
        assertNull(repo.get("x"))
        assertNotNull(repo.create(TodoType.TODO, "a").let { repo.get(it.id) })
    }
}
