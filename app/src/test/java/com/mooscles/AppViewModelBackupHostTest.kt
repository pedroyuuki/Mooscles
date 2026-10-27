package com.mooscles

import android.Manifest
import android.net.Uri
import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mooscles.backup.BackupApp
import com.mooscles.backup.BackupCodec
import com.mooscles.backup.BackupPreferences
import com.mooscles.backup.ImportSummary
import com.mooscles.data.AppData
import com.mooscles.data.Equipment
import com.mooscles.data.LoadConvention
import com.mooscles.data.MuscleGroup
import com.mooscles.data.RoutineFolder
import com.mooscles.data.WorkoutDatabase
import com.mooscles.data.WorkoutRepository
import com.mooscles.ui.PreferencesUi
import com.mooscles.ui.UiState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Export and import commands against real Room, DataStore and file URIs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class AppViewModelBackupHostTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var app: MoosclesApplication
    private val models = mutableListOf<AppViewModel>()
    private val defaults = PreferencesUi().toBackup()
    /** What the other install's backup carries: differs from [defaults] in every field but the folder. */
    private val filePreferences = BackupPreferences(true, "dark", false, 150, RoutineFolder.DEFAULT_ID)

    @Before fun setup() {
        app = ApplicationProvider.getApplicationContext()
        app.deleteDatabase("mooscles.db")
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        runBlocking(Dispatchers.IO) { app.preferences.restore(defaults) }
    }

    @After fun close() {
        models.forEach { it.viewModelScope.cancel() }
        app.appScope.cancel()
        shadowOf(Looper.getMainLooper()).idle()
        app.database.close()
    }

    @Test fun exportWritesDecodableFileAndReportsCounts() {
        runBlocking(Dispatchers.IO) {
            val repo = app.repository
            repo.initialize()
            val press = repo.createCustomExercise("Landmine press", MuscleGroup.SHOULDERS, Equipment.BARBELL, LoadConvention.TOTAL_LOAD)
            val routine = repo.createRoutine("Push")
            repo.addRoutineExercise(routine, press)
            repeat(2) { finishedWorkout(repo, press, start = 1_000_000L + it * 100_000L) }
        }
        val model = newModel()
        awaitUi(model) { !it.loading }
        val target = File(folder.root, "export.json")

        model.exportData(uri(target))

        awaitUi(model) { !it.backup.busy && it.backup.message != null }
        assertEquals("Exported 2 workouts, 1 routine and 1 custom exercise", model.contentUi.value.backup.message)
        val exported = BackupCodec.decode(target.readText())
        assertEquals(BackupApp(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, "android"), exported.app)
        assertEquals(defaults, exported.preferences)
        assertEquals(2, exported.data.workouts.size)
        assertEquals(1, exported.data.routines.size)
        assertEquals(listOf("Landmine press"), exported.data.exercises.filter { it.custom }.map { it.name })
        assertTrue(exported.exportedAt.endsWith("Z"))

        model.clearBackupMessage()
        awaitUi(model) { it.backup.message == null }
    }

    @Test fun prepareImportShowsSummaryWithoutWriting() {
        val source = otherInstallBackup()
        val model = newModel()
        awaitUi(model) { !it.loading }
        val before = snapshot()

        model.prepareImport(source)

        awaitUi(model) { !it.backup.busy && it.backup.pendingImport != null }
        assertEquals(ImportSummary(1, 0, 1, 0, 1, restoresPreferences = true), model.contentUi.value.backup.pendingImport)
        assertNull(model.contentUi.value.backup.message)
        assertEquals(before, snapshot())
        assertEquals(defaults, preferences())
    }

    @Test fun confirmImportAppliesDataAndPreferencesOnFreshInstall() {
        val source = otherInstallBackup()
        val model = newModel()
        awaitUi(model) { !it.loading }
        model.prepareImport(source)
        awaitUi(model) { it.backup.pendingImport != null }

        model.confirmImport()

        awaitUi(model) { !it.backup.busy && it.backup.message != null }
        val state = model.contentUi.value
        assertEquals("Imported 1 workout, 1 routine and 1 custom exercise", state.backup.message)
        assertNull(state.backup.pendingImport)
        assertNull(state.error)
        val saved = snapshot()
        assertEquals(listOf("Imported push"), saved.routines.map { it.name })
        assertEquals(1, saved.workouts.size)
        assertTrue(saved.exercises.any { it.custom && it.name == "Imported landmine press" })
        assertEquals(filePreferences, preferences())
        awaitUi(model) { it.preferences.pounds && it.preferences.theme == "dark" && it.history.size == 1 }
    }

    @Test fun confirmImportRefusesWhenWorkoutStartedAfterPreview() {
        val source = otherInstallBackup()
        val model = newModel()
        awaitUi(model) { !it.loading }
        model.prepareImport(source)
        awaitUi(model) { it.backup.pendingImport != null }
        model.startWorkout(null)
        awaitUi(model) { !it.startingWorkout && it.active != null }
        val before = snapshot()

        model.confirmImport()

        awaitUi(model) { !it.backup.busy && it.backup.message != null }
        val state = model.contentUi.value
        assertEquals("Finish or discard the active workout to import", state.backup.message)
        assertNull(state.backup.pendingImport)
        assertNull(state.error)
        assertEquals(before, snapshot())
        assertTrue(snapshot().routines.none { it.name == "Imported push" })
        assertEquals(defaults, preferences())
    }

    @Test fun invalidFileShowsReasonAndKeepsData() {
        val notBackup = File(folder.root, "notes.json").apply { writeText("{\"hello\": 1}") }
        val valid = BackupCodec.decode(File(Uri.parse(otherInstallBackup()).path!!).readText())
        val badPreferences = File(folder.root, "bad.json").apply {
            writeText(BackupCodec.encode(valid.copy(preferences = valid.preferences.copy(theme = "neon"))))
        }
        val model = newModel()
        awaitUi(model) { !it.loading }
        val before = snapshot()

        model.prepareImport(uri(notBackup))
        awaitUi(model) { !it.backup.busy && it.backup.message != null }
        assertEquals("This file is not a Mooscles backup.", model.contentUi.value.backup.message)
        assertNull(model.contentUi.value.backup.pendingImport)

        model.clearBackupMessage()
        model.prepareImport(uri(badPreferences))
        awaitUi(model) { !it.backup.busy && it.backup.message != null }
        assertTrue(model.contentUi.value.backup.message!!.startsWith("This backup contains invalid data:"))
        assertNull(model.contentUi.value.backup.pendingImport)
        assertNull(model.contentUi.value.error)

        model.confirmImport()
        idleCommands(model)
        assertEquals(before, snapshot())
        assertEquals(defaults, preferences())
    }

    @Test fun cancelImportDiscardsPendingFile() {
        val source = otherInstallBackup()
        val model = newModel()
        awaitUi(model) { !it.loading }
        model.prepareImport(source)
        awaitUi(model) { it.backup.pendingImport != null }
        val before = snapshot()

        model.cancelImport()
        awaitUi(model) { it.backup.pendingImport == null }
        model.confirmImport()
        idleCommands(model)

        assertFalse(model.contentUi.value.backup.busy)
        assertEquals(before, snapshot())
        assertEquals(defaults, preferences())
    }

    /** One finished workout from one routine with one custom exercise, made on another install. */
    private fun otherInstallBackup(): String = runBlocking(Dispatchers.IO) {
        val db = Room.inMemoryDatabaseBuilder(app, WorkoutDatabase::class.java).build()
        try {
            val other = WorkoutRepository(db)
            other.initialize()
            val press = other.createCustomExercise("Imported landmine press", MuscleGroup.SHOULDERS, Equipment.BARBELL, LoadConvention.TOTAL_LOAD)
            val routine = other.createRoutine("Imported push")
            other.addRoutineExercise(routine, press)
            val workout = other.startRoutine(routine, now = 5_000_000)
            val entry = other.snapshot().workoutItems(workout).single()
            other.updateSet(other.snapshot().exerciseSets(entry.id).first().copy(reps = 6, weightKg = 40.0, completed = true))
            assertTrue(other.finishWorkout(workout, 5_090_000))
            val file = other.exportBackup(filePreferences, BackupApp("1.5.0", 17, "android"), "2026-10-27T12:00:00Z")
            val target = File(folder.root, "other-${System.nanoTime()}.json").apply { writeText(BackupCodec.encode(file)) }
            uri(target)
        } finally {
            db.close()
        }
    }

    private suspend fun finishedWorkout(repo: WorkoutRepository, exerciseId: String, start: Long) {
        val id = repo.startEmpty("Session $start", now = start)
        val entry = repo.addWorkoutExercise(id, exerciseId)
        repo.updateSet(repo.snapshot().exerciseSets(entry).first().copy(reps = 5, weightKg = 30.0, completed = true))
        assertTrue(repo.finishWorkout(id, start + 60_000))
    }

    private fun uri(file: File) = Uri.fromFile(file).toString()

    private fun newModel() = AppViewModel(app).also { models += it }

    private fun snapshot(): AppData = runBlocking(Dispatchers.IO) { app.repository.snapshot() }

    private fun preferences(): BackupPreferences = runBlocking(Dispatchers.IO) { app.preferences.snapshot().toBackup() }

    /** Commands run in order: once a marker command has run, everything queued before it has too. */
    private fun idleCommands(model: AppViewModel) {
        model.prepareImport(uri(File(folder.root, "marker.json").apply { writeText("{}") }))
        awaitUi(model) { !it.backup.busy && it.backup.message == "This file is not a Mooscles backup." }
        model.clearBackupMessage()
        awaitUi(model) { it.backup.message == null }
    }

    private fun awaitUi(model: AppViewModel, predicate: (UiState) -> Boolean) {
        val deadline = System.nanoTime() + 15_000_000_000L
        while (!predicate(model.ui.value) && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("Expected state was not published: ${model.ui.value.backup} ${model.ui.value.error}", predicate(model.ui.value))
    }
}
