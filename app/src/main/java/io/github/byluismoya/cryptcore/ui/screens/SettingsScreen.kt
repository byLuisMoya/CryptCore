package io.github.byluismoya.cryptcore.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentPasteOff
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.byluismoya.cryptcore.AppContainer
import io.github.byluismoya.cryptcore.BuildConfig
import io.github.byluismoya.cryptcore.data.ImportResult
import io.github.byluismoya.cryptcore.data.SettingsStore
import io.github.byluismoya.cryptcore.data.ThemeMode
import io.github.byluismoya.cryptcore.data.VaultRepository
import io.github.byluismoya.cryptcore.security.BiometricKeys
import io.github.byluismoya.cryptcore.security.BiometricUnlock
import io.github.byluismoya.cryptcore.security.findFragmentActivity
import io.github.byluismoya.cryptcore.ui.components.PasswordField
import io.github.byluismoya.cryptcore.ui.components.StrengthMeter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Dialog { AutoLock, Clipboard, ChangePassword }

private const val MAX_BACKUP_BYTES = 32 * 1024 * 1024

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val repo = container.repository
    val settings = container.settings
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val biometricEnabled by repo.biometricEnabled.collectAsStateWithLifecycle()
    val autoLock by settings.autoLockSeconds.collectAsStateWithLifecycle()
    val clipboardSeconds by settings.clipboardSeconds.collectAsStateWithLifecycle()
    val themeMode by settings.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by settings.dynamicColor.collectAsStateWithLifecycle()

    var biometricAvailability by remember { mutableIntStateOf(BiometricKeys.availability(context)) }
    LifecycleResumeEffect(Unit) {
        biometricAvailability = BiometricKeys.availability(context)
        onPauseOrDispose { }
    }
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    var pendingImport by remember { mutableStateOf<ByteArray?>(null) }
    var biometricBusy by remember { mutableStateOf(false) }

    fun toast(message: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = try {
                val stream = withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt") }
                    ?: throw IOException("No se pudo abrir el destino")
                stream.use { repo.exportTo(it) }
                true
            } catch (e: Exception) {
                false
            }
            toast(if (ok) "Copia cifrada exportada" else "No se pudo exportar la copia")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { readLimited(context, uri) }
            if (bytes == null) toast("No se pudo leer el archivo") else pendingImport = bytes
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver") }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Section("Seguridad") {
                val canEnable = biometricAvailability == BiometricManager.BIOMETRIC_SUCCESS
                SettingRow(
                    icon = Icons.Rounded.Fingerprint,
                    title = "Desbloqueo biométrico",
                    subtitle = when {
                        biometricEnabled -> "Activado · huella o rostro (clase fuerte)"
                        canEnable -> "Entra con tu huella o rostro"
                        biometricAvailability == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                            "Registra una huella en los ajustes del sistema"
                        else -> "No disponible en este dispositivo"
                    },
                    enabled = !biometricBusy && (biometricEnabled || canEnable),
                    onClick = {
                        if (biometricEnabled) {
                            repo.disableBiometric()
                            toast("Desbloqueo biométrico desactivado")
                        } else {
                            biometricBusy = true
                            scope.launch {
                                val error = BiometricUnlock.enable(activity, repo)
                                biometricBusy = false
                                if (error != null) toast(error) else if (repo.biometricEnabled.value) toast("Desbloqueo biométrico activado")
                            }
                        }
                    },
                    trailing = { Switch(checked = biometricEnabled, onCheckedChange = null) },
                )
                SettingRow(
                    icon = Icons.Rounded.Timer,
                    title = "Bloqueo automático",
                    subtitle = SettingsStore.label(SettingsStore.AUTO_LOCK_OPTIONS, autoLock),
                    onClick = { dialog = Dialog.AutoLock },
                )
                SettingRow(
                    icon = Icons.Rounded.ContentPasteOff,
                    title = "Borrar portapapeles",
                    subtitle = if (clipboardSeconds == 0) {
                        "Nunca"
                    } else {
                        "Tras ${SettingsStore.label(SettingsStore.CLIPBOARD_OPTIONS, clipboardSeconds)}"
                    },
                    onClick = { dialog = Dialog.Clipboard },
                )
                SettingRow(
                    icon = Icons.Rounded.Password,
                    title = "Cambiar contraseña maestra",
                    onClick = { dialog = Dialog.ChangePassword },
                )
            }

            Section("Apariencia") {
                Row(
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(16.dp))
                    Text("Tema", style = MaterialTheme.typography.bodyLarge)
                }
                SingleChoiceSegmentedButtonRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 4.dp),
                ) {
                    ThemeMode.entries.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = themeMode == mode,
                            onClick = { settings.setThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = ThemeMode.entries.size),
                        ) { Text(mode.label) }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SettingRow(
                        icon = Icons.Rounded.Palette,
                        title = "Colores de Material You",
                        subtitle = "Usa la paleta de tu fondo de pantalla",
                        onClick = { settings.setDynamicColor(!dynamicColor) },
                        trailing = { Switch(checked = dynamicColor, onCheckedChange = null) },
                    )
                }
            }

            Section("Copia de seguridad") {
                SettingRow(
                    icon = Icons.Rounded.Upload,
                    title = "Exportar copia cifrada",
                    subtitle = "Archivo protegido con tu contraseña maestra actual",
                    onClick = {
                        container.autoLock.allowExternalTrip()
                        val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
                        exportLauncher.launch("cryptcore-$date.ccvault")
                    },
                )
                SettingRow(
                    icon = Icons.Rounded.Download,
                    title = "Importar copia",
                    subtitle = "Añade las claves de una copia a las actuales",
                    onClick = {
                        container.autoLock.allowExternalTrip()
                        importLauncher.launch(arrayOf("*/*"))
                    },
                )
            }

            Section("Acerca de") {
                SettingRow(
                    icon = Icons.Rounded.WifiOff,
                    title = "Sin acceso a Internet",
                    subtitle = "CryptCore no tiene permiso de red: tus datos nunca salen del dispositivo.",
                )
                SettingRow(
                    icon = Icons.Rounded.SystemUpdate,
                    title = "Buscar actualizaciones",
                    subtitle = "Versión ${BuildConfig.VERSION_NAME} · abre las releases de GitHub en tu navegador",
                    onClick = {
                        container.autoLock.allowExternalTrip()
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.RELEASES_URL)))
                        } catch (e: ActivityNotFoundException) {
                            toast("No hay ningún navegador instalado")
                        }
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    when (dialog) {
        Dialog.AutoLock -> ChoiceDialog(
            title = "Bloqueo automático",
            description = "Tiempo en segundo plano antes de pedir de nuevo la contraseña.",
            options = SettingsStore.AUTO_LOCK_OPTIONS,
            selected = autoLock,
            onSelect = { settings.setAutoLockSeconds(it); dialog = null },
            onDismiss = { dialog = null },
        )
        Dialog.Clipboard -> ChoiceDialog(
            title = "Borrar portapapeles",
            description = "Las contraseñas copiadas se eliminan del portapapeles pasado este tiempo.",
            options = SettingsStore.CLIPBOARD_OPTIONS,
            selected = clipboardSeconds,
            onSelect = { settings.setClipboardSeconds(it); dialog = null },
            onDismiss = { dialog = null },
        )
        Dialog.ChangePassword -> ChangePasswordDialog(
            repo = repo,
            onDone = { dialog = null; toast("Contraseña maestra actualizada") },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }

    pendingImport?.let { bytes ->
        ImportDialog(
            repo = repo,
            bytes = bytes,
            onResult = { message -> pendingImport = null; toast(message) },
            onDismiss = { pendingImport = null },
        )
    }
}

