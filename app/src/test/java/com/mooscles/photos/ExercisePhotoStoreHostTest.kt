package com.mooscles.photos

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Android photo store on the pipeline the phase 4B spike chose: sdk 26 runs the BitmapFactory path alone, sdk 35
 * the same path with the ImageDecoder fallback available. Images are generated here (hand-made EXIF table).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [26, 35])
class ExercisePhotoStoreHostTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val sources = File(context.cacheDir, "photo-test-sources")
    private val root = File(context.filesDir, "exercise_photos")
    private lateinit var store: AndroidExercisePhotoStore

    @Before fun setup() {
        root.deleteRecursively()
        sources.deleteRecursively()
        store = AndroidExercisePhotoStore(context, Dispatchers.Unconfined)
    }

    @After fun cleanup() {
        root.deleteRecursively()
        sources.deleteRecursively()
    }

    private fun source(name: String, w: Int = 1600, h: Int = 1200, orientation: Int = 1, detail: PhotoTestImages.Detail = PhotoTestImages.Detail.SHAPES): String =
        Uri.fromFile(PhotoTestImages.write(File(sources, name), w, h, orientation, detail = detail)).toString()

    private fun size(bytes: ByteArray): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        return o.outWidth to o.outHeight
    }

    @Test fun stageResizesTheLongSideTo1080AndWritesA192Thumb() = runBlocking {
        assertTrue(store.stage("d1", source("a.jpg")))
        assertEquals(1080 to 810, size(store.draft("d1", false)!!.bytes))
        assertEquals(192 to 192, size(store.draft("d1", true)!!.bytes))
    }

    @Test fun exifRotationIsApplied() = runBlocking {
        assertTrue(store.stage("d1", source("o6.jpg", orientation = 6)))
        val photo = store.draft("d1", false)!!.bytes
        assertEquals(810 to 1080, size(photo))
        assertTrue(PhotoTestImages.upright(photo))
    }

    @Test fun outputIsUnder300KB() = runBlocking {
        // Every pixel random is JPEG's worst case: 85 passes the cap and the quality steps down.
        assertTrue(store.stage("d1", source("noise.jpg", 1440, 1080, detail = PhotoTestImages.Detail.FULL_NOISE)))
        val bytes = store.draft("d1", false)!!.bytes
        assertTrue("${bytes.size} bytes", bytes.size <= 300 * 1024)
        assertEquals(1080 to 810, size(bytes))
    }

    @Test fun undecodableSourceFailsWithoutFiles() = runBlocking {
        val bad = Uri.fromFile(File(sources.apply { mkdirs() }, "bad.jpg").apply { writeBytes(ByteArray(4096) { it.toByte() }) }).toString()
        assertFalse(store.stage("d1", bad))
        assertFalse(store.stage("d2", Uri.fromFile(File(sources, "missing.jpg")).toString()))
        assertNull(store.draft("d1", false))
        assertEquals(emptyList<String>(), root.walkTopDown().filter { it.isFile }.map { it.name }.toList())
        assertEquals(0L, store.revision.value)
    }

    @Test fun commitReplacesAtomically() = runBlocking {
        assertTrue(store.stage("d1", source("a.jpg", orientation = 1)))
        assertTrue(store.commit("d1", "ex"))
        val first = store.read("ex", false)!!
        // A commit without a draft changes nothing: the old photo stays whole.
        assertFalse(store.commit("missing", "ex"))
        assertArrayEquals(first.bytes, store.read("ex", false)!!.bytes)
        assertEquals(first.version, store.read("ex", false)!!.version)
        // A replacement: new bytes, a different version, the draft gone and no temporary left over.
        assertTrue(store.stage("d2", source("b.jpg", orientation = 6)))
        assertTrue(store.commit("d2", "ex"))
        val second = store.read("ex", false)!!
        assertFalse(first.bytes.contentEquals(second.bytes))
        assertNotEquals(first.version, second.version)
        assertNull(store.draft("d2", false))
        assertEquals(emptyList<String>(), root.walkTopDown().filter { it.isFile && it.name.endsWith(".tmp") }.toList())
    }

    @Test fun twoCommitsInTheSameClockTickGetDifferentVersions() = runBlocking {
        val versions = mutableListOf<Long>()
        repeat(3) { i ->
            assertTrue(store.stage("d$i", source("p$i.jpg", orientation = if (i % 2 == 0) 1 else 3)))
            // Whatever the file system's granularity, give every draft the same timestamp (rename keeps it).
            listOf(File(root, "drafts/d$i.jpg"), File(root, "drafts/thumbs/d$i.jpg")).forEach { it.setLastModified(1_700_000_000_000L) }
            assertTrue(store.commit("d$i", "ex"))
            versions += store.read("ex", false)!!.version
        }
        assertEquals(versions.size, versions.toSet().size)
        assertEquals(versions, versions.sorted())
    }

    @Test fun discardDeletesTheDraft() = runBlocking {
        assertTrue(store.stage("d1", source("a.jpg")))
        store.discard("d1")
        assertNull(store.draft("d1", false))
        assertNull(store.draft("d1", true))
    }

    @Test fun removeDeletesBoth() = runBlocking {
        assertTrue(store.stage("d1", source("a.jpg")))
        assertTrue(store.commit("d1", "ex"))
        assertNotNull(store.read("ex", true))
        store.remove("ex")
        assertNull(store.read("ex", false))
        assertNull(store.read("ex", true))
    }

    @Test fun oldDraftsGoFreshDraftsStay() = runBlocking {
        assertTrue(store.stage("old", source("a.jpg")))
        assertTrue(store.stage("fresh", source("b.jpg")))
        val day = 86_400_000L
        File(root, "drafts/old.jpg").setLastModified(System.currentTimeMillis() - 2 * day)
        File(root, "drafts/thumbs/old.jpg").setLastModified(System.currentTimeMillis() - 2 * day)
        store.removeOrphans(emptySet(), day)
        assertNull(store.draft("old", false))
        assertNull(store.draft("old", true))
        assertNotNull(store.draft("fresh", false))
        assertNotNull(store.draft("fresh", true))
    }

    @Test fun staleTemporariesGoFreshOnesStay() = runBlocking {
        val day = 86_400_000L
        val stale = File(root, "drafts/photo1.tmp").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1)); setLastModified(System.currentTimeMillis() - 2 * day) }
        val fresh = File(root, "drafts/photo2.tmp").apply { writeBytes(byteArrayOf(1)) }
        val staleCommitted = File(root, "photo3.tmp").apply { writeBytes(byteArrayOf(1)); setLastModified(System.currentTimeMillis() - 2 * day) }
        store.removeOrphans(emptySet(), day)
        assertFalse(stale.exists())
        assertFalse(staleCommitted.exists())
        assertTrue(fresh.exists())
    }

    @Test fun orphanPhotosGoCustomOnesStay() = runBlocking {
        for (id in listOf("keep", "gone")) {
            assertTrue(store.stage("d-$id", source("$id.jpg")))
            assertTrue(store.commit("d-$id", id))
        }
        store.removeOrphans(setOf("keep"))
        assertNotNull(store.read("keep", false))
        assertNotNull(store.read("keep", true))
        assertNull(store.read("gone", false))
        assertNull(store.read("gone", true))
    }

    @Test fun revisionBumpsOnEveryChange() = runBlocking {
        var last = store.revision.value
        fun assertBumped(step: String) { assertTrue("$step did not bump", store.revision.value > last); last = store.revision.value }
        assertTrue(store.stage("d1", source("a.jpg"))); assertBumped("stage")
        assertTrue(store.commit("d1", "ex")); assertBumped("commit")
        assertTrue(store.stage("d2", source("b.jpg"))); assertBumped("stage again")
        store.discard("d2"); assertBumped("discard")
        store.removeOrphans(emptySet()); assertBumped("removeOrphans")
        assertTrue(store.stage("d3", source("c.jpg"))); last = store.revision.value
        assertTrue(store.commit("d3", "ex2")); last = store.revision.value
        store.remove("ex2"); assertBumped("remove")
    }

    @Test fun namesThatCouldEscapeTheFolderAreRefused() = runBlocking {
        assertFalse(store.stage("../x", source("a.jpg")))
        assertTrue(store.stage("d1", source("a.jpg")))
        assertFalse(store.commit("d1", "../../escape"))
        assertNull(store.read("a/b", false))
    }
}
