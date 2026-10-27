package com.mooscles.backup

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

object BackupCodec {
    const val FORMAT = "mooscles-backup"
    const val FORMAT_VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    fun decode(text: String): BackupFile {
        val root = try {
            json.parseToJsonElement(text.removePrefix("\uFEFF"))
        } catch (e: SerializationException) {
            throw BackupException.Corrupted(e.message ?: "unreadable JSON")
        } catch (e: IllegalArgumentException) {
            throw BackupException.Corrupted(e.message ?: "unreadable JSON")
        }
        if (root !is JsonObject) throw BackupException.NotABackup
        val format = (root["format"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (format != FORMAT) throw BackupException.NotABackup
        val version = (root["formatVersion"] as? JsonPrimitive)?.intOrNull
            ?: throw BackupException.Corrupted("missing or invalid formatVersion")
        if (version > FORMAT_VERSION) throw BackupException.NewerVersion(version)
        if (version < 1) throw BackupException.Corrupted("invalid formatVersion $version")
        return try {
            json.decodeFromJsonElement(BackupFile.serializer(), root)
        } catch (e: SerializationException) {
            throw BackupException.Corrupted(e.message ?: "unreadable backup")
        } catch (e: IllegalArgumentException) {
            throw BackupException.Corrupted(e.message ?: "unreadable backup")
        }
    }
}
