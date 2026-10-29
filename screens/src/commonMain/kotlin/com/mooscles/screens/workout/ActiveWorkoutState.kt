package com.mooscles.screens.workout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.LayoutCoordinates
import com.mooscles.designsystem.components.NumericInput
import com.mooscles.domain.WeightUnits
import com.mooscles.ui.SetField
import com.mooscles.ui.SetUi

/**
 * The field of a set row being edited. [keyboard] is true while the field has the keyboard (a
 * second tap, the keyboard button or a screen reader focusing the field), and [error] when the
 * row is in edit because it was refused (a check without reps). [stepperFocus] asks the field's
 * stepper to take the focus, as when the edit comes from a screen reader's action (D21); it is not
 * saved.
 */
data class SetEditTarget(
    val setId: String,
    val field: SetField,
    val keyboard: Boolean = false,
    val error: Boolean = false,
    val stepperFocus: Boolean = false,
)

/**
 * What the active workout screen keeps on its own, by the real ids of sets and entries, and
 * survives recreation ([rememberActiveWorkoutScreenState]):
 *
 * - [cleared]: the (set, field) pairs the person emptied on purpose, which get no suggestion again
 *   this session (spec D10);
 * - [optimistic]: the done state a check asked for and the database has not echoed yet, by set
 *   id; the disc draws it at once and it goes away when the state arrives equal, or when the
 *   screen drops it because the write failed ([dropOptimistic], [dropAllOptimistic]). It is not
 *   saved: it only bridges one echo, and after a recreation the database is the truth;
 * - [editing]: the row and field being edited, if any;
 * - [drafts]: what the person typed or stepped in a field, by (set, field), until the database
 *   holds the same value. A draft is the text as entered ("82,5", "80.0", an invalid "82,"), and
 *   the field shows it instead of the saved value. With it, by the same key, the values written
 *   since it was entered and not yet seen back ([echo]), so a late echo of an older write cannot
 *   erase a newer draft, and the unit the load drafts are written in ([useUnit]).
 */
