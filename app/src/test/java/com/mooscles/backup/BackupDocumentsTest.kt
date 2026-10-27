package com.mooscles.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupDocumentsTest {
    @get:Rule val folder = TemporaryFolder()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun uri(file: File) = Uri.fromFile(file).toString()

    @Test fun writeThenReadRoundTrips() = runBlocking {
        val file = File(folder.root, "backup.json")
        val text = "{\"name\": \"Supino inclinado — 100 kg ✓\"}"

        BackupDocuments.write(context, uri(file), text)

        assertEquals(text, BackupDocuments.read(context, uri(file)))
    }

    @Test fun writeReplacesLongerExistingContent() = runBlocking {
        val file = File(folder.root, "backup.json")
        file.writeText("x".repeat(500))

        BackupDocuments.write(context, uri(file), "{}")

        assertEquals("{}", BackupDocuments.read(context, uri(file)))
    }

    @Test fun readRejectsOversizedDocument() = runBlocking {
        val file = File(folder.root, "big.json").apply { writeText("x".repeat(11)) }
        try {
            BackupDocuments.read(context, uri(file), maxBytes = 10)
            fail("A document larger than maxBytes must be refused")
        } catch (expected: BackupException.Corrupted) {
        }
        assertEquals("x".repeat(10), BackupDocuments.read(context, uri(File(folder.root, "ten.json").apply { writeText("x".repeat(10)) }), maxBytes = 10))
    }

    @Test fun readRejectsInvalidUtf8() = runBlocking {
        val file = File(folder.root, "latin1.json").apply { writeBytes(byteArrayOf('{'.code.toByte(), 0xC3.toByte(), 0x28, '}'.code.toByte())) }
        try {
            BackupDocuments.read(context, uri(file))
            fail("Invalid UTF-8 must be refused")
        } catch (expected: BackupException.Corrupted) {
        }
    }

    @Test fun suggestedNameUsesLocalDate() {
        assertEquals("mooscles-backup-2026-10-27.json", suggestedBackupFileName(LocalDate.of(2026, 10, 27)))
        assertEquals("mooscles-backup-2027-01-05.json", suggestedBackupFileName(LocalDate.of(2027, 1, 5)))
    }
}