private fun readLimited(context: Context, uri: Uri): ByteArray? = try {
    context.contentResolver.openInputStream(uri)?.use { input ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > MAX_BACKUP_BYTES) return null
        }
        out.toByteArray()
    }
} catch (e: Exception) {
    null
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
        ) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .alpha(if (enabled) 1f else 0.5f),
    )
}

@Composable
private fun ChoiceDialog(
    title: String,
    description: String,
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
                options.forEach { (value, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onSelect(value) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = null, modifier = Modifier.padding(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun DialogProgressButton(text: String, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled && !busy) {
        if (busy) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp)) else Text(text)
    }
}

@Composable
private fun ChangePasswordDialog(repo: VaultRepository, onDone: () -> Unit, onDismiss: () -> Unit) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val tooShort = new.isNotEmpty() && new.length < MIN_MASTER_LENGTH
    val mismatch = confirm.isNotEmpty() && confirm != new
    val valid = current.isNotEmpty() && new.length >= MIN_MASTER_LENGTH && new == confirm

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Cambiar contraseña maestra") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PasswordField(current, { current = it; error = null }, "Contraseña actual", imeAction = ImeAction.Next, isError = error != null, supportingText = error)
                PasswordField(
                    new, { new = it }, "Nueva contraseña", imeAction = ImeAction.Next,
                    isError = tooShort, supportingText = if (tooShort) "Mínimo $MIN_MASTER_LENGTH caracteres" else null,
                )
                StrengthMeter(new)
                PasswordField(
                    confirm, { confirm = it }, "Repite la nueva",
                    isError = mismatch, supportingText = if (mismatch) "No coinciden" else null,
                )
                Text(
                    "Tus copias exportadas antes seguirán usando la contraseña anterior.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            DialogProgressButton("Cambiar", busy, valid) {
                scope.launch {
                    busy = true
                    val a = current.toCharArray()
                    val b = new.toCharArray()
                    val outcome = try {
                        if (repo.changePassword(a, b)) null else "La contraseña actual no es correcta"
                    } catch (e: Exception) {
                        "No se pudo guardar el cambio"
                    } finally {
                        a.fill('\u0000')
                        b.fill('\u0000')
                    }
                    busy = false
                    if (outcome == null) onDone() else error = outcome
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } },
    )
}

@Composable
private fun ImportDialog(repo: VaultRepository, bytes: ByteArray, onResult: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (password.isEmpty() || busy) return
        scope.launch {
            busy = true
            val chars = password.toCharArray()
            val result = try {
                repo.importBackup(bytes, chars)
            } catch (e: Exception) {
                ImportResult.InvalidFile
            } finally {
                chars.fill('\u0000')
            }
            busy = false
            when (result) {
                is ImportResult.Ok -> onResult(
                    if (result.added + result.updated == 0) {
                        "La copia no tenía nada nuevo"
                    } else {
                        "Importadas: ${result.added} nuevas, ${result.updated} actualizadas"
                    },
                )
                ImportResult.WrongPassword -> error = "Contraseña incorrecta para esta copia"
                ImportResult.InvalidFile -> onResult("El archivo no es una copia válida de CryptCore")
                ImportResult.VaultLocked -> onResult("El almacén se bloqueó; vuelve a intentarlo")
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Importar copia") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Introduce la contraseña maestra con la que se creó la copia. Las claves se añadirán a las actuales; si una ya existe, se queda la versión más reciente.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                PasswordField(
                    password, { password = it; error = null }, "Contraseña de la copia",
                    onImeAction = ::submit, isError = error != null, supportingText = error,
                )
            }
        },
        confirmButton = { DialogProgressButton("Importar", busy, password.isNotEmpty(), ::submit) },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } },
    )
}
