package io.github.byluismoya.cryptcore.security

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.byluismoya.cryptcore.data.SettingsStore
import io.github.byluismoya.cryptcore.data.VaultRepository

/**
 * Locks the vault when the whole app has been in the background longer than the configured delay.
 * The deadline is also checked on return, because a frozen process may never run the Handler.
 */
class AutoLock(
    private val repository: VaultRepository,
    private val settings: SettingsStore,
) : DefaultLifecycleObserver {
    private val handler = Handler(Looper.getMainLooper())
    private val lockNow = Runnable { repository.lock() }
    private var deadline = 0L
    private var graceUntil = 0L

    fun install() = ProcessLifecycleOwner.get().lifecycle.addObserver(this)

    /** Call before opening a system screen (file picker, browser) so a short trip doesn't lock. */
    fun allowExternalTrip() {
        graceUntil = SystemClock.elapsedRealtime() + EXTERNAL_GRACE_MS
    }

    override fun onStop(owner: LifecycleOwner) {
        val now = SystemClock.elapsedRealtime()
        var timeout = settings.autoLockSeconds.value * 1000L
        if (now < graceUntil) timeout = maxOf(timeout, graceUntil - now)
        if (timeout <= 0) {
            repository.lock()
        } else {
            deadline = now + timeout
            handler.postDelayed(lockNow, timeout)
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        handler.removeCallbacks(lockNow)
        if (deadline != 0L && SystemClock.elapsedRealtime() >= deadline) repository.lock()
        deadline = 0L
        graceUntil = 0L
    }

    private companion object {
        const val EXTERNAL_GRACE_MS = 3 * 60 * 1000L
    }
}
