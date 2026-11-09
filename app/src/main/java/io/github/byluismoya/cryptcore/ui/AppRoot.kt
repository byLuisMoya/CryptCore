package io.github.byluismoya.cryptcore.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.byluismoya.cryptcore.AppContainer
import io.github.byluismoya.cryptcore.data.Entry
import io.github.byluismoya.cryptcore.data.VaultState
import io.github.byluismoya.cryptcore.ui.screens.EntryDetailScreen
import io.github.byluismoya.cryptcore.ui.screens.EntryEditScreen
import io.github.byluismoya.cryptcore.ui.screens.HomeScreen
import io.github.byluismoya.cryptcore.ui.screens.LockScreen
import io.github.byluismoya.cryptcore.ui.screens.SettingsScreen
import io.github.byluismoya.cryptcore.ui.screens.SetupScreen
import kotlinx.coroutines.launch

@Composable
fun AppRoot(container: AppContainer) {
    val state by container.repository.state.collectAsStateWithLifecycle()
    AnimatedContent(
        targetState = state,
        contentKey = { it::class },
        transitionSpec = {
            (fadeIn(tween(350)) + scaleIn(tween(350), initialScale = 0.97f)) togetherWith fadeOut(tween(180))
        },
        label = "root",
    ) { s ->
        when (s) {
            VaultState.NoVault -> SetupScreen(container)
            VaultState.Locked -> LockScreen(container)
            is VaultState.Unlocked -> UnlockedApp(container, s.entries)
        }
    }
}

private sealed interface Route {
    data object Home : Route
    data object Settings : Route
    data class Detail(val id: String) : Route
    data class Edit(val id: String?) : Route
}

/*
 * Navigation is a tiny in-memory back stack. MainActivity handles configuration changes itself
 * (see the manifest), so plain remember survives rotation without ever writing secrets into the
 * saved-instance Bundle.
 */
@Composable
private fun UnlockedApp(container: AppContainer, entries: List<Entry>) {
    val repo = container.repository
    val stack = remember { mutableStateListOf<Route>(Route.Home) }
    var forward by remember { mutableStateOf(true) }
    val push: (Route) -> Unit = { forward = true; stack.add(it) }
    val pop: () -> Unit = {
        if (stack.size > 1) {
            forward = false
            stack.removeAt(stack.lastIndex)
        }
    }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipSeconds by container.settings.clipboardSeconds.collectAsStateWithLifecycle()
    val copy: (String, String, Boolean) -> Unit = { message, value, sensitive ->
        container.clipboard.copy(value, sensitive)
        val suffix = if (sensitive && clipSeconds > 0) " · se borrará en $clipSeconds s" else ""
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar("$message$suffix", duration = SnackbarDuration.Short)
        }
    }

    BackHandler(enabled = stack.size > 1, onBack = pop)

    AnimatedContent(
        targetState = stack.last(),
        transitionSpec = {
            if (forward) {
                (slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(320))) togetherWith
                    (slideOutHorizontally(tween(320)) { -it / 8 } + fadeOut(tween(200)))
            } else {
                (slideInHorizontally(tween(320)) { -it / 8 } + fadeIn(tween(320))) togetherWith
                    (slideOutHorizontally(tween(320)) { it / 3 } + fadeOut(tween(200)))
            }
        },
        label = "nav",
    ) { route ->
        when (route) {
            Route.Home -> HomeScreen(
                entries = entries,
                snackbar = snackbar,
                onOpen = { push(Route.Detail(it)) },
                onAdd = { push(Route.Edit(null)) },
                onSettings = { push(Route.Settings) },
                onLock = repo::lock,
                onCopyPassword = { copy("Contraseña copiada", it.password, true) },
            )

            is Route.Detail -> {
                // Keep showing the last known entry while it animates out after a delete.
                val found = entries.firstOrNull { it.id == route.id }
                val last = remember { mutableStateOf(found) }
                if (found != null) last.value = found
                val shown = last.value
                if (shown == null) {
                    LaunchedEffect(Unit) { pop() }
                } else {
                    EntryDetailScreen(
                        entry = shown,
                        snackbar = snackbar,
                        onBack = pop,
                        onEdit = { push(Route.Edit(shown.id)) },
                        onDelete = {
                            container.appScope.launch { repo.delete(shown.id) }
                            pop()
                        },
                        onToggleFavorite = {
                            container.appScope.launch { repo.setFavorite(shown.id, !shown.favorite) }
                        },
                        onCopy = copy,
                    )
                }
            }

            is Route.Edit -> {
                val initial = route.id?.let { id -> entries.firstOrNull { it.id == id } }
                if (route.id != null && initial == null) {
                    LaunchedEffect(Unit) { pop() }
                } else {
                    EntryEditScreen(
                        initial = initial,
                        onClose = pop,
                        onSave = { entry ->
                            container.appScope.launch { repo.upsert(entry) }
                            pop()
                        },
                    )
                }
            }

            Route.Settings -> SettingsScreen(container, onBack = pop)
        }
    }
}
