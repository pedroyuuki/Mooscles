package com.mooscles.data

import com.mooscles.backup.BackupApp
import com.mooscles.backup.BackupCodec
import com.mooscles.backup.BackupData
import com.mooscles.backup.BackupFile
import com.mooscles.backup.BackupPreferences
import com.mooscles.backup.ImportPlan
import com.mooscles.backup.ImportPlanner
import com.mooscles.backup.LocalState
import com.mooscles.backup.from
import com.mooscles.domain.RestDuration
import com.mooscles.domain.RoutineTargetRecords
import com.mooscles.domain.currentTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/** Small atomic commands shared by active tracking, routine editing, and history editing. */
class WorkoutRepository(private val db: WorkoutDatabase) {
    private val dao = db.dao()

    /** A narrow timer/session observer must not subscribe to the entire workout archive. */
    val activeWorkoutIds: Flow<String?> = dao.activeWorkoutId().distinctUntilChanged()

    /**
     * Read all related tables in one transaction: another write can commit between an invalidation
     * signal and this read, so the signal's table names cannot safely select cached table values.
     * Reuse equal immutable lists only after that atomic read to preserve presentation identities.
     */
    val data: Flow<AppData> = flow {
        var previous: AppData? = null
        db.invalidationTracker.createFlow(
            "exercises", "routine_folders", "routines", "routine_exercises", "routine_sets", "workouts", "workout_exercises", "workout_sets"
        ).collect {
            val cached = previous
            val loaded = snapshot()
            val next = if (cached == null) loaded else {
                loaded.copy(
                    exercises = reuseEqual(cached.exercises, loaded.exercises),
                    routines = reuseEqual(cached.routines, loaded.routines),
                    routineExercises = reuseEqual(cached.routineExercises, loaded.routineExercises),
                    workouts = reuseEqual(cached.workouts, loaded.workouts),
                    workoutExercises = reuseEqual(cached.workoutExercises, loaded.workoutExercises),
                    sets = reuseEqual(cached.sets, loaded.sets),
                    routineSets = reuseEqual(cached.routineSets, loaded.routineSets),
                    routineFolders = reuseEqual(cached.routineFolders, loaded.routineFolders)
                )
            }
            previous = next
            emit(next)
        }
    }.distinctUntilChanged().flowOn(Dispatchers.Default)

    private fun <T> reuseEqual(previous: List<T>, current: List<T>): List<T> =
        if (previous == current) previous else current

    suspend fun initialize() = db.writeTransaction {
        ensureDefaultFolder()
        dao.seedExercises(ExerciseCatalog.exercises)
        val catalog = ExerciseCatalog.exercises.associateBy { it.id }
        dao.exercises().filterNot { it.custom }.forEach { existing ->
            catalog[existing.id]?.let { current ->
                if (existing.name != current.name || existing.muscle != current.muscle || existing.equipment != current.equipment) {
                    dao.updateExercise(existing.copy(name = current.name, muscle = current.muscle, equipment = current.equipment))
                }
            }
        }
        dao.routineExercises().forEach { ensurePlannedSets(it) }
        // Update configurable values only; completed workout snapshots stay unchanged.
        dao.routineExercises().filter { (it.restSeconds ?: 0) > RestDuration.MAX_SECONDS }.forEach {
            dao.updateRoutineExercise(it.copy(restSeconds = RestDuration.MAX_SECONDS))
        }
        val activeIds = dao.workouts().filter { it.status == WorkoutStatus.ACTIVE }.map { it.id }.toSet()
        dao.workoutExercises().filter { it.workoutId in activeIds && it.restSeconds > RestDuration.MAX_SECONDS }.forEach {
            dao.updateWorkoutExercise(it.copy(restSeconds = RestDuration.MAX_SECONDS))
        }
        synchronizeRoutineTargets()
    }

    suspend fun snapshot(): AppData = db.writeTransaction {
        AppData(dao.exercises(), dao.routines(), dao.routineExercises(), dao.workouts(), dao.workoutExercises(), dao.sets(), dao.routineSets(), dao.routineFolders())
    }

    /** Everything a backup holds, read atomically. Call after [initialize] so the default folder exists. */
    suspend fun localState(): LocalState = db.writeTransaction { LocalState(snapshot(), dao.targetCheckpoints()) }

