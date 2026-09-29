package com.example.minimaltodo.ui

import com.example.minimaltodo.data.local.Priority
import com.example.minimaltodo.data.local.TodoType
import com.example.minimaltodo.data.repository.TodoRepository
import com.example.minimaltodo.testutil.DirectTransactionRunner
import com.example.minimaltodo.testutil.FakeTodoDao
import com.example.minimaltodo.testutil.MainDispatcherRule
import com.example.minimaltodo.testutil.TestClock
import com.example.minimaltodo.testutil.TestIds
import com.example.minimaltodo.ui.editor.EditorViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EditorViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val dao = FakeTodoDao()
    private val repo = TodoRepository(dao, DirectTransactionRunner, TestClock()::tick, TestIds()::newId)

    @Test
    fun edit_marksDirty_andSaves() = runBlocking {
        val item = repo.create(TodoType.TODO, "Alt")
        val vm = EditorViewModel(repo, item.id)
        assertFalse(vm.state.value.loading)
        assertFalse(vm.state.value.isDirty)

        vm.update { it.copy(title = "Neu", content = "Notiz", priority = Priority.HIGH, dueDate = 100, dueTime = 480, favorite = true) }
        assertTrue(vm.state.value.isDirty)
        vm.save()

        val saved = dao.state.value.getValue(item.id)
        assertEquals("Neu", saved.title)
        assertEquals("Notiz", saved.content)
        assertEquals(Priority.HIGH, saved.priority)
        assertEquals(100L, saved.dueDate)
        assertEquals(480, saved.dueTime)
        assertTrue(saved.favorite)
        assertTrue(vm.state.value.saved)
        assertFalse(vm.state.value.isDirty)
    }

    @Test
    fun cannotSaveTodoWithoutTitle_butTextWithContentOnly() = runBlocking {
        val todo = repo.create(TodoType.TODO, "A")
        val vm = EditorViewModel(repo, todo.id)
        vm.update { it.copy(title = "  ") }
        assertFalse(vm.state.value.canSave)

        val text = repo.create(TodoType.TEXT, "", content = "Inhalt")
        val vm2 = EditorViewModel(repo, text.id)
        assertTrue(vm2.state.value.canSave)
    }

    @Test
    fun timeIsClearedWithoutDate() = runBlocking {
        val todo = repo.create(TodoType.TODO, "A")
        val vm = EditorViewModel(repo, todo.id)
        vm.update { it.copy(dueDate = null, dueTime = 600) }
        vm.save()
        assertEquals(null, dao.state.value.getValue(todo.id).dueTime)
    }

    @Test
    fun missingItem_isNotFound() {
        val vm = EditorViewModel(repo, "gibt-es-nicht")
        assertTrue(vm.state.value.notFound)
    }

    @Test
    fun subtasks_canBeAdded() {
        val todo = runBlocking { repo.create(TodoType.TODO, "Urlaub") }
        val vm = EditorViewModel(repo, todo.id)
        vm.addSubtask("Hotel buchen")
        val children = dao.state.value.values.filter { it.parentId == todo.id }
        assertEquals(listOf("Hotel buchen"), children.map { it.title })
    }
}
