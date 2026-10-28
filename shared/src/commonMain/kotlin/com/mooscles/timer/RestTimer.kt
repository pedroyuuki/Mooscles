package com.mooscles.timer

import com.mooscles.ui.TimerUi
import kotlinx.coroutines.flow.StateFlow

/** The rest timer as the view model drives it. Android implements it with alarms and notifications. */
interface RestTimer {
    val ui: StateFlow<TimerUi>
    val error: StateFlow<String?>
    fun clearError()
    suspend fun start(name: String, seconds: Int, workoutExerciseId: String? = null)
    /** Clears the timer and reports storage failures through [error] instead of throwing. */
    suspend fun clear()
    /** Like [clear], but propagates storage failures, so a session end never reports success after a failed clear. */
    suspend fun clearForSessionEnd()
    suspend fun pause()
    suspend fun resume()
    suspend fun reset()
    suspend fun skip()
    suspend fun adjust(seconds: Int)
}