    suspend fun exportBackup(preferences: BackupPreferences, app: BackupApp, exportedAt: String): BackupFile {
        val state = localState()
        // Commands run on the main thread; mapping a large archive must not.
        val rows = withContext(Dispatchers.Default) { BackupData.from(state.data, state.checkpoints) }
        return BackupFile(
            format = BackupCodec.FORMAT,
            formatVersion = BackupCodec.FORMAT_VERSION,
            exportedAt = exportedAt,
            app = app,
            preferences = preferences,
            data = rows,
        )
    }

    /**
     * Adds what [file] has and the phone does not, all or nothing. The only existing rows it changes are
     * inherited routine targets (with their mirrored plan columns) and PR checkpoints, which follow the
     * merged history. [beforeCommit] runs last, inside the transaction, so the caller can apply the
     * preferences right before the commit: if it throws, nothing is imported.
     */
    suspend fun importBackup(file: BackupFile, beforeCommit: suspend (ImportPlan) -> Unit = {}): ImportPlan = db.writeTransaction {
        check(dao.activeWorkout() == null) { "Finish or discard the active workout to import" }
        val plan = ImportPlanner.plan(file, localState())
        with(plan.entities) {
            dao.insertRoutineFolders(routineFolders)
            dao.insertExercises(exercises)
            dao.insertRoutines(routines)
            dao.insertRoutineExercises(routineExercises)
            dao.insertRoutineSets(routineSets)
            dao.insertWorkouts(workouts)
            dao.insertWorkoutExercises(workoutExercises)
            dao.insertSets(workoutSets)
            dao.insertTargetCheckpoints(checkpoints)
        }
        synchronizeRoutineTargets()
        beforeCommit(plan)
        plan
    }

    // Commands that edit one row should not load the full workout archive to locate that row.
    suspend fun activeWorkout(): Workout? = dao.activeWorkout()
    suspend fun workout(id: String): Workout? = dao.workout(id)
    suspend fun workoutSet(id: String): WorkoutSet? = dao.set(id)
    suspend fun workoutExercise(id: String): WorkoutExercise? = dao.workoutExercise(id)
    suspend fun routineExercise(id: String): RoutineExercise? = dao.routineExercise(id)
    suspend fun routineFolder(id: String): RoutineFolder? = dao.routineFolder(id)
    suspend fun exercise(id: String): Exercise? = dao.exercise(id)
    suspend fun workoutSets(workoutId: String): List<WorkoutSet> = dao.workoutSets(workoutId)

    /** Run only inside a repository transaction, never while rendering UI or editing an active set. */
    private suspend fun synchronizeRoutineTargets() {
        val records = RoutineTargetRecords.latest(snapshot()).associateBy { it.exerciseId to it.convention }
        val checkpoints = dao.targetCheckpoints().associateBy { it.exerciseId to it.convention }
        val exercises = dao.exercises().associateBy { it.id }
        dao.routineExercises().forEach { plan ->
            val exercise = dataRequireNotNull(exercises[plan.exerciseId], DataError.EXERCISE_NOT_FOUND) { "Exercise not found." }
            val record = records[plan.exerciseId to exercise.convention]
            val reps = record?.reps ?: 0
            val weightKg = record?.weightKg
            val changed = dao.plannedSets(plan.id).filter {
                it.inheritPr && (it.targetReps != reps || it.targetWeightKg != weightKg)
            }
            changed.forEach { row ->
                dao.updateRoutineSet(row.copy(targetWeightKg = weightKg, targetReps = reps))
            }
            if (changed.isNotEmpty()) mirrorPlan(plan.id)
        }
        // Row modes now protect manual values. A deleted/corrected maximum must not leave an old
        // checkpoint suppressing new inherited targets; retain markers only for compatibility.
        records.values.forEach { record ->
            if (checkpoints[record.exerciseId to record.convention]?.record() != record) {
                dao.saveTargetCheckpoint(RoutineTargetCheckpoint(record.exerciseId, record.convention, record.weightKg, record.reps))
            }
        }
    }

    private suspend fun inheritedSet(plan: RoutineExercise, position: Int, id: String = newId()): RoutineSet {
        val exercise = dataRequireNotNull(dao.exercise(plan.exerciseId), DataError.EXERCISE_NOT_FOUND) { "Exercise not found." }
        val record = RoutineTargetRecords.latest(snapshot()).firstOrNull {
            it.exerciseId == plan.exerciseId && it.convention == exercise.convention
        }
        return RoutineSet(id, plan.id, position, record?.reps ?: 0, record?.weightKg, inheritPr = true)
    }

