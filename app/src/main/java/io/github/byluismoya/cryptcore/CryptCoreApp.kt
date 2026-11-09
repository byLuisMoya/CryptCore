package io.github.byluismoya.cryptcore

import android.app.Application
import io.github.byluismoya.cryptcore.data.SettingsStore
import io.github.byluismoya.cryptcore.data.VaultRepository
import io.github.byluismoya.cryptcore.security.AutoLock
import io.github.byluismoya.cryptcore.security.SecureClipboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class CryptCoreApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Manual DI: a handful of process-wide singletons. */
class AppContainer(app: Application) {
    val settings = SettingsStore(app)
    val repository = VaultRepository(app)
    val clipboard = SecureClipboard(app, settings)
    val autoLock = AutoLock(repository, settings).also { it.install() }

    /** Saves launched from a screen must outlive that screen's composition. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
