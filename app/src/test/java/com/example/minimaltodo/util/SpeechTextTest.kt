package com.example.minimaltodo.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class SpeechTextTest {

    @Test
    fun merge_intoEmptyField_capitalizes() {
        assertEquals("Milch kaufen", SpeechText.merge("", "milch kaufen", Locale.GERMANY))
        assertEquals("Äpfel", SpeechText.merge("  ", " äpfel ", Locale.GERMANY))
    }

    @Test
    fun merge_appendsWithSingleSpace() {
        assertEquals("Milch und Brot", SpeechText.merge("Milch", "und Brot"))
        assertEquals("Milch und Brot", SpeechText.merge("Milch ", "und Brot"))
        assertEquals("Zeile\nneu", SpeechText.merge("Zeile\n", "neu"))
    }

    @Test
    fun merge_ignoresEmptyRecognition() {
        assertEquals("Milch", SpeechText.merge("Milch", "   "))
    }

    @Test
    fun firstResult_skipsBlankEntries() {
        assertEquals("Hallo", SpeechText.firstResult(listOf(null, " ", " Hallo ", "Welt")))
        assertNull(SpeechText.firstResult(emptyList()))
        assertNull(SpeechText.firstResult(null))
    }
}