    /** Fallback for plans inserted by old import/test clients; real upgrades are handled by migration. */
    private suspend fun ensurePlannedSets(plan: RoutineExercise): List<RoutineSet> {
        var rows = dao.plannedSets(plan.id)
        if (rows.isEmpty()) {
            repeat(plan.plannedSets) { position ->
                dao.insertRoutineSet(RoutineSet(routineExerciseId = plan.id, position = position,
                    targetReps = plan.targetReps, targetWeightKg = plan.targetWeightKg, inheritPr = false))
            }
            rows = dao.plannedSets(plan.id)
        }
        return rows
    }

    /** Retain the old columns for compatibility; independent rows remain the source of truth. */
    private suspend fun mirrorPlan(id: String) {
        val plan = dataRequireNotNull(dao.routineExercise(id), DataError.ROUTINE_NOT_FOUND) { "Routine exercise not found." }
        val rows = dao.plannedSets(id)
        val first = rows.firstOrNull()
        dao.updateRoutineExercise(plan.copy(plannedSets = rows.size,
            targetReps = first?.targetReps ?: 0, targetWeightKg = first?.targetWeightKg))
    }

    private suspend fun ensureDefaultFolder() {
        dao.seedRoutineFolder(RoutineFolder(RoutineFolder.DEFAULT_ID, RoutineFolder.DEFAULT_NAME))
    }

    suspend fun createRoutineFolder(name: String): String = db.writeTransaction {
        ensureDefaultFolder()
        val folder = RoutineFolder(name = EditRules.name(name), position = dao.routineFolders().size)
        dao.insertRoutineFolder(folder)
        folder.id
    }

    suspend fun renameRoutineFolder(id: String, name: String) = db.writeTransaction {
        val folder = dataRequireNotNull(dao.routineFolder(id), DataError.FOLDER_NOT_FOUND) { "Folder not found." }
        dao.updateRoutineFolder(folder.copy(name = EditRules.name(name)))
    }

    /** Moving only changes membership; routine identity and active/finished snapshots stay intact. */
    suspend fun moveRoutineToFolder(routineId: String, folderId: String) = db.writeTransaction {
        ensureDefaultFolder()
        dataRequireNotNull(dao.routineFolder(folderId), DataError.FOLDER_NOT_FOUND) { "Folder not found." }
        val routine = dataRequireNotNull(dao.routine(routineId), DataError.ROUTINE_NOT_FOUND) { "Routine not found." }
        dao.updateRoutine(routine.copy(folderId = folderId))
    }

    suspend fun createRoutine(name: String, folderId: String = RoutineFolder.DEFAULT_ID): String = db.writeTransaction {
        ensureDefaultFolder()
        dataRequireNotNull(dao.routineFolder(folderId), DataError.FOLDER_NOT_FOUND) { "Folder not found." }
        val item = Routine(name = EditRules.name(name), position = dao.routines().size, folderId = folderId)
        dao.insertRoutine(item)
        item.id
    }

    suspend fun renameRoutine(id: String, name: String) = db.writeTransaction {
        val item = dataRequireNotNull(dao.routine(id), DataError.ROUTINE_NOT_FOUND) { "Routine not found." }
        dao.updateRoutine(item.copy(name = EditRules.name(name)))
    }

    suspend fun duplicateRoutine(id: String): String = db.writeTransaction {
        val original = dataRequireNotNull(dao.routine(id), DataError.ROUTINE_NOT_FOUND) { "Routine not found." }
        val copy = original.copy(id = newId(), name = (original.name.take(113) + " (copy)"), position = dao.routines().size)
        dao.insertRoutine(copy)
        dao.routineItems(id).forEach { originalPlan ->
            val plan = originalPlan.copy(id = newId(), routineId = copy.id)
            dao.insertRoutineExercise(plan)
            ensurePlannedSets(originalPlan).forEach { row ->
                dao.insertRoutineSet(row.copy(id = newId(), routineExerciseId = plan.id))
            }
        }
        copy.id
    }

