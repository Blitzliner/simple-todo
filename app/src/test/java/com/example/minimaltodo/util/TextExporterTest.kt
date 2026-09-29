package com.example.minimaltodo.util

import com.example.minimaltodo.testutil.group
import com.example.minimaltodo.testutil.text
import com.example.minimaltodo.testutil.todo
import org.junit.Assert.assertEquals
import org.junit.Test

class TextExporterTest {

    @Test
    fun share_singleTodo() {
        val e = TextExporter(listOf(todo("m", "Milch kaufen")))
        assertEquals("☐ Milch kaufen", e.shareText("m"))
    }

    @Test
    fun share_flatGroup() {
        val e = TextExporter(
            listOf(
                group("g", "Einkauf"),
                todo("1", "Milch", "g"),
                todo("2", "Brot", "g"),
                todo("3", "Eier", "g", completed = true),
            ),
        )
        assertEquals("Einkauf\n\n☐ Milch\n☐ Brot\n☑ Eier", e.shareText("g"))
    }

    @Test
    fun share_nestedGroupKeepsHierarchy() {
        val e = TextExporter(
            listOf(
                group("haus", "Haus"),
                group("garten", "Garten", "haus"),
                group("rasen", "Rasen", "garten"),
                group("hecke", "Hecke", "garten"),
                group("keller", "Keller", "haus"),
                group("werkzeug", "Werkzeug", "keller"),
            ),
        )
        val expected = """
            Haus
              ├─ Garten
              │   ├─ Rasen
              │   └─ Hecke
              └─ Keller
                  └─ Werkzeug
        """.trimIndent()
        assertEquals(expected, e.shareText("haus"))
    }

    @Test
    fun share_text() {
        val e = TextExporter(listOf(text("t", "", "Hallo\nWelt")))
        assertEquals("Hallo\nWelt", e.shareText("t"))
    }

    @Test
    fun exportTxt() {
        val e = TextExporter(
            listOf(
                group("g", "Einkauf"),
                todo("1", "Milch", "g"),
                todo("2", "Brot", "g"),
                todo("3", "Eier", "g", completed = true),
            ),
        )
        assertEquals("Einkauf\n\n☐ Milch\n☐ Brot\n☑ Eier\n", e.exportTxt())
    }

    @Test
    fun exportTxt_nestedIndentation() {
        val e = TextExporter(
            listOf(
                todo("r", "Rechnung"),
                group("h", "Haus"),
                todo("g", "Garten", "h"),
                todo("ra", "Rasen", "g"),
            ),
        )
        assertEquals("☐ Rechnung\n\nHaus\n\n☐ Garten\n  ☐ Rasen\n", e.exportTxt())
    }

    @Test
    fun exportMarkdown() {
        val e = TextExporter(
            listOf(
                group("g", "Einkauf"),
                todo("1", "Milch", "g"),
                todo("2", "Brot", "g"),
                todo("3", "Eier", "g", completed = true),
                group("h", "Haus"),
                todo("ga", "Garten", "h"),
                todo("ra", "Rasen", "ga"),
                todo("he", "Hecke", "ga"),
            ),
        )
        val expected = """
            # Einkauf

            - [ ] Milch
            - [ ] Brot
            - [x] Eier

            # Haus

            - [ ] Garten
              - [ ] Rasen
              - [ ] Hecke

        """.trimIndent()
        assertEquals(expected, e.exportMarkdown())
    }
}
