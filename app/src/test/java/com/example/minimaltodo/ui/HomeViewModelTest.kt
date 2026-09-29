package com.example.minimaltodo.ui

import com.example.minimaltodo.data.local.SortMode
import com.example.minimaltodo.data.local.TodoType
import com.example.minimaltodo.data.repository.TodoRepository
import com.example.minimaltodo.testutil.DirectTransactionRunner
import com.example.minimaltodo.testutil.FakeSettingsRepository
import com.example.minimaltodo.testutil.FakeTodoDao
import com.example.minimaltodo.testutil.MainDispatcherRule
import com.example.minimaltodo.testutil.TestClock
import com.example.minimaltodo.testutil.TestIds
import com.example.minimaltodo.ui.common.ItemEvent
import com.example.minimaltodo.ui.home.HomeViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val dao = FakeTodoDao()
    private val settings = FakeSettingsRepository()
    private val repo = TodoRepository(dao, DirectTransactionRunner, TestClock()::tick, TestIds()::newId)
    private val vm = HomeViewModel(repo, settings)

    private fun items() = dao.state.value.values
    private fun byTitle(title: String) = items().first { it.title == title || it.content == title }

    @Test
    fun create_addsItemsOfAllTypes() {
        vm.create(TodoType.TODO, "Milch", null)
        vm.create(TodoType.GROUP, "Haus", null)
        vm.create(TodoType.TEXT, "Eine Notiz", null)
        vm.create(TodoType.TODO, "   ", null) // leer → ignoriert
        assertEquals(3, items().size)
        assertEquals(TodoType.GROUP, byTitle("Haus").type)
        assertEquals("", byTitle("Eine Notiz").title)
    }

    @Test
    fun create_multilineTodo_splitsTitleAndNote() {
        vm.create(TodoType.TODO, "Urlaub\nPass nicht vergessen", null)
        val t = items().single()
        assertEquals("Urlaub", t.title)
        assertEquals("Pass nicht vergessen", t.content)
    }

    @Test
    fun complete_toggles_andSuggestsParent() = runBlocking {
        vm.create(TodoType.TODO, "Parent", null)
        val parent = byTitle("Parent")
        vm.create(TodoType.TODO, "Child", parent.id)
        val child = byTitle("Child")
        vm.toggleCompleted(child)
        assertTrue(byTitle("Child").completed)
        val event = withTimeout(1_000) {
            var e: ItemEvent
            do { e = vm.events.first() } while (e !is ItemEvent.SuggestCompleteParent)
            e
        }
        assertEquals(parent.id, (event as ItemEvent.SuggestCompleteParent).parent.id)
        assertFalse(byTitle("Parent").completed)
        vm.toggleCompleted(byTitle("Child"))
        assertFalse(byTitle("Child").completed)
    }

    @Test
    fun favoriteAndPin() {
        vm.create(TodoType.TODO, "A", null)
        vm.toggleFavorite(byTitle("A"))
        vm.togglePinned(byTitle("A"))
        assertTrue(byTitle("A").favorite)
        assertTrue(byTitle("A").pinned)
        vm.toggleFavorite(byTitle("A"))
        assertFalse(byTitle("A").favorite)
    }

    @Test
    fun move_viaMoveDialogState() {
        vm.create(TodoType.GROUP, "G", null)
        vm.create(TodoType.GROUP, "Sub", byTitle("G").id)
        vm.create(TodoType.TODO, "T", null)
        vm.openMove(byTitle("G"))
        val state = vm.moveState.value
        assertNotNull(state)
        // eigener Unterbaum (G, Sub) ist kein Ziel, Root schon
        assertEquals(listOf(null, byTitle("T").id), state!!.targets.map { it.id })

        vm.openMove(byTitle("T"))
        vm.moveTo(byTitle("Sub").id)
        assertNull(vm.moveState.value)
        assertEquals(byTitle("Sub").id, byTitle("T").parentId)
    }

    @Test
    fun duplicate_createsCopyWithSubtree() {
        vm.create(TodoType.GROUP, "G", null)
        vm.create(TodoType.TODO, "T", byTitle("G").id)
        vm.duplicate(byTitle("G"))
        assertEquals(2, items().count { it.title == "G" })
        assertEquals(2, items().count { it.title == "T" })
    }

    @Test
    fun delete_withChildren_asksFirst() {
        vm.create(TodoType.GROUP, "G", null)
        vm.create(TodoType.TODO, "T", byTitle("G").id)
        vm.requestDelete(byTitle("G"))
        assertEquals("G", vm.pendingDelete.value?.title)
        assertEquals(2, items().size)
        vm.confirmDelete(includeChildren = false)
        assertEquals(listOf("T"), items().map { it.title })
        assertNull(byTitle("T").parentId)
    }

    @Test
    fun uiState_buildsTreeAndRespectsSettings() = runBlocking {
        vm.create(TodoType.TODO, "Offen", null)
        vm.create(TodoType.TODO, "Erledigt", null)
        vm.toggleCompleted(byTitle("Erledigt"))
        vm.setHideCompleted(true)
        val state = withTimeout(2_000) { vm.uiState.first { !it.loading && it.hideCompleted } }
        assertEquals(listOf("Offen"), state.rows.map { it.item.title })
        vm.setSort(SortMode.ALPHABETICAL)
        assertEquals(SortMode.ALPHABETICAL, settings.settings.value.defaultSort)
    }
}