    suspend fun deleteRoutine(id: String) = db.writeTransaction {
        dao.deleteRoutine(id)
        dao.routines().forEachIndexed { index, item -> dao.updateRoutine(item.copy(position = index)) }
    }

    suspend fun moveRoutine(id: String, direction: Int) = db.writeTransaction {
        val selected = dataRequireNotNull(dao.routine(id), DataError.ROUTINE_NOT_FOUND) { "Routine not found." }
        val items = dao.routines().filter { it.folderId == selected.folderId }.toMutableList()
        val positions = items.map { it.position }
        move(items, id, direction) { it.id }
        items.forEachIndexed { index, item -> dao.updateRoutine(item.copy(position = positions[index])) }
    }

    suspend fun reorderRoutines(ids: List<String>) = db.writeTransaction {
        val items = dao.routines()
        dataRequire(ids.toSet() == items.map { it.id }.toSet() && ids.size == items.size, DataError.INVALID_VALUE) { "Invalid routine order." }
        ids.forEachIndexed { index, id -> dao.updateRoutine(items.first { it.id == id }.copy(position = index)) }
    }

    suspend fun addRoutineExercise(routineId: String, exerciseId: String): String = db.writeTransaction {
        dataRequireNotNull(dao.routine(routineId), DataError.ROUTINE_NOT_FOUND) { "Routine not found." }
        dataRequireNotNull(dao.exercise(exerciseId), DataError.EXERCISE_NOT_FOUND) { "Exercise not found." }
        val item = RoutineExercise(routineId = routineId, exerciseId = exerciseId, position = dao.routineItems(routineId).size,
            restSeconds = RestDuration.DEFAULT_SECONDS)
        dao.insertRoutineExercise(item)
        repeat(item.plannedSets) { dao.insertRoutineSet(inheritedSet(item, it)) }
        mirrorPlan(item.id)
        item.id
    }

    suspend fun updateRoutineExercise(item: RoutineExercise) = db.writeTransaction {
        val existing = dataRequireNotNull(dao.routineExercise(item.id), DataError.ROUTINE_NOT_FOUND) { "Routine exercise not found." }
        dataRequire(existing.routineId == item.routineId, DataError.INVALID_VALUE) { "Cannot move an exercise to another routine." }
        dataRequireNotNull(dao.exercise(item.exerciseId), DataError.EXERCISE_NOT_FOUND) { "Exercise not found." }
        EditRules.plannedSets(item.plannedSets)
        EditRules.target(item.targetReps, item.targetWeightKg)
        EditRules.rest(item.restSeconds)
        dataRequire(existing.exerciseId == item.exerciseId, DataError.INVALID_VALUE) { "Use replacement to change exercise identity." }
        val rows = ensurePlannedSets(existing)
        val editedTargets = existing.targetReps != item.targetReps || existing.targetWeightKg != item.targetWeightKg
        rows.drop(item.plannedSets).forEach { dao.deleteRoutineSet(it.id) }
        rows.take(item.plannedSets).forEach { row ->
            if (editedTargets) dao.updateRoutineSet(row.copy(targetReps = item.targetReps,
                targetWeightKg = item.targetWeightKg, inheritPr = false))
        }
        for (position in rows.size until item.plannedSets) {
            dao.insertRoutineSet(if (editedTargets) RoutineSet(routineExerciseId = item.id, position = position,
                targetReps = item.targetReps, targetWeightKg = item.targetWeightKg, inheritPr = false)
                else inheritedSet(item, position))
        }
        dao.updateRoutineExercise(item)
        mirrorPlan(item.id)
    }

    suspend fun addRoutineSet(entryId: String): String = db.writeTransaction {
        val plan = dataRequireNotNull(dao.routineExercise(entryId), DataError.ROUTINE_NOT_FOUND) { "Routine exercise not found." }
        val rows = ensurePlannedSets(plan)
        EditRules.routineSetCount(rows.size + 1)
        val row = inheritedSet(plan, rows.size)
        dao.insertRoutineSet(row)
        mirrorPlan(entryId)
        row.id
    }

