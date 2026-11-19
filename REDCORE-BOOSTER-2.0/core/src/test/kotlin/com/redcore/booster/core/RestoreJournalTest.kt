package com.redcore.booster.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreJournalTest {
    @Test fun firstOriginalWins() {
        val j = RestoreJournal()
            .noteOriginal(Tweak.BRIGHTNESS, "128").noteApplied(Tweak.BRIGHTNESS, "255")
            .noteOriginal(Tweak.BRIGHTNESS, "255") // a second write in the same session must not lose the true original
        assertEquals("128", j.entries.getValue(Tweak.BRIGHTNESS).original)
        assertEquals("255", j.entries.getValue(Tweak.BRIGHTNESS).applied)
    }

    @Test fun restoresOnlyWhatRedcoreStillOwns() {
        val j = RestoreJournal()
            .noteOriginal(Tweak.BRIGHTNESS, "128").noteApplied(Tweak.BRIGHTNESS, "255")
            .noteOriginal(Tweak.DND_FILTER, "1").noteApplied(Tweak.DND_FILTER, "2")
            .noteOriginal(Tweak.MASTER_SYNC, "true").noteApplied(Tweak.MASTER_SYNC, "false")
        val plan = j.planRestore(mapOf(
            Tweak.BRIGHTNESS to "255",   // unchanged since REDCORE wrote it -> restore
            Tweak.DND_FILTER to "1",     // user already turned DND off -> already original
            Tweak.MASTER_SYNC to "true", // same
        )).associateBy { it.tweak }
        assertEquals(RestoreAction.RESTORE, plan.getValue(Tweak.BRIGHTNESS).action)
        assertEquals("128", plan.getValue(Tweak.BRIGHTNESS).target)
        assertEquals(RestoreAction.ALREADY_ORIGINAL, plan.getValue(Tweak.DND_FILTER).action)
    }

    @Test fun userChangesDuringTheGameAreKept() {
        val j = RestoreJournal().noteOriginal(Tweak.BRIGHTNESS, "100").noteApplied(Tweak.BRIGHTNESS, "255")
        val step = j.planRestore(mapOf(Tweak.BRIGHTNESS to "180")).single()
        assertEquals(RestoreAction.KEEP_USER_CHANGE, step.action)
        val forced = j.planRestore(mapOf(Tweak.BRIGHTNESS to "180"), keepUserChanges = false).single()
        assertEquals(RestoreAction.RESTORE, forced.action)
    }

    @Test fun neverWrittenSettingsAreNotRestored() {
        // Original recorded, but the write never happened (permission missing): nothing of ours to undo.
        val j = RestoreJournal().noteOriginal(Tweak.ANIM_WINDOW, "1.0")
        assertEquals(RestoreAction.ALREADY_ORIGINAL, j.planRestore(mapOf(Tweak.ANIM_WINDOW to "1")).single().action)
        assertEquals(RestoreAction.KEEP_USER_CHANGE, j.planRestore(mapOf(Tweak.ANIM_WINDOW to "0.5")).single().action)
    }

    @Test fun missingOriginalIsRestoredAsMissing() {
        // peak_refresh_rate often doesn't exist until something writes it; "restore" means removing ours.
        val j = RestoreJournal().noteOriginal(Tweak.PEAK_REFRESH, null).noteApplied(Tweak.PEAK_REFRESH, "120.0")
        val s = j.planRestore(mapOf(Tweak.PEAK_REFRESH to "120")).single()
        assertEquals(RestoreAction.RESTORE, s.action)
        assertEquals(null, s.target)
    }

    @Test fun numericComparisonIgnoresFormatting() {
        assertTrue(RestoreJournal.same(Tweak.PEAK_REFRESH, "120", "120.0"))
        assertFalse(RestoreJournal.same(Tweak.PEAK_REFRESH, "120", "90.0"))
        assertFalse(RestoreJournal.same(Tweak.DND_POLICY, "1,2", "1,2,3"))
        assertTrue(RestoreJournal.same(Tweak.DND_POLICY, null, null))
    }

    @Test fun serializationRoundTrip() {
        val j = RestoreJournal()
            .noteOriginal(Tweak.DND_POLICY, "160,2,2,129\tweird\nvalue\\").noteApplied(Tweak.DND_POLICY, "96,0,0,0")
            .noteOriginal(Tweak.PEAK_REFRESH, null).noteApplied(Tweak.PEAK_REFRESH, "120.0")
            .noteFailure(Tweak.PEAK_REFRESH)
            .withMeta(JournalMeta("com.game.a", "Sky\tRaiders", 1234L))
        assertEquals(j, RestoreJournal.parse(j.serialize()))
    }

    @Test fun parseIsTolerant() {
        assertTrue(RestoreJournal.parse(null).isEmpty)
        assertTrue(RestoreJournal.parse("garbage").isEmpty)
        val j = RestoreJournal.parse("FUTURE_KEY\t=1\t=2\t0\nBRIGHTNESS\t=10\t=255\t0\n")
        assertEquals(setOf(Tweak.BRIGHTNESS), j.entries.keys)
    }

    @Test fun removeAndFailureCount() {
        val j = RestoreJournal().noteOriginal(Tweak.BRIGHTNESS, "1").noteFailure(Tweak.BRIGHTNESS).noteFailure(Tweak.BRIGHTNESS)
        assertEquals(2, j.entries.getValue(Tweak.BRIGHTNESS).failures)
        assertTrue(j.remove(Tweak.BRIGHTNESS).isEmpty)
    }
}
