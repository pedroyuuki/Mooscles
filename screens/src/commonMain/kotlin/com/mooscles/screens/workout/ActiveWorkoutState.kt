package com.mooscles.screens.workout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.mooscles.ui.SetField

/**
 * The field of a set row being edited. [keyboard] is true once the field has the keyboard (a
 * second tap), and [error] when the row is in edit because it was refused (a check without reps).
 */
data class SetEditTarget(val setId: String, val field: SetField, val keyboard: Boolean = false, val error: Boolean = false)

/**
 * What the active workout screen keeps on its own, by the real ids of sets and entries, and
 * survives recreation ([rememberActiveWorkoutScreenState]):
 *
 * - [cleared]: the (set, field) pairs the person emptied on purpose, which get no suggestion again
 *   this session (spec D10);
 * - [optimistic]: the done state a check asked for and the database has not echoed yet, by set
 *   id; the disc draws it at once and it goes away when the state arrives equal;
 * - [editing]: the row and field being edited, if any.
 */
@Stable
class ActiveWorkoutScreenState(
    cleared: Set<Pair<String, SetField>> = emptySet(),
    optimistic: Map<String, Boolean> = emptyMap(),
    editing: SetEditTarget? = null,
) {
    var cleared: Set<Pair<String, SetField>> by mutableStateOf(cleared)
        internal set
    var optimistic: Map<String, Boolean> by mutableStateOf(optimistic)
        internal set
    var editing: SetEditTarget? by mutableStateOf(editing)
        internal set

    /** Marks [field] of [setId] as emptied on purpose. */
    internal fun clear(setId: String, field: SetField) {
        if ((setId to field) !in cleared) cleared = cleared + (setId to field)
    }

    /** Draws [setId] as [completed] until the state says the same. */
    internal fun expect(setId: String, completed: Boolean) {
        optimistic = optimistic + (setId to completed)
    }

    /** Drops the optimistic value of [setId] once the state's [completed] matches it. */
    internal fun settle(setId: String, completed: Boolean) {
        if (optimistic[setId] == completed) optimistic = optimistic - setId
    }

    companion object {
        /**
         * Saves the fields as lists of strings and booleans, which every platform can keep: a
         * cleared pair as "FIELD:setId", an optimistic entry as "1:setId" or "0:setId", the edit
         * target as [setId, field, keyboard, error]. The field name has no colon; an id may.
         */
        val Saver: Saver<ActiveWorkoutScreenState, Any> = listSaver<ActiveWorkoutScreenState, Any?>(
            save = { state ->
                listOf(
                    ArrayList(state.cleared.map { (id, field) -> "${field.name}:$id" }),
                    ArrayList(state.optimistic.map { (id, done) -> "${if (done) 1 else 0}:$id" }),
                    state.editing?.let { arrayListOf<Any>(it.setId, it.field.name, it.keyboard, it.error) },
                )
            },
            restore = { saved ->
                @Suppress("UNCHECKED_CAST")
                val cleared = (saved[0] as List<String>).map { it.substringAfter(':') to SetField.valueOf(it.substringBefore(':')) }.toSet()
                @Suppress("UNCHECKED_CAST")
                val optimistic = (saved[1] as List<String>).associate { it.substringAfter(':') to (it.substringBefore(':') == "1") }
                @Suppress("UNCHECKED_CAST")
                val editing = (saved[2] as List<Any>?)?.let {
                    SetEditTarget(it[0] as String, SetField.valueOf(it[1] as String), it[2] as Boolean, it[3] as Boolean)
                }
                ActiveWorkoutScreenState(cleared, optimistic, editing)
            },
        )
    }
}

/** The [ActiveWorkoutScreenState] of the screen, kept across recreation and process death. */
@Composable
fun rememberActiveWorkoutScreenState(): ActiveWorkoutScreenState =
    rememberSaveable(saver = ActiveWorkoutScreenState.Saver) { ActiveWorkoutScreenState() }