    suspend fun removeRoutineSet(setId: String) = db.writeTransaction {
        val row = dataRequireNotNull(dao.routineSet(setId), DataError.ROUTINE_NOT_FOUND) { "Routine set not found." }
        val rows = dao.plannedSets(row.routineExerciseId)
        dataRequire(rows.size > 1, DataError.INVALID_VALUE) { "Keep at least one planned set." }
        dao.deleteRoutineSet(setId)
        dao.plannedSets(row.routineExerciseId).forEachIndexed { index, item ->
            dao.updateRoutineSet(item.copy(position = index))
        }
        mirrorPlan(row.routineExerciseId)
    }

    suspend fun updateRoutineSet(setId: String, reps: Int, weightKg: Double?) = db.writeTransaction {
        EditRules.target(reps, weightKg)
        val row = dataRequireNotNull(dao.routineSet(setId), DataError.ROUTINE_NOT_FOUND) { "Routine set not found." }
        dao.updateRoutineSet(row.copy(targetReps = reps, targetWeightKg = weightKg, inheritPr = false))
        mirrorPlan(row.routineExerciseId)
    }

    suspend fun inheritRoutineSet(setId: String) = db.writeTransaction {
        val row = dataRequireNotNull(dao.routineSet(setId), DataError.ROUTINE_NOT_FOUND) { "Routine set not found." }
        val plan = dataRequireNotNull(dao.routineExercise(row.routineExerciseId), DataError.ROUTINE_NOT_FOUND) { "Routine exercise not found." }
        dao.updateRoutineSet(inheritedSet(plan, row.position, row.id))
        mirrorPlan(row.routineExerciseId)
    }

    suspend fun updateRoutineExerciseRest(entryId: String, seconds: Int) = db.writeTransaction {
        EditRules.rest(seconds)
        val plan = dataRequireNotNull(dao.routineExercise(entryId), DataError.ROUTINE_NOT_FOUND) { "Routine exercise not found." }
        dao.updateRoutineExercise(plan.copy(restSeconds = seconds))
    }

    suspend fun replaceRoutineExercise(id: String, exerciseId: String) = db.writeTransaction {
        val item = dataRequireNotNull(dao.routineExercise(id), DataError.ROUTINE_NOT_FOUND) { "Routine exercise not found." }
        dataRequireNotNull(dao.exercise(exerciseId), DataError.EXERCISE_NOT_FOUND) { "Exercise not found." }
        if (item.exerciseId == exerciseId) return@writeTransaction
        val replacement = item.copy(exerciseId = exerciseId, targetWeightKg = null, targetReps = 0)
        dao.updateRoutineExercise(replacement)
        ensurePlannedSets(item).forEach { row ->
            dao.updateRoutineSet(inheritedSet(replacement, row.position, row.id))
        }
        mirrorPlan(id)
    }

    suspend fun removeRoutineExercise(id: String) = db.writeTransaction {
        val item = dao.routineExercise(id) ?: return@writeTransaction
        dao.deleteRoutineExercise(id)
        dao.routineItems(item.routineId).forEachIndexed { index, entry -> dao.updateRoutineExercise(entry.copy(position = index)) }
    }

    suspend fun moveRoutineExercise(id: String, direction: Int) = db.writeTransaction {
        val entry = dataRequireNotNull(dao.routineExercise(id), DataError.ROUTINE_NOT_FOUND) { "Routine exercise not found." }
        val items = dao.routineItems(entry.routineId).toMutableList()
        move(items, id, direction) { it.id }
        items.forEachIndexed { index, item -> dao.updateRoutineExercise(item.copy(position = index)) }
    }

    suspend fun reorderRoutineExercises(routineId: String, ids: List<String>) = db.writeTransaction {
        val items = dao.routineItems(routineId)
        dataRequire(ids.toSet() == items.map { it.id }.toSet() && ids.size == items.size, DataError.INVALID_VALUE) { "Invalid exercise order." }
        ids.forEachIndexed { index, id -> dao.updateRoutineExercise(items.first { it.id == id }.copy(position = index)) }
    }

    suspend fun saveExercise(item: Exercise) = db.writeTransaction {
        val valid = item.copy(name = EditRules.name(item.name))
        val existing = dao.exercise(item.id)
        dataRequire(existing == null || existing.custom, DataError.INVALID_VALUE) { "Only custom exercises can be edited." }
        if (existing == null) dao.insertExercise(valid.copy(custom = true)) else dao.updateExercise(valid.copy(custom = true))
    }

