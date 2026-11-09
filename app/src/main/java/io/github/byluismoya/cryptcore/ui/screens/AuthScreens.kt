package io.github.byluismoya.cryptcore.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.withResumed
import io.github.byluismoya.cryptcore.AppContainer
import io.github.byluismoya.cryptcore.core.MasterPasswordReminder
import io.github.byluismoya.cryptcore.crypto.CorruptVaultException
import io.github.byluismoya.cryptcore.security.BiometricUnlock
import io.github.byluismoya.cryptcore.security.findFragmentActivity
import io.github.byluismoya.cryptcore.ui.components.AppLogo
import io.github.byluismoya.cryptcore.ui.components.GlowBackdrop
import io.github.byluismoya.cryptcore.ui.components.PasswordField
import io.github.byluismoya.cryptcore.ui.components.StrengthMeter
import kotlinx.coroutines.launch

const val MIN_MASTER_LENGTH = 8

/** Centered, scrollable column over the glow backdrop, shared by setup and lock screens. */
@Composable
private fun AuthScaffold(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        GlowBackdrop()
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Column(
                    Modifier.widthIn(max = 420.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun OfflineFooter() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 32.dp),
    ) {
        Icon(
            Icons.Rounded.WifiOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Text(
            "  Sin conexión · AES-256-GCM + Argon2id",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BusyButton(text: String, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(22.dp),
            )
        } else {
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun SetupScreen(container: AppContainer) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val tooShort = password.isNotEmpty() && password.length < MIN_MASTER_LENGTH
    val mismatch = confirm.isNotEmpty() && confirm != password
    val valid = password.length >= MIN_MASTER_LENGTH && password == confirm

    fun submit() {
        if (!valid || busy) return
        scope.launch {
            busy = true
            error = null
            val chars = password.toCharArray()
            try {
                container.repository.create(chars)
            } catch (e: Exception) {
                error = "No se pudo crear el almacén: ${e.message}"
            } finally {
                chars.fill('\u0000')
                busy = false
            }
        }
    }

    AuthScaffold {
        AppLogo(88.dp)
        Spacer(Modifier.height(24.dp))
        Text("Bienvenido a CryptCore", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Crea tu contraseña maestra. Cifra todas tus claves y nunca sale de este dispositivo.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))

        PasswordField(
            value = password,
            onValueChange = { password = it },
            label = "Contraseña maestra",
            imeAction = ImeAction.Next,
            isError = tooShort,
            supportingText = if (tooShort) "Mínimo $MIN_MASTER_LENGTH caracteres" else null,
        )
        StrengthMeter(password, Modifier.padding(vertical = 8.dp))
        PasswordField(
            value = confirm,
            onValueChange = { confirm = it },
            label = "Repite la contraseña",
            onImeAction = ::submit,
            isError = mismatch,
            supportingText = if (mismatch) "No coinciden" else null,
        )
        Spacer(Modifier.height(16.dp))

        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.WarningAmber, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(Modifier.size(12.dp))
                Text(
                    "Si la olvidas no hay forma de recuperar tus datos. Nadie, ni siquiera el desarrollador, puede restablecerla.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
        }
        BusyButton("Crear almacén", busy = busy, enabled = valid, onClick = ::submit)
        if (busy) {
            Text(
                "Derivando la clave… tarda un segundo a propósito",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        OfflineFooter()
    }
}

@Composable
fun LockScreen(container: AppContainer) {
    val repo = container.repository
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val biometricEnabled by repo.biometricEnabled.collectAsStateWithLifecycle()
    val settings = container.settings
    val reminderDays by settings.masterReminderDays.collectAsStateWithLifecycle()
    // Decided once per lock screen: when due, the fingerprint is not offered until the password is typed.
    val passwordDue = remember { settings.isMasterPasswordDue() }
    val offerBiometric = biometricEnabled && !passwordDue

    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val shake = remember { Animatable(0f) }
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (password.isEmpty() || busy) return
        scope.launch {
            busy = true
            error = null
            val chars = password.toCharArray()
            try {
                if (!repo.unlock(chars)) {
                    error = "Contraseña incorrecta"
                    password = ""
                    shake.animateTo(0f, keyframes {
                        durationMillis = 380
                        -18f at 50
                        16f at 110
                        -11f at 170
                        8f at 230
                        -4f at 290
                    })
                }
            } catch (e: CorruptVaultException) {
                error = "El almacén está dañado y no se puede leer"
            } catch (e: Exception) {
                error = "No se pudo leer el almacén"
            } finally {
                chars.fill('\u0000')
                busy = false
            }
        }
    }

    fun biometric() {
        if (settings.isMasterPasswordDue()) return
        scope.launch { error = BiometricUnlock.unlock(activity, repo) }
    }

    // Offer biometrics straight away (once the screen is really in front, as the vault can lock
    // while the app is in the background); otherwise put the cursor in the password field.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        lifecycle.withResumed {}
        if (offerBiometric) biometric() else runCatching { focus.requestFocus() }
    }

    AuthScaffold {
        AppLogo(84.dp)
        Spacer(Modifier.height(24.dp))
        Text("CryptCore", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Introduce tu contraseña maestra",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        if (biometricEnabled && passwordDue) {
            val days = MasterPasswordReminder.daysSince(settings.lastMasterPasswordAt, System.currentTimeMillis())
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.padding(bottom = 20.dp),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Key, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.size(12.dp))
                    Text(
                        (if (days >= reminderDays) "Hace $days días que no usas tu contraseña maestra. " else "") +
                            "Escríbela para no olvidarla; después podrás seguir entrando con la huella.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }

        PasswordField(
            value = password,
            onValueChange = { password = it; error = null },
            label = "Contraseña maestra",
            onImeAction = ::submit,
            isError = error != null,
            modifier = Modifier
                .offset(x = shake.value.dp)
                .focusRequester(focus),
        )
        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Text(
                error.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        BusyButton("Desbloquear", busy = busy, enabled = password.isNotEmpty(), onClick = ::submit)

        AnimatedVisibility(visible = offerBiometric) {
            OutlinedButton(
                onClick = ::biometric,
                enabled = !busy,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                Icon(Icons.Rounded.Fingerprint, contentDescription = null)
                Spacer(Modifier.size(10.dp))
                Text("Usar biometría")
            }
        }
        OfflineFooter()
    }
}
