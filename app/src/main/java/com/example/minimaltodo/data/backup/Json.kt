package com.example.minimaltodo.data.backup

/**
 * Minimaler, abhängigkeitsfreier JSON-Parser/-Writer.
 * Bewusst klein gehalten (statt kotlinx.serialization/Gson), damit die APK schlank bleibt
 * und das Backup-Format ohne Reflection auskommt (R8-sicher).
 *
 * Werte: Map<String, Any?>, List<Any?>, String, Long, Double, Boolean, null.
 */
object Json {

    class ParseException(message: String) : Exception(message)

    fun parse(text: String): Any? = Parser(text).parseDocument()

    fun stringify(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    // ------------------------------------------------------------ Writer

    private fun write(sb: StringBuilder, value: Any?) {
        when (value) {
            null -> sb.append("null")
            is String -> writeString(sb, value)
            is Boolean -> sb.append(value)
            is Int, is Long, is Short, is Byte -> sb.append(value.toString())
            is Double -> if (value.isFinite()) sb.append(value.toString()) else sb.append("null")
            is Float -> if (value.isFinite()) sb.append(value.toString()) else sb.append("null")
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, v) in value) {
                    if (!first) sb.append(',')
                    first = false
                    writeString(sb, k.toString())
                    sb.append(':')
                    write(sb, v)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (v in value) {
                    if (!first) sb.append(',')
                    first = false
                    write(sb, v)
                }
                sb.append(']')
            }
            else -> writeString(sb, value.toString())
        }
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    // ------------------------------------------------------------ Parser

    private class Parser(private val s: String) {
        private var pos = 0
        private var depth = 0

        fun parseDocument(): Any? {
            skipWs()
            if (pos < s.length && s[pos] == '﻿') pos++ // BOM
            skipWs()
            val value = parseValue()
            skipWs()
            if (pos != s.length) fail("Unerwartete Zeichen am Ende")
            return value
        }

        private fun parseValue(): Any? {
            skipWs()
            if (pos >= s.length) fail("Unerwartetes Ende")
            return when (val c = s[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) parseNumber() else fail("Unerwartetes Zeichen '$c'")
            }
        }

        private fun enter() {
            if (++depth > MAX_DEPTH) fail("Zu tief verschachtelt")
        }

        private fun parseObject(): Map<String, Any?> {
            enter()
            pos++ // {
            val map = LinkedHashMap<String, Any?>()
            skipWs()
            if (peek() == '}') { pos++; depth--; return map }
            while (true) {
                skipWs()
                if (peek() != '"') fail("Schlüssel erwartet")
                val key = parseString()
                skipWs()
                expect(':')
                map[key] = parseValue()
                skipWs()
                when (peek()) {
                    ',' -> pos++
                    '}' -> { pos++; depth--; return map }
                    else -> fail("',' oder '}' erwartet")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            enter()
            pos++ // [
            val list = ArrayList<Any?>()
            skipWs()
            if (peek() == ']') { pos++; depth--; return list }
            while (true) {
                list += parseValue()
                skipWs()
                when (peek()) {
                    ',' -> pos++
                    ']' -> { pos++; depth--; return list }
                    else -> fail("',' oder ']' erwartet")
                }
            }
        }

        private fun parseString(): String {
            pos++ // "
            val sb = StringBuilder()
            while (true) {
                if (pos >= s.length) fail("Nicht abgeschlossene Zeichenkette")
                val c = s[pos++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        if (pos >= s.length) fail("Ungültige Escape-Sequenz")
                        when (val e = s[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > s.length) fail("Ungültige Unicode-Sequenz")
                                val hex = s.substring(pos, pos + 4)
                                val code = hex.toIntOrNull(16) ?: fail("Ungültige Unicode-Sequenz")
                                sb.append(code.toChar())
                                pos += 4
                            }
                            else -> fail("Ungültige Escape-Sequenz '\\$e'")
                        }
                    }
                    c < ' ' -> fail("Steuerzeichen in Zeichenkette")
                    else -> sb.append(c)
                }
            }
        }

        private fun parseNumber(): Any {
            val start = pos
            if (peek() == '-') pos++
            while (pos < s.length && s[pos].isDigit()) pos++
            var isDecimal = false
            if (peek() == '.') {
                isDecimal = true
                pos++
                while (pos < s.length && s[pos].isDigit()) pos++
            }
            if (peek() == 'e' || peek() == 'E') {
                isDecimal = true
                pos++
                if (peek() == '+' || peek() == '-') pos++
                while (pos < s.length && s[pos].isDigit()) pos++
            }
            val text = s.substring(start, pos)
            if (!isDecimal) text.toLongOrNull()?.let { return it }
            return text.toDoubleOrNull() ?: fail("Ungültige Zahl '$text'")
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!s.startsWith(word, pos)) fail("Unerwartetes Literal")
            pos += word.length
            return value
        }

        private fun expect(c: Char) {
            if (peek() != c) fail("'$c' erwartet")
            pos++
        }

        private fun peek(): Char? = if (pos < s.length) s[pos] else null

        private fun skipWs() {
            while (pos < s.length && s[pos].let { it == ' ' || it == '\n' || it == '\r' || it == '\t' }) pos++
        }

        private fun fail(msg: String): Nothing = throw ParseException("$msg (Position $pos)")
    }

    private const val MAX_DEPTH = 64
}