    suspend fun createCustomExercise(name: String, muscle: MuscleGroup, equipment: Equipment, convention: LoadConvention): String {
        val item = Exercise(name = EditRules.name(name), muscle = muscle, equipment = equipment, convention = convention, custom = true)
        saveExercise(item)
        return item.id
    }

    /** Returning the existing active id makes double taps and process recreation idempotent. */
    suspend fun startEmpty(name: String = "Workout", defaultRestSeconds: Int = RestDuration.DEFAULT_SECONDS, now: Long = currentTimeMillis()): String = db.writeTransaction {
        EditRules.rest(defaultRestSeconds)
        dao.activeWorkout()?.id ?: Workout(name = EditRules.name(name), startedAt = now).also { dao.insertWorkout(it) }.id
    }

    suspend fun startRoutine(routineId: String, defaultRestSeconds: Int = RestDuration.DEFAULT_SECONDS, now: Long = currentTimeMillis()): String = db.writeTransaction {
        dao.activeWorkout()?.let { return@writeTransaction it.id }
        EditRules.rest(defaultRestSeconds)
        val routine = dataRequireNotNull(dao.routine(routineId), DataError.ROUTINE_NOT_FOUND) { "Routine not found." }
        val workout = Workout(name = routine.name, startedAt = now, sourceRoutineId = routine.id)
        val plans = dao.routineItems(routineId)
        val exercises = dao.routineMovements(routineId).associateBy { it.id }
        val plannedSets = dao.routinePlannedSets(routineId).groupBy { it.routineExerciseId }.toMutableMap()
        // Legacy import clients can still create only the shared target columns. Repair those
        // prescriptions in this transaction, without adding a query for every normal exercise.
        val legacySets = plans.filter { plannedSets[it.id].isNullOrEmpty() }.flatMap { plan ->
            List(plan.plannedSets) { position ->
                RoutineSet(routineExerciseId = plan.id, position = position,
                    targetReps = plan.targetReps, targetWeightKg = plan.targetWeightKg, inheritPr = false)
            }.also { plannedSets[plan.id] = it }
        }
        if (legacySets.isNotEmpty()) dao.insertRoutineSets(legacySets)
        val entries = ArrayList<WorkoutExercise>(plans.size)
        val sets = ArrayList<WorkoutSet>(plannedSets.values.sumOf { it.size })
        plans.forEach { plan ->
            val exercise = dataRequireNotNull(exercises[plan.exerciseId], DataError.EXERCISE_NOT_FOUND) { "Exercise not found." }
            val entry = snapshotExercise(exercise, workout.id, plan.position, (plan.restSeconds ?: RestDuration.DEFAULT_SECONDS).coerceAtMost(RestDuration.MAX_SECONDS))
            entries.add(entry)
            plannedSets[plan.id].orEmpty().forEach { row ->
                sets.add(WorkoutSet(workoutExerciseId = entry.id, position = row.position,
                    reps = row.targetReps,
                    weightKg = row.targetWeightKg ?: 0.0,
                    assistance = exercise.convention == LoadConvention.ASSISTANCE))
            }
        }
        dao.insertWorkout(workout)
        dao.insertWorkoutExercises(entries)
        dao.insertSets(sets)
        workout.id
    }

    suspend fun renameWorkout(id: String, name: String) = db.writeTransaction {
        val item = dataRequireNotNull(dao.workout(id), DataError.WORKOUT_NOT_FOUND) { "Workout not found." }
        dao.updateWorkout(item.copy(name = EditRules.name(name)))
    }

    suspend fun addWorkoutExercise(workoutId: String, exerciseId: String, defaultRestSeconds: Int = RestDuration.DEFAULT_SECONDS): String = db.writeTransaction {
        dataRequireNotNull(dao.workout(workoutId), DataError.WORKOUT_NOT_FOUND) { "Workout not found." }
        EditRules.rest(defaultRestSeconds)
        val exercise = dataRequireNotNull(dao.exercise(exerciseId), DataError.EXERCISE_NOT_FOUND) { "Exercise not found." }
        val entry = snapshotExercise(exercise, workoutId, dao.workoutItems(workoutId).size, defaultRestSeconds)
        dao.insertWorkoutExercise(entry)
        repeat(2) { position -> dao.insertSet(WorkoutSet(workoutExerciseId = entry.id, position = position,
            assistance = exercise.convention == LoadConvention.ASSISTANCE)) }
        entry.id
    }