@Stable
class ActiveWorkoutScreenState(
    cleared: Set<Pair<String, SetField>> = emptySet(),
    optimistic: Map<String, Boolean> = emptyMap(),
    editing: SetEditTarget? = null,
    drafts: Map<Pair<String, SetField>, String> = emptyMap(),
    sent: Map<Pair<String, SetField>, List<Double>> = emptyMap(),
    draftsPounds: Boolean? = null,
) {
    var cleared: Set<Pair<String, SetField>> by mutableStateOf(cleared)
        internal set
    var optimistic: Map<String, Boolean> by mutableStateOf(optimistic)
        internal set
    var editing: SetEditTarget? by mutableStateOf(editing)
        internal set
    var drafts: Map<Pair<String, SetField>, String> by mutableStateOf(drafts)
        internal set

    /**
     * The values (kg or reps) written for a field while its draft was held, oldest first, until
     * the database shows them. Read only in callbacks and effects, so it is not snapshot state.
     */
    internal var sent: Map<Pair<String, SetField>, List<Double>> = sent
        private set

    /** The unit the load drafts are written in; null until a row first reports it. */
    internal var draftsPounds: Boolean? = draftsPounds
        private set

    /** Where each composed row (and its edit panel) is, for [endsSetEditingOnOutsideTap]. Not state. */
    internal val rowAreas = HashMap<String, LayoutCoordinates>()

    /** Where the editing scope is, for the same. Not state. */
    internal var scopeArea: LayoutCoordinates? = null

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

    /** Whether [set] is drawn as done: the optimistic value while there is one, the database's otherwise. */
    fun completedOf(set: SetUi): Boolean = optimistic[set.id] ?: set.completed

    /**
     * Forgets the optimistic value of [setId], so its row shows the database's done state again.
     * For a write that failed: no echo will come to settle it.
     */
    fun dropOptimistic(setId: String) {
        if (setId in optimistic) optimistic = optimistic - setId
    }

    /** [dropOptimistic] for every set, as when an error arrives and the screen cannot tell which write failed. */
    fun dropAllOptimistic() {
        if (optimistic.isNotEmpty()) optimistic = emptyMap()
    }

    /** Holds [text] as the draft of [field] of [setId]. */
    internal fun type(setId: String, field: SetField, text: String) {
        val key = setId to field
        if (drafts[key] != text) drafts = drafts + (key to text)
    }

    /**
     * Forgets the draft of [field] of [setId], so the field shows the saved value; the writes
     * still awaited stay, so their late echoes are still recognized.
     */
    internal fun dropDraft(setId: String, field: SetField) {
        val key = setId to field
        if (key in drafts) drafts = drafts - key
    }

    /** Notes that [value] was written for [field] of [setId]; its echo will be awaited. */
    internal fun sent(setId: String, field: SetField, value: Double) {
        val key = setId to field
        sent = sent + (key to sent[key].orEmpty() + value)
    }

    /** The value the database will hold for [field] once every write is done: the last one sent, or [saved]. */
    internal fun expected(setId: String, field: SetField, saved: Double): Double = sent[setId to field]?.lastOrNull() ?: saved

    /**
     * The database now holds [saved] for [field] of [setId] (it held [previous] before, if known).
     * [draftValue] is the value of the field's draft, null when there is none or it is invalid.
     *
     * Writes are applied in order, and an emission may skip some, so [saved] is the result of one
     * of the writes still awaited: the first one equal to it, which acknowledges it and the older
     * ones. Once nothing is awaited and the draft has the saved value (in any form: "80.0", "082",
     * "82,5"), the draft goes and the field shows the saved value. A value nobody here wrote,
     * with writes in flight, is an older state and changes nothing. One that arrives with nothing
     * in flight is a change from elsewhere: it shows unless the field holds something the person
     * entered that was not written (an invalid text), which is never lost; a draft that only
     * repeated the old value gives way.
     */
    internal fun echo(setId: String, field: SetField, saved: Double, previous: Double?, draftValue: Double?) {
        val key = setId to field
        val pending = sent[key].orEmpty()
        val at = pending.indexOf(saved)
        if (at >= 0) {
            val left = pending.drop(at + 1)
            sent = if (left.isEmpty()) sent - key else sent + (key to left)
            if (left.isEmpty() && draftValue == saved && key in drafts) drafts = drafts - key
        } else if (pending.isEmpty() && previous != null && saved != previous && draftValue == previous && key in drafts) {
            drafts = drafts - key
        }
    }

    /**
     * When a row leaves the edit: a draft with the saved value goes, if the last write sent is the
     * saved one too. It covers echoes the database folded into one ("8", "80", "8" seen as one
     * "8") and a draft that never needed a write ("80.0" over a saved 80).
     */
    internal fun settleDraft(setId: String, field: SetField, saved: Double, draftValue: Double?) {
        val key = setId to field
        val pending = sent[key].orEmpty()
        if (key in drafts && draftValue == saved && (pending.isEmpty() || pending.last() == saved)) {
            drafts = drafts - key
            if (key in sent) sent = sent - key
        }
    }

    /**
     * The load drafts are now shown in [pounds]. Each valid one is rewritten in the new unit from
     * its exact kg: the value written for it when there is one ([text] of it in the old unit is the
     * draft), the typed number otherwise. [text] writes a kg value in a unit as a field shows it.
     * An invalid draft has no unit and stays as it is.
     */
    internal fun useUnit(pounds: Boolean, text: (kg: Double, pounds: Boolean) -> String) {
        val before = draftsPounds
        draftsPounds = pounds
        if (before == null || before == pounds) return
        drafts = drafts.mapValues { (key, draft) ->
            if (key.second != SetField.LOAD || draft.isEmpty()) return@mapValues draft
            val kg = sent[key]?.lastOrNull { text(it, before) == draft }
                ?: NumericInput.parseDecimal(draft, max = Double.MAX_VALUE)?.let { WeightUnits.toKg(it, before) }
            if (kg == null) draft else text(kg, pounds)
        }
    }

    companion object {
        /**
         * Saves the fields as lists of strings, booleans and numbers, which every platform can
         * keep: a cleared pair as "FIELD:setId" (the field name has no colon; an id may), the edit
         * target as [setId, field, keyboard, error], a draft as [field, setId, text] and its
         * awaited writes as [field, setId, values]. [optimistic] is left out on purpose: a write
         * lost with the process must not come back as a full disc.
         */
        val Saver: Saver<ActiveWorkoutScreenState, Any> = listSaver<ActiveWorkoutScreenState, Any?>(
            save = { state ->
                listOf(
                    ArrayList(state.cleared.map { (id, field) -> "${field.name}:$id" }),
                    state.editing?.let { arrayListOf<Any>(it.setId, it.field.name, it.keyboard, it.error) },
                    ArrayList(state.drafts.map { (key, text) -> arrayListOf<Any>(key.second.name, key.first, text) }),
                    ArrayList(state.sent.map { (key, values) -> arrayListOf<Any>(key.second.name, key.first, ArrayList(values)) }),
                    state.draftsPounds,
                )
            },
            restore = { saved ->
                @Suppress("UNCHECKED_CAST")
                val cleared = (saved[0] as List<String>).map { it.substringAfter(':') to SetField.valueOf(it.substringBefore(':')) }.toSet()
                @Suppress("UNCHECKED_CAST")
                val editing = (saved[1] as List<Any>?)?.let {
                    SetEditTarget(it[0] as String, SetField.valueOf(it[1] as String), it[2] as Boolean, it[3] as Boolean)
                }
                @Suppress("UNCHECKED_CAST")
                val drafts = (saved.getOrNull(2) as List<List<Any>>?).orEmpty()
                    .associate { (it[1] as String to SetField.valueOf(it[0] as String)) to it[2] as String }
                @Suppress("UNCHECKED_CAST")
                val sent = (saved.getOrNull(3) as List<List<Any>>?).orEmpty()
                    .associate { (it[1] as String to SetField.valueOf(it[0] as String)) to (it[2] as List<Double>).toList() }
                ActiveWorkoutScreenState(
                    cleared = cleared,
                    editing = editing,
                    drafts = drafts,
                    sent = sent,
                    draftsPounds = saved.getOrNull(4) as Boolean?,
                )
            },
        )
    }
}

/** The [ActiveWorkoutScreenState] of the screen, kept across recreation and process death. */
@Composable
fun rememberActiveWorkoutScreenState(): ActiveWorkoutScreenState =
    rememberSaveable(saver = ActiveWorkoutScreenState.Saver) { ActiveWorkoutScreenState() }
