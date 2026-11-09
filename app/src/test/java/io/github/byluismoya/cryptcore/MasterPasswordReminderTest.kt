package io.github.byluismoya.cryptcore

import io.github.byluismoya.cryptcore.core.MasterPasswordReminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterPasswordReminderTest {
    private val day = 24 * 60 * 60 * 1000L
    private val now = 1_800_000_000_000L

    @Test
    fun notDueBeforeInterval() {
        assertFalse(MasterPasswordReminder.isDue(now - 6 * day, now, 7))
        assertFalse(MasterPasswordReminder.isDue(now - day + 1, now, 1))
    }

    @Test
    fun dueAtAndAfterInterval() {
        assertTrue(MasterPasswordReminder.isDue(now - 7 * day, now, 7))
        assertTrue(MasterPasswordReminder.isDue(now - 30 * day, now, 20))
    }

    @Test
    fun clockMovedBackwardsCountsAsDue() {
        assertTrue(MasterPasswordReminder.isDue(now + day, now, 7))
        assertEquals(0, MasterPasswordReminder.daysSince(now + day, now))
    }

    @Test
    fun daysSinceRoundsDown() {
        assertEquals(6, MasterPasswordReminder.daysSince(now - 6 * day - 5, now))
    }
}