    suspend fun replaceWorkoutExercise(id: String, exerciseId: String) = db.writeTransaction {
        val old = dataRequireNotNull(dao.workoutExercise(id), DataError.WORKOUT_EXERCISE_NOT_FOUND) { "Workout exercise not found." }
        val exercise = dataRequireNotNull(dao.exercise(exerciseId), DataError.EXERCISE_NOT_FOUND) { "Exercise not found." }
        dao.updateWorkoutExercise(snapshotExercise(exercise, old.workoutId, old.position, old.restSeconds).copy(id = old.id))
        dao.deleteExerciseSets(id)
        repeat(2) { position -> dao.insertSet(WorkoutSet(workoutExerciseId = id, position = position,
            assistance = exercise.convention == LoadConvention.ASSISTANCE)) }
    }

    suspend fun updateWorkoutExercise(item: WorkoutExercise) = db.writeTransaction {
        val existing = dataRequireNotNull(dao.workoutExercise(item.id), DataError.WORKOUT_EXERCISE_NOT_FOUND) { "Workout exercise not found." }
        dataRequire(existing.workoutId == item.workoutId && existing.exerciseId == item.exerciseId, DataError.INVALID_VALUE) { "Exercise identity cannot change." }
        EditRules.rest(item.restSeconds)
        dao.updateWorkoutExercise(item.copy(name = EditRules.name(item.name)))
    }

    suspend fun updateWorkoutExerciseRest(id: String, seconds: Int) = db.writeTransaction {
        EditRules.rest(seconds)
        val item = dataRequireNotNull(dao.workoutExercise(id), DataError.WORKOUT_EXERCISE_NOT_FOUND) { "Workout exercise not found." }
        dao.updateWorkoutExercise(item.copy(restSeconds = seconds))
    }

    suspend fun removeWorkoutExercise(id: String) = db.writeTransaction {
        val item = dao.workoutExercise(id) ?: return@writeTransaction
        dao.deleteWorkoutExercise(id)
        dao.workoutItems(item.workoutId).forEachIndexed { index, entry -> dao.updateWorkoutExercise(entry.copy(position = index)) }
    }

    suspend fun moveWorkoutExercise(id: String, direction: Int) = db.writeTransaction {
        val entry = dataRequireNotNull(dao.workoutExercise(id), DataError.WORKOUT_EXERCISE_NOT_FOUND) { "Workout exercise not found." }
        val items = dao.workoutItems(entry.workoutId).toMutableList()
        move(items, id, direction) { it.id }
        items.forEachIndexed { index, item -> dao.updateWorkoutExercise(item.copy(position = index)) }
    }

    suspend fun reorderWorkoutExercises(workoutId: String, ids: List<String>) = db.writeTransaction {
        val items = dao.workoutItems(workoutId)
        dataRequire(ids.toSet() == items.map { it.id }.toSet() && ids.size == items.size, DataError.INVALID_VALUE) { "Invalid exercise order." }
        ids.forEachIndexed { index, id -> dao.updateWorkoutExercise(items.first { it.id == id }.copy(position = index)) }
    }

    suspend fun addSet(workoutExerciseId: String): String = db.writeTransaction {
        val exercise = dataRequireNotNull(dao.workoutExercise(workoutExerciseId), DataError.WORKOUT_EXERCISE_NOT_FOUND) { "Workout exercise not found." }
        val sets = dao.exerciseSets(workoutExerciseId)
        EditRules.workoutSetCount(sets.size + 1)
        val last = sets.lastOrNull()
        val item = WorkoutSet(workoutExerciseId = workoutExerciseId, position = sets.size,
            weightKg = last?.weightKg ?: 0.0,
            assistance = last?.assistance ?: (exercise.convention == LoadConvention.ASSISTANCE))
        dao.insertSet(item)
        item.id
    }

    suspend fun updateSet(item: WorkoutSet) = db.writeTransaction {
        val existing = dataRequireNotNull(dao.set(item.id), DataError.SET_NOT_FOUND) { "Set not found." }
        dataRequire(existing.workoutExerciseId == item.workoutExerciseId, DataError.INVALID_VALUE) { "Cannot move a set to another exercise." }
        EditRules.workoutSet(item)
        dao.updateSet(item)
    }

