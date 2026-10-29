package com.mooscles.timer

import com.mooscles.ui.TimerUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.ceil

/**
 * A [RestTimer] with no alarm and no notification: a coroutine ticker over the same
 * [RestTimerPersistence], so it reads and writes exactly the keys the Android controller does.
 * Used by the iOS end-to-end flow test and as the base for the iOS timer.
 */
class InProcessRestTimer(
    private val persistence: RestTimerPersistence,
    private val scope: CoroutineScope,
    private val activeWorkoutId: suspend () -> String?,
    private val now: () -> Long,
    private val tickMillis: Long = 250L,
) : RestTimer {
    private val mutex = Mutex()
    private val problem = MutableStateFlow<String?>(null)
    private val problemCode = MutableStateFlow<RestTimerError?>(null)
    override val error: StateFlow<String?> = problem.asStateFlow()
    override val errorCode: StateFlow<RestTimerError?> = problemCode.asStateFlow()
    override fun clearError() { problem.value = null; problemCode.value = null }

    private val state = persistence.states.retryWhen { failure, attempt ->
        report(failure)
        delay((attempt + 1).coerceAtMost(30) * 1000)
        true
    }.catch { failure ->
        report(failure)
        emit(RestTimerState())
    }.stateIn(scope, SharingStarted.Eagerly, RestTimerState())
    private val ticks = flow { while (true) { emit(Unit); delay(tickMillis) } }

    override val ui: StateFlow<TimerUi> = combine(state, ticks) { s, _ ->
        val current = now()
        if (s.running && s.remaining(current) == 0L) scope.launch { complete(s.token) }
        TimerUi(s.exerciseName, ceil(s.remaining(current) / 1000.0).toInt(), (s.durationMillis / 1000).toInt(),
            s.running, s.paused, s.finished, progress = s.progress(current), workoutExerciseId = s.workoutExerciseId)
    }.stateIn(scope, SharingStarted.Eagerly, TimerUi())

    private fun report(failure: Throwable) {
        if (failure is CancellationException) throw failure
        // The code goes first, so the text never appears without it.
        problemCode.value = RestTimerError.STORAGE_UNAVAILABLE
        problem.value = "Unable to access the rest timer: ${failure.message ?: "local storage is unavailable"}. Your workout data is saved separately."
    }

    private suspend fun safely(block: suspend () -> Unit) {
        try { block() } catch (failure: Exception) { report(failure) }
    }

    private suspend fun mutate(command: suspend RestTimerPersistence.() -> RestTimerState) = safely {
        mutex.withLock {
            val workoutId = activeWorkoutId()
            persistence.reconcileSession(workoutId)
            if (workoutId == null) return@withLock
            persistence.command()
        }
    }

    override suspend fun start(name: String, seconds: Int, workoutExerciseId: String?) {
        mutate { this.start(name, seconds, workoutExerciseId) }
    }
    override suspend fun pause() { mutate { this.pause() } }
    override suspend fun resume() { mutate { this.resume() } }
    override suspend fun reset() { mutate { this.reset() } }
    override suspend fun skip() { mutate { this.skip() } }
    override suspend fun adjust(seconds: Int) { mutate { this.adjust(seconds) } }
    override suspend fun clear() = safely { clearForSessionEnd() }
    override suspend fun clearForSessionEnd() {
        mutex.withLock { persistence.clear() }
    }

    /** Claims the durable completion once; a second call for the same token finds nothing to claim. */
    private suspend fun complete(token: Long) = safely {
        mutex.withLock {
            val workoutId = activeWorkoutId()
            persistence.reconcileSession(workoutId)
            if (workoutId == null) return@withLock
            persistence.claimCompletion(token)
        }
    }
}
