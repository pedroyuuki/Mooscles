package com.mooscles.designsystem.interaction

/** The moments the app gives haptic feedback for (spec "Háptico"). */
enum class HapticEvent {
    SetCompleted, PersonalRecord, RestFinished, StepperStep, SetUnchecked,
    Rejected, LongPress, ToggleOn, ToggleOff,
}

/** The single entry point for haptics. Platform implementations arrive in a later task. */
fun interface MoosclesHaptics {
    fun perform(event: HapticEvent)
}

/** Does nothing: the default until the platform implementations exist. */
object NoHaptics : MoosclesHaptics {
    override fun perform(event: HapticEvent) = Unit
}

/** Records every event, for tests. */
class RecordingHaptics : MoosclesHaptics {
    private val recorded = mutableListOf<HapticEvent>()
    val events: List<HapticEvent> get() = recorded

    override fun perform(event: HapticEvent) {
        recorded += event
    }
}
