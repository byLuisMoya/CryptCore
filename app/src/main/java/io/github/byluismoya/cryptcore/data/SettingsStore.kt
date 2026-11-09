package io.github.byluismoya.cryptcore.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) { System("Sistema"), Light("Claro"), Dark("Oscuro") }

/** Non-secret preferences only. Nothing here reveals vault contents. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        ThemeMode.entries.getOrElse(prefs.getInt(K_THEME, 0)) { ThemeMode.System },
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(prefs.getBoolean(K_DYNAMIC, false))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _autoLockSeconds = MutableStateFlow(prefs.getInt(K_AUTOLOCK, 60))
    val autoLockSeconds: StateFlow<Int> = _autoLockSeconds.asStateFlow()

    private val _clipboardSeconds = MutableStateFlow(prefs.getInt(K_CLIPBOARD, 30))
    val clipboardSeconds: StateFlow<Int> = _clipboardSeconds.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit { putInt(K_THEME, mode.ordinal) }
        _themeMode.value = mode
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit { putBoolean(K_DYNAMIC, enabled) }
        _dynamicColor.value = enabled
    }

    fun setAutoLockSeconds(seconds: Int) {
        prefs.edit { putInt(K_AUTOLOCK, seconds) }
        _autoLockSeconds.value = seconds
    }

    fun setClipboardSeconds(seconds: Int) {
        prefs.edit { putInt(K_CLIPBOARD, seconds) }
        _clipboardSeconds.value = seconds
    }

    companion object {
        private const val K_THEME = "theme_mode"
        private const val K_DYNAMIC = "dynamic_color"
        private const val K_AUTOLOCK = "auto_lock_seconds"
        private const val K_CLIPBOARD = "clipboard_seconds"

        val AUTO_LOCK_OPTIONS = listOf(
            0 to "Al salir de la app",
            30 to "30 segundos",
            60 to "1 minuto",
            300 to "5 minutos",
            900 to "15 minutos",
        )
        val CLIPBOARD_OPTIONS = listOf(
            15 to "15 segundos",
            30 to "30 segundos",
            60 to "1 minuto",
            120 to "2 minutos",
            0 to "Nunca",
        )

        fun label(options: List<Pair<Int, String>>, value: Int) =
            options.firstOrNull { it.first == value }?.second ?: "$value s"
    }
}
