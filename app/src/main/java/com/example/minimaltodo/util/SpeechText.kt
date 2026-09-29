package com.example.minimaltodo.util

import java.util.Locale

/** Reine Hilfsfunktionen für die Spracheingabe (ohne Android-Abhängigkeit, testbar). */
object SpeechText {

    /** Erster sinnvoller Treffer der Spracherkennung. */
    fun firstResult(results: List<String?>?): String? =
        results?.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }

    /**
     * Fügt erkannten Text an vorhandenen Text an.
     * Leeres Feld → erkannter Text mit großem Anfangsbuchstaben;
     * sonst durch ein Leerzeichen (bzw. bei Zeilenende ohne) angehängt.
     */
    fun merge(existing: String, spoken: String, locale: Locale = Locale.getDefault()): String {
        val s = spoken.trim()
        if (s.isEmpty()) return existing
        if (existing.isBlank()) return s.replaceFirstChar { it.titlecase(locale) }
        val separator = if (existing.endsWith("\n") || existing.endsWith(" ")) "" else " "
        return existing + separator + s
    }
}
