package com.example.minimaltodo.util

import com.example.minimaltodo.data.local.SortMode
import com.example.minimaltodo.testutil.group
import com.example.minimaltodo.testutil.todo
import org.junit.Assert.assertEquals
import org.junit.Test

class TreeBuilderTest {

    private val items = listOf(
        todo("milch", sort = 1),
        group("haus", sort = 2),
        todo("keller", parent = "haus", sort = 1),
        todo("lampe", parent = "haus", sort = 2, completed = true),
        group("urlaub", sort = 3, collapsed = true),
        todo("hotel", parent = "urlaub", sort = 1),
    )

    @Test
    fun build_flattensExpandedBranchesWithDepth() {
        val rows = TreeBuilder.build(items, null, SortMode.MANUAL, hideCompleted = false)
        assertEquals(listOf("milch", "haus", "keller", "lampe", "urlaub"), rows.map { it.item.id })
        assertEquals(listOf(0, 0, 1, 1, 0), rows.map { it.depth })
        val haus = rows.first { it.item.id == "haus" }
        assertEquals(2, haus.todoTotal)
        assertEquals(1, haus.todoDone)
        val urlaub = rows.first { it.item.id == "urlaub" }
        assertEquals(true, urlaub.hasChildren)
        assertEquals(false, urlaub.expanded)
    }

    @Test
    fun build_hidesCompleted() {
        val rows = TreeBuilder.build(items, null, SortMode.MANUAL, hideCompleted = true)
        assertEquals(listOf("milch", "haus", "keller", "urlaub"), rows.map { it.item.id })
    }

    @Test
    fun build_forGroupRoot() {
        val rows = TreeBuilder.build(items, "haus", SortMode.MANUAL, hideCompleted = false)
        assertEquals(listOf("keller", "lampe"), rows.map { it.item.id })
        assertEquals(listOf(0, 0), rows.map { it.depth })
    }

    @Test
    fun build_openFirstAndPinnedFirst() {
        val list = listOf(
            todo("a", sort = 1, completed = true),
            todo("b", sort = 2),
            todo("c", sort = 3, pinned = true),
        )
        val rows = TreeBuilder.build(list, null, SortMode.OPEN_FIRST, false)
        assertEquals(listOf("c", "b", "a"), rows.map { it.item.id })
        val rows2 = TreeBuilder.build(list, null, SortMode.COMPLETED_FIRST, false)
        assertEquals(listOf("c", "a", "b"), rows2.map { it.item.id })
    }

    @Test
    fun build_perGroupSortOverridesDefault() {
        val list = listOf(
            group("g", sort = 1).copy(childSort = SortMode.ALPHABETICAL),
            todo("z", title = "Zebra", parent = "g", sort = 1),
            todo("a", title = "affe", parent = "g", sort = 2),
            todo("r2", title = "Z", sort = 5),
            todo("r1", title = "A", sort = 6),
        )
        val rows = TreeBuilder.build(list, null, SortMode.MANUAL, false)
        assertEquals(listOf("g", "a", "z", "r2", "r1"), rows.map { it.item.id })
    }

    @Test
    fun build_orphansAndCyclesDoNotCrashOrDisappear() {
        val list = listOf(
            todo("orphan", parent = "missing"),
            todo("x", parent = "y"),
            todo("y", parent = "x"),
            todo("self", parent = "self"),
        )
        val rows = TreeBuilder.build(list, null, SortMode.MANUAL, false)
        assertEquals(setOf("orphan", "self"), rows.map { it.item.id }.toSet())
    }

    @Test
    fun build_handlesTenThousandItemsQuickly() {
        val big = ArrayList<com.example.minimaltodo.data.local.TodoEntity>()
        for (g in 0 until 100) {
            big += group("g$g", sort = g.toLong())
            for (t in 0 until 99) big += todo("t$g-$t", parent = "g$g", sort = t.toLong())
        }
        val start = System.nanoTime()
        val rows = TreeBuilder.build(big, null, SortMode.OPEN_FIRST, false)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals(10_000, rows.size)
        assert(ms < 2000) { "zu langsam: $ms ms" }
    }

    @Test
    fun treeIndex_pathOf() {
        val index = TreeIndex(
            listOf(group("haus", "Haus"), group("garten", "Garten", parent = "haus"), todo("rasen", parent = "garten")),
        )
        assertEquals("Haus / Garten", index.pathOf("rasen"))
        assertEquals("", index.pathOf("haus"))
    }
}
