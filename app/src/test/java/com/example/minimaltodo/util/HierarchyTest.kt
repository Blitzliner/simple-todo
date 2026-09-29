package com.example.minimaltodo.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HierarchyTest {
    // A → B → C
    private val parents = mapOf("A" to null, "B" to "A", "C" to "B", "D" to null)

    @Test
    fun canMove_rejectsOwnSubtree() {
        assertFalse(Hierarchy.canMove("A", "A", parents::get))
        assertFalse(Hierarchy.canMove("A", "B", parents::get))
        assertFalse(Hierarchy.canMove("A", "C", parents::get))
        assertTrue(Hierarchy.canMove("C", "D", parents::get))
        assertTrue(Hierarchy.canMove("A", "D", parents::get))
        assertTrue(Hierarchy.canMove("B", null, parents::get))
    }

    @Test
    fun canMove_terminatesOnCorruptCycle() {
        val broken = mapOf("X" to "Y", "Y" to "X", "Z" to null)
        assertFalse(Hierarchy.canMove("Z", "X", broken::get))
    }

    @Test
    fun subtreeIds() {
        val children = mapOf("A" to listOf("B"), "B" to listOf("C"))
        assertEquals(setOf("A", "B", "C"), Hierarchy.subtreeIds("A") { children[it].orEmpty() }.toSet())
    }
}