    suspend fun setCompleted(id: String, completed: Boolean): WorkoutSet = db.writeTransaction {
        val item = dataRequireNotNull(dao.set(id), DataError.SET_NOT_FOUND) { "Set not found." }.copy(completed = completed)
        EditRules.workoutSet(item)
        dao.updateSet(item)
        item
    }

    suspend fun removeSet(id: String) = db.writeTransaction {
        val item = dao.set(id) ?: return@writeTransaction
        dao.deleteSet(id)
        dao.exerciseSets(item.workoutExerciseId).forEachIndexed { index, entry -> dao.updateSet(entry.copy(position = index)) }
    }

    /**
     * Puts a removed set back: the same id and values, at [position] among the exercise's current sets (past the
     * end, last), with the later sets moving down one. Refused with [DataError.RESTORE_UNAVAILABLE] when the
     * exercise is gone, its workout is no longer active or the id already exists (a second undo); the set limit
     * and value rules are the ones [addSet] and [updateSet] apply. Every rule is checked before the first write.
     */
    suspend fun restoreSet(workoutExerciseId: String, position: Int, item: WorkoutSet) = db.writeTransaction {
        val entry = dataRequireNotNull(dao.workoutExercise(workoutExerciseId), DataError.RESTORE_UNAVAILABLE) { RESTORE_UNAVAILABLE }
        dataRequire(dao.workout(entry.workoutId)?.status == WorkoutStatus.ACTIVE, DataError.RESTORE_UNAVAILABLE) { RESTORE_UNAVAILABLE }
        dataRequire(dao.set(item.id) == null, DataError.RESTORE_UNAVAILABLE) { RESTORE_UNAVAILABLE }
        val sets = dao.exerciseSets(workoutExerciseId)
        EditRules.workoutSetCount(sets.size + 1)
        EditRules.workoutSet(item)
        val target = position.coerceIn(0, sets.size)
        sets.forEachIndexed { index, set ->
            val moved = if (index >= target) index + 1 else index
            if (set.position != moved) dao.updateSet(set.copy(position = moved))
        }
        dao.insertSet(item.copy(workoutExerciseId = workoutExerciseId, position = target))
    }

    /** All child mutations are already durable; final status/end time changes atomically once. */
    suspend fun finishWorkout(id: String, now: Long = currentTimeMillis()): Boolean = db.writeTransaction {
        val workout = dataRequireNotNull(dao.workout(id), DataError.WORKOUT_NOT_FOUND) { "Workout not found." }
        if (workout.status == WorkoutStatus.FINISHED) return@writeTransaction false
        val sets = dao.workoutSets(id)
        dataRequire(sets.any { it.completed }, DataError.NO_COMPLETED_SET) { "Complete at least one set before finishing." }
        sets.forEach(EditRules::workoutSet)
        dao.updateWorkout(workout.copy(status = WorkoutStatus.FINISHED, endedAt = now.coerceAtLeast(workout.startedAt)))
        synchronizeRoutineTargets()
        true
    }

    suspend fun discardWorkout(id: String) = db.writeTransaction {
        val workout = dao.workout(id) ?: return@writeTransaction
        dataRequire(workout.status == WorkoutStatus.ACTIVE, DataError.WORKOUT_NOT_ACTIVE) { "Only active workouts can be discarded." }
        dao.deleteWorkout(id)
    }

    suspend fun deleteWorkout(id: String) = db.writeTransaction { dao.deleteWorkout(id) }

    private fun snapshotExercise(exercise: Exercise, workoutId: String, position: Int, restSeconds: Int) = WorkoutExercise(
        workoutId = workoutId, exerciseId = exercise.id, name = exercise.name, muscle = exercise.muscle,
        equipment = exercise.equipment, convention = exercise.convention, position = position,
        restSeconds = restSeconds.coerceIn(0, RestDuration.MAX_SECONDS)
    )

    private fun <T> move(items: MutableList<T>, id: String, direction: Int, identity: (T) -> String) {
        val index = items.indexOfFirst { identity(it) == id }
        dataRequire(index >= 0, DataError.INVALID_VALUE) { "Item not found." }
        val destination = (index + direction.coerceIn(-1, 1)).coerceIn(0, items.lastIndex)
        if (index != destination) items.add(destination, items.removeAt(index))
    }

    private companion object {
        const val RESTORE_UNAVAILABLE = "This set can no longer be restored."
    }
}
