package com.mooscles.backup

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The only Android-aware file of this package: reads and writes documents the user picked. */
object BackupDocuments {
    suspend fun write(context: Context, uri: String, text: String) = withContext(Dispatchers.IO) {
        // "wt" truncates, so replacing a longer file leaves no trailing bytes behind.
        val output = context.contentResolver.openOutputStream(Uri.parse(uri), "wt")
            ?: throw IOException("Could not open the selected file.")
        output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    suspend fun read(context: Context, uri: String, maxBytes: Long = 64L * 1024 * 1024): String = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(Uri.parse(uri))
            ?: throw IOException("Could not open the selected file.")
        val bytes = input.use {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = it.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) throw BackupException.Corrupted("document is larger than $maxBytes bytes")
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            throw BackupException.Corrupted("document is not valid UTF-8")
        }
    }
}

fun suggestedBackupFileName(date: LocalDate): String = "mooscles-backup-$date.json"
