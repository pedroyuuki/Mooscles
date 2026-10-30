package com.mooscles.photos

import android.content.Context
import android.os.Build
import androidx.core.net.toUri
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The exercise photos in `files/exercise_photos/`: `<id>.jpg` and `thumbs/<id>.jpg`, with drafts in the same shape
 * under `drafts/`. Every write goes to a `.tmp` file first and is renamed into place, so a reader never sees half a
 * photo and an older photo stays whole until the rename. Not in the backup, not shared.
 */
class AndroidExercisePhotoStore(
    private val context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ExercisePhotoStore {
    private val root get() = File(context.filesDir, DIRECTORY)
    private val gate = Mutex()
    private val revisions = MutableStateFlow(0L)
    override val revision: StateFlow<Long> = revisions.asStateFlow()

    private fun full(dir: File, key: String) = File(dir, "$key.jpg")
    private fun thumb(dir: File, key: String) = File(File(dir, THUMBS), "$key.jpg")
    private fun drafts() = File(root, DRAFTS)
    private fun bump() = revisions.update { it + 1 }

    override suspend fun stage(draftKey: String, source: String): Boolean = withContext(io) {
        if (!safe(draftKey)) return@withContext false
        val uri = source.toUri()
        val processed = PhotoProcessing.process({ context.contentResolver.openInputStream(uri) ?: throw IOException("No stream") }, Build.VERSION.SDK_INT)
            ?: return@withContext false
        gate.withLock {
            try {
                val dir = drafts()
                val tmpFull = tmp(dir).also { it.writeBytes(processed.full) }
                val tmpThumb = tmp(File(dir, THUMBS)).also { it.writeBytes(processed.thumb) }
                move(tmpThumb, thumb(dir, draftKey))
                move(tmpFull, full(dir, draftKey))
                bump()
                true
            } catch (_: IOException) {
                false
            }
        }
    }

    override suspend fun draft(draftKey: String, thumbnail: Boolean): PhotoFile? = withContext(io) {
        if (!safe(draftKey)) null else load(if (thumbnail) thumb(drafts(), draftKey) else full(drafts(), draftKey))
    }

    override suspend fun commit(draftKey: String, exerciseId: String): Boolean = withContext(io) {
        if (!safe(draftKey) || !safe(exerciseId)) return@withContext false
        gate.withLock {
            val draftFull = full(drafts(), draftKey)
            val draftThumb = thumb(drafts(), draftKey)
            if (!draftFull.isFile || !draftThumb.isFile) return@withLock false
            val targetFull = full(root, exerciseId)
            val targetThumb = thumb(root, exerciseId)
            val oldFull = targetFull.takeIf { it.isFile }?.lastModified()
            val oldThumb = targetThumb.takeIf { it.isFile }?.lastModified()
            try {
                move(draftThumb, targetThumb)
                move(draftFull, targetFull)
            } catch (_: IOException) {
                return@withLock false
            }
            // The version is the modified time; make sure a replacement never reuses the older value.
            newer(targetFull, oldFull)
            newer(targetThumb, oldThumb)
            bump()
            true
        }
    }

    override suspend fun discard(draftKey: String) {
        if (!safe(draftKey)) return
        withContext(io) {
            gate.withLock {
                val deleted = full(drafts(), draftKey).delete() or thumb(drafts(), draftKey).delete()
                if (deleted) bump()
            }
        }
    }

    override suspend fun read(exerciseId: String, thumbnail: Boolean): PhotoFile? = withContext(io) {
        if (!safe(exerciseId)) null else load(if (thumbnail) thumb(root, exerciseId) else full(root, exerciseId))
    }

    override suspend fun remove(exerciseId: String) {
        if (!safe(exerciseId)) return
        withContext(io) {
            gate.withLock {
                val deleted = full(root, exerciseId).delete() or thumb(root, exerciseId).delete()
                if (deleted) bump()
            }
        }
    }

    override suspend fun removeOrphans(customExerciseIds: Set<String>, draftMaxAgeMillis: Long) = withContext(io) {
        gate.withLock {
            var changed = false
            val cutoff = System.currentTimeMillis() - draftMaxAgeMillis
            for (dir in listOf(root, File(root, THUMBS))) {
                dir.listFiles { file -> file.isFile && file.name.endsWith(SUFFIX) }?.forEach { file ->
                    if (file.name.removeSuffix(SUFFIX) !in customExerciseIds && file.delete()) changed = true
                }
            }
            for (dir in listOf(drafts(), File(drafts(), THUMBS))) {
                dir.listFiles { file -> file.isFile && file.name.endsWith(SUFFIX) && file.lastModified() < cutoff }
                    ?.forEach { if (it.delete()) changed = true }
            }
            // Leftovers of a write that was interrupted before its rename.
            for (dir in listOf(root, File(root, THUMBS), drafts(), File(drafts(), THUMBS))) {
                dir.listFiles { file -> file.isFile && file.name.endsWith(TEMP) && file.lastModified() < cutoff }
                    ?.forEach { it.delete() }
            }
            if (changed) bump()
        }
    }

    private fun load(file: File): PhotoFile? = try {
        if (file.isFile) PhotoFile(file.readBytes(), file.lastModified()) else null
    } catch (_: IOException) {
        null
    }

    private fun tmp(dir: File): File {
        dir.mkdirs()
        return File.createTempFile("photo", TEMP, dir)
    }

    private fun move(from: File, to: File) {
        to.parentFile?.mkdirs()
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            from.delete()
            throw e
        }
    }

    /** Makes [file]'s modified time later than [previous], so two commits in one clock tick still differ. */
    private fun newer(file: File, previous: Long?) {
        if (previous != null && file.lastModified() <= previous) file.setLastModified(previous + 1_000)
    }

    /** Ids and keys become file names: no separators, no dots, not empty. */
    private fun safe(name: String) = name.isNotEmpty() && name.length <= 128 && name.none { it == '/' || it == '\\' || it == '.' || it.isISOControl() }

    private companion object {
        const val DIRECTORY = "exercise_photos"
        const val THUMBS = "thumbs"
        const val DRAFTS = "drafts"
        const val SUFFIX = ".jpg"
        const val TEMP = ".tmp"
    }
}
