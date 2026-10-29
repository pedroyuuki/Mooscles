package com.mooscles.screens.workout

import androidx.compose.runtime.saveable.SaverScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RestDraftTest {
    private fun RestDraft.saveAndRestore(): RestDraft {
        val saved = assertNotNull(with(RestDraft.Saver) { SaverScope { true }.save(this@saveAndRestore) })
        return assertNotNull(RestDraft.Saver.restore(saved))
    }

    @Test
    fun aRestoredDraftForgetsTheWritesInFlight() {
        val writes = mutableListOf<Int>()
        val draft = RestDraft(value = 120, saved = 120)
        draft.show(90)
        draft.commit { writes += it }
        assertEquals(listOf(90), writes)
        // Before the restore, writing the same rest again is skipped: it is in flight.
        draft.commit { writes += it }
        assertEquals(listOf(90), writes)

        // The process dies before the write lands. The restored draft does not wait for it.
        val restored = draft.saveAndRestore()
        assertEquals(90, restored.value)
        restored.commit { writes += it }
        assertEquals(listOf(90, 90), writes, "asked again, the restored sheet writes the same rest again")

        // A restored draft whose write was lost: the database's rest shows at its first echo.
        val lost = draft.saveAndRestore()
        lost.echo(120)
        assertEquals(120, lost.value)
        // One whose write landed shows it.
        val landed = draft.saveAndRestore()
        landed.echo(90)
        assertEquals(90, landed.value)
    }
}
