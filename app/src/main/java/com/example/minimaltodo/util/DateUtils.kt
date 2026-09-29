package com.example.minimaltodo.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Datum-Helfer. Fälligkeiten werden als Epoch-Day gespeichert (zeitzonenunabhängig). */
object DateUtils {

    fun today(zone: ZoneId = ZoneId.systemDefault()): Long = LocalDate.now(zone).toEpochDay()

    /** Material-DatePicker liefert UTC-Mitternacht in Millisekunden. */
    fun epochDayFromUtcMillis(millis: Long): Long =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()

    fun utcMillisFromEpochDay(epochDay: Long): Long =
        LocalDate.ofEpochDay(epochDay).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun isOverdue(dueDate: Long?, today: Long): Boolean = dueDate != null && dueDate < today

    fun formatTime(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    fun formatDate(epochDay: Long): String = try {
        LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    } catch (e: Exception) {
        "?"
    }

    /** Sichere Validierung importierter Werte. */
    fun isValidEpochDay(value: Long): Boolean = value in -365_243_219_162L..365_241_780_471L
    fun isValidMinuteOfDay(value: Int): Boolean = value in 0 until 24 * 60
}
