package com.example.minimaltodo.data

import com.example.minimaltodo.data.backup.BackupCodec
import com.example.minimaltodo.data.backup.BackupFormatException
import com.example.minimaltodo.data.backup.ImportPlanner
import com.example.minimaltodo.data.backup.Json
import com.example.minimaltodo.data.local.Priority
import com.example.minimaltodo.data.local.SortMode
import com.example.minimaltodo.data.local.TodoType
import com.example.minimaltodo.testutil.TestIds
import com.example.minimaltodo.testutil.group
import com.example.minimaltodo.testutil.todo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupTest {

    private val ids = TestIds()

    @Test
    fun json_roundTripWithSpecialCharacters() {
        val value = mapOf("a" to "Zeile 1\n\"Zitat\" \\ ☐ äöü \u0001", "n" to 42L, "d" to 1.5, "b" to true, "x" to null, "l" to listOf(1L, 2L))
        assertEquals(value, Json.parse(Json.stringify(value)))
    }

    @Test
    fun json_unicodeEscapes() {
        assertEquals("ä", Json.parse("\"\\u00e4\""))
    }

    @Test
    fun backup_roundTripKeepsAllFields() {
        val original = listOf(
            group("g", "Gruppe").copy(favorite = true, childSort = SortMode.ALPHABETICAL, collapsed = true),
            todo("t", "Aufgabe", "g", completed = true, content = "Notiz\nmit Zeilen").copy(
                priority = Priority.HIGH, dueDate = 20_000L, dueTime = 600, pinned = true, repeatRule = "WEEKLY",
            ),
        )
        val json = BackupCodec.encode(original, exportedAt = 99L)
        assertTrue(json.contains("\"version\":1"))
        val decoded = BackupCodec.decode(json, now = 5L, newId = ids::newId)
        assertEquals(original, decoded)
    }

    @Test
    fun decode_invalidJson_throws() {
        listOf("", "{", "not json", "[1,2]", "{\"version\":1}", "{\"version\":99,\"items\":[]}", "{\"items\":[]}")
            .forEach { input ->
                try {
                    BackupCodec.decode(input, 0L, ids::newId)
                    fail("Erwartete Exception für: $input")
                } catch (_: BackupFormatException) {
                }
            }
    }

    @Test
    fun decode_isTolerantForBrokenFields() {
        val json = """
            {"version":1,"items":[
              {"type":"UNKNOWN","title":"x","dueDate":"morgen","dueTime":99999,"priority":"URGENT"},
              "kein objekt",
              {"id":"g","type":"GROUP","title":"G","completed":true}
            ]}
        """.trimIndent()
        val items = BackupCodec.decode(json, 7L, ids::newId)
        assertEquals(2, items.size)
        assertEquals(TodoType.TODO, items[0].type)
        assertNull(items[0].dueDate)
        assertNull(items[0].dueTime)
        assertEquals(Priority.NONE, items[0].priority)
        assertEquals(7L, items[0].createdAt)
        // Gruppen können nicht erledigt sein
        assertEquals(false, items[1].completed)
    }

    @Test
    fun planner_resolvesConflictsAndRemapsParents() {
        val imported = listOf(group("g"), todo("t", parent = "g"))
        val planned = ImportPlanner.plan(imported, existingIds = setOf("g", "t"), rootSortStart = 5000, newId = ids::newId)
        val g = planned.first { it.type == TodoType.GROUP }
        val t = planned.first { it.type == TodoType.TODO }
        assertTrue(g.id !in setOf("g", "t"))
        assertTrue(t.id !in setOf("g", "t"))
        assertEquals(g.id, t.parentId)
        assertEquals(6000L, g.sortOrder)
    }

    @Test
    fun planner_duplicateIdsInFile_missingParents_andCycles() {
        val imported = listOf(
            todo("a"),
            todo("a"), // Duplikat
            todo("orphan", parent = "nope"),
            todo("x", parent = "y"),
            todo("y", parent = "x"),
        )
        val planned = ImportPlanner.plan(imported, emptySet(), 0, ids::newId)
        assertEquals(5, planned.map { it.id }.toSet().size)
        assertNull(planned.first { it.title == "orphan" }.parentId)
        val x = planned.first { it.title == "x" }
        val y = planned.first { it.title == "y" }
        assertTrue("Zyklus muss aufgebrochen sein", x.parentId == null || y.parentId == null)
    }
}
