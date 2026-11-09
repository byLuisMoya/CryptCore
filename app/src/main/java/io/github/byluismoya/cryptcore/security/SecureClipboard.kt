package io.github.byluismoya.cryptcore.security

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.os.SystemClock
import io.github.byluismoya.cryptcore.data.SettingsStore

/**
 * Copies secrets flagged as sensitive (hidden from the Android 13+ clipboard preview) and wipes
 * them after the configured delay. A Handler gives precise timing while the process is alive; an
 * inexact alarm is the fallback when Android freezes the app in the background.
 */
class SecureClipboard(private val context: Context, private val settings: SettingsStore) {
    private val clipboard = context.getSystemService(ClipboardManager::class.java)
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val clearNow = Runnable {
        cancelAlarm()
        clear(context)
    }

    fun copy(text: String, sensitive: Boolean) {
        val clip = ClipData.newPlainText("CryptCore", text)
        if (sensitive) {
            clip.description.extras = PersistableBundle().apply { putBoolean(SENSITIVE_EXTRA, true) }
        }
        clipboard.setPrimaryClip(clip)

        handler.removeCallbacks(clearNow)
        cancelAlarm()
        val seconds = settings.clipboardSeconds.value
        if (sensitive && seconds > 0) {
            val delay = seconds * 1000L
            handler.postDelayed(clearNow, delay)
            alarms.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + delay, alarmIntent())
        }
    }

    private fun cancelAlarm() = alarms.cancel(alarmIntent())

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ClipboardClearReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private val SENSITIVE_EXTRA =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ClipDescription.EXTRA_IS_SENSITIVE
            } else {
                "android.content.extra.IS_SENSITIVE"
            }

        fun clear(context: Context) {
            runCatching { context.getSystemService(ClipboardManager::class.java).clearPrimaryClip() }
        }
    }
}

class ClipboardClearReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = SecureClipboard.clear(context)
}
