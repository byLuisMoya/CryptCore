package io.github.byluismoya.cryptcore.core

import java.util.concurrent.TimeUnit

/**
 * With biometrics it's easy to forget the master password, and it can't be recovered. Every few
 * days the lock screen asks for it instead of the fingerprint.
 *
 * Uses wall-clock time because it must survive reboots. If the stored time is in the future
 * (the clock was moved back), the reminder is considered due rather than postponed.
 */
object MasterPasswordReminder {
    val INTERVAL_OPTIONS = listOf(
        1 to "Cada día",
        5 to "Cada 5 días",
        7 to "Cada semana",
        10 to "Cada 10 días",
        20 to "Cada 20 días",
    )
    const val DEFAULT_DAYS = 7

    fun isDue(lastVerifiedAt: Long, now: Long, intervalDays: Int): Boolean {
        if (lastVerifiedAt > now) return true
        return now - lastVerifiedAt >= TimeUnit.DAYS.toMillis(intervalDays.toLong())
    }

    fun daysSince(lastVerifiedAt: Long, now: Long): Long =
        if (lastVerifiedAt > now) 0 else TimeUnit.MILLISECONDS.toDays(now - lastVerifiedAt)
}
