package com.example.minimaltodo.util

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Liest/schreibt Dateien, die der Nutzer über das System (ACTION_OPEN_DOCUMENT /
 * ACTION_CREATE_DOCUMENT) ausgewählt hat. Keine Speicher-Berechtigungen nötig.
 */
class DocumentIo(private val resolver: ContentResolver) {

    suspend fun writeText(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        val stream = resolver.openOutputStream(uri, "wt") ?: throw IOException("Datei nicht beschreibbar")
        stream.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
    }

    suspend fun readText(uri: Uri, maxBytes: Long = MAX_IMPORT_BYTES): String = withContext(Dispatchers.IO) {
        val stream = resolver.openInputStream(uri) ?: throw IOException("Datei nicht lesbar")
        stream.use { input ->
            val bytes = input.readNBytesCompat(maxBytes + 1)
            if (bytes.size > maxBytes) throw IOException("Datei zu groß")
            String(bytes, Charsets.UTF_8)
        }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (total < limit) {
            val read = read(buffer, 0, minOf(buffer.size.toLong(), limit - total).toInt())
            if (read < 0) break
            out.write(buffer, 0, read)
            total += read
        }
        return out.toByteArray()
    }

    companion object {
        const val MAX_IMPORT_BYTES = 50L * 1024 * 1024
    }
}
