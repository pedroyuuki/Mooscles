package com.mooscles.photos

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import com.mooscles.sharing.HostFileProviderPaths
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Android half of the photo picker (plan 4B, Task 15; pre-flight F12): the gallery and camera launchers, the
 * pending camera file kept across a process death, the cleanup of the capture file, and the camera check.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], shadows = [HostFileProviderPaths::class], instrumentedPackages = ["androidx.core.content"])
class AndroidPhotoCaptureHostTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val captureDir = File(context.cacheDir, PHOTO_CAPTURE_DIR)
    private val capture = AndroidPhotoCapture(context)
    private val registry = RecordingRegistry()
    private val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
    private val results = mutableListOf<String?>()

    @Before fun clean() {
        captureDir.deleteRecursively()
        // Instrumented AndroidX classes keep static state while Robolectric changes app cache paths.
        val cache = FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
        (cache.get(null) as MutableMap<*, *>).clear()
    }
    @After fun cleanup() { captureDir.deleteRecursively() }

    private class RecordingRegistry : ActivityResultRegistry() {
        val launches = mutableListOf<Pair<Int, Any?>>()
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            launches += requestCode to input
        }
    }

    private fun content(launcher: MutableList<() -> Unit>, camera: Boolean) = @androidx.compose.runtime.Composable {
        CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
            launcher += if (camera) capture.rememberCameraLauncher { results += it } else capture.rememberGalleryLauncher { results += it }
        }
    }

    /** F12: the process dies while the camera app is in front; the file the camera is writing is still the one handed back. */
    @Test fun pendingCaptureUriSurvivesRecreation() {
        val restoration = StateRestorationTester(compose)
        val launchers = mutableListOf<() -> Unit>()
        restoration.setContent(content(launchers, camera = true))
        compose.waitForIdle()
        compose.runOnIdle { launchers.last()() }
        val (code, input) = registry.launches.single()
        val uri = input as Uri
        assertEquals("content", uri.scheme)
        assertTrue(uri.path.orEmpty(), uri.path.orEmpty().startsWith("/photo_capture/"))
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.runOnIdle { registry.dispatchResult(code, Activity.RESULT_OK, null) }
        assertEquals(listOf(uri.toString()), results)
    }

    @Test fun aCancelledCameraReturnsNullAndDeletesTheFile() {
        val launchers = mutableListOf<() -> Unit>()
        compose.setContent(content(launchers, camera = true))
        compose.waitForIdle()
        compose.runOnIdle { launchers.last()() }
        val (code, input) = registry.launches.single()
        val file = File(captureDir, (input as Uri).lastPathSegment!!)
        file.writeBytes(byteArrayOf(1))
        compose.runOnIdle { registry.dispatchResult(code, Activity.RESULT_CANCELED, null) }
        assertEquals(listOf<String?>(null), results)
        assertFalse(file.exists())
    }

    @Test fun eachShotGetsANewFileUnderTheCaptureFolder() {
        val launchers = mutableListOf<() -> Unit>()
        compose.setContent(content(launchers, camera = true))
        compose.waitForIdle()
        compose.runOnIdle { launchers.last()(); launchers.last()() }
        val uris = registry.launches.map { it.second as Uri }
        assertEquals(2, uris.toSet().size)
    }

    @Test fun aPickedImageIsHandedBackAndNothingIsHandedBackWithoutOne() {
        val launchers = mutableListOf<() -> Unit>()
        compose.setContent(content(launchers, camera = false))
        compose.waitForIdle()
        compose.runOnIdle { launchers.last()() }
        val (code, input) = registry.launches.single()
        assertTrue(input.toString(), input is androidx.activity.result.PickVisualMediaRequest)
        assertEquals(ActivityResultContracts.PickVisualMedia.ImageOnly,
            (input as androidx.activity.result.PickVisualMediaRequest).mediaType)
        val picked = Intent().setData(Uri.parse("content://media/picker/0/1"))
        compose.runOnIdle { registry.dispatchResult(code, Activity.RESULT_OK, picked) }
        compose.runOnIdle { launchers.last()() }
        compose.runOnIdle { registry.dispatchResult(registry.launches.last().first, Activity.RESULT_CANCELED, Intent()) }
        assertEquals(listOf<String?>("content://media/picker/0/1", null), results)
    }

    @Test fun stagingDeletesTheCaptureFile() {
        val sources = File(context.cacheDir, "capture-sources").also { it.deleteRecursively() }
        val jpeg = PhotoTestImages.write(File(sources, "shot.jpg"), 800, 600, 1)
        captureDir.mkdirs()
        val file = File(captureDir, "shot.jpg").also { jpeg.copyTo(it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.workout-shares", file)
        val store = AndroidExercisePhotoStore(context, Dispatchers.Unconfined)
        val root = File(context.filesDir, "exercise_photos")
        try {
            assertTrue(runBlocking { store.stage("draft-1", uri.toString()) })
            assertFalse("The capture file is deleted after stage", file.exists())
            // A file from the gallery (not ours) is never deleted.
            val other = File(sources, "gallery.jpg").also { jpeg.copyTo(it) }
            assertTrue(runBlocking { store.stage("draft-2", Uri.fromFile(other).toString()) })
            assertTrue(other.exists())
        } finally {
            root.deleteRecursively(); sources.deleteRecursively()
        }
    }

    @Test fun aFailedStageStillDeletesTheCaptureFile() {
        captureDir.mkdirs()
        val file = File(captureDir, "bad.jpg").also { it.writeBytes(byteArrayOf(1, 2, 3)) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.workout-shares", file)
        val store = AndroidExercisePhotoStore(context, Dispatchers.Unconfined)
        assertFalse(runBlocking { store.stage("draft-3", uri.toString()) })
        assertFalse(file.exists())
    }

    @Test fun cameraAvailabilityFollowsTheCameraApp() {
        assertFalse(capture.cameraAvailable)
        val info = ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = "com.example.camera"; name = "Camera" } }
        shadowOf(context.packageManager).addResolveInfoForIntent(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE), info)
        assertTrue(capture.cameraAvailable)
    }
}
