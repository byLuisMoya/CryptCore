package io.github.byluismoya.cryptcore.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.byluismoya.cryptcore.data.Entry
import io.github.byluismoya.cryptcore.ui.components.EntryAvatar
import io.github.byluismoya.cryptcore.ui.components.PasswordField
import io.github.byluismoya.cryptcore.ui.components.PasswordGeneratorSheet
import io.github.byluismoya.cryptcore.ui.components.StrengthMeter
import io.github.byluismoya.cryptcore.ui.components.colorizedPassword
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val dateFormat: DateFormat =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.forLanguageTag("es-ES"))

// ------------------------------------------------------------------------------------------------
// Detail
// ------------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(
    entry: Entry,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
    onCopy: (message: String, value: String, sensitive: Boolean) -> Unit,
) {
    var revealed by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver") }
                },
                actions = {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            if (entry.favorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                            contentDescription = if (entry.favorite) "Quitar de favoritas" else "Marcar como favorita",
                            tint = if (entry.favorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, contentDescription = "Editar") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, contentDescription = "Eliminar") }
                },
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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EntryAvatar(entry.name, size = 76.dp)
            Spacer(Modifier.height(14.dp))
            Text(entry.name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            if (entry.website.isNotBlank()) {
                Text(
                    entry.website,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(24.dp))

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
            ) {
                Column {
                    val rows = buildList<@Composable () -> Unit> {
                        if (entry.username.isNotBlank()) add {
                            FieldRow(Icons.Rounded.Person, "Usuario", entry.username) { onCopy("Usuario copiado", entry.username, false) }
                        }
                        if (entry.email.isNotBlank()) add {
                            FieldRow(Icons.Rounded.AlternateEmail, "Correo", entry.email) { onCopy("Correo copiado", entry.email, false) }
                        }
                        add {
                            PasswordRow(entry.password, revealed, { revealed = !revealed }) {
                                onCopy("Contraseña copiada", entry.password, true)
                            }
                        }
                        if (entry.website.isNotBlank()) add {
                            FieldRow(Icons.Rounded.Language, "Web", entry.website) { onCopy("Web copiada", entry.website, false) }
                        }
                    }
                    rows.forEachIndexed { i, row ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        row()
                    }
                }
            }

            if (entry.notes.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FieldRow(Icons.Rounded.Description, "Notas", entry.notes, singleLine = false) {
                        onCopy("Notas copiadas", entry.notes, true)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Creada el ${dateFormat.format(Date(entry.createdAt))}" +
                    if (entry.updatedAt != entry.createdAt) "\nModificada el ${dateFormat.format(Date(entry.updatedAt))}" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
            title = { Text("¿Eliminar «${entry.name}»?") },
            text = { Text("Se borrará de este dispositivo. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun FieldRow(
    icon: ImageVector,
    label: String,
    value: String,
    singleLine: Boolean = true,
    onCopy: () -> Unit,
) {
    Row(Modifier.padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, maxLines = if (singleLine) 2 else Int.MAX_VALUE)
        }
        IconButton(onClick = onCopy) { Icon(Icons.Rounded.ContentCopy, contentDescription = "Copiar $label") }
    }
}

@Composable
private fun PasswordRow(password: String, revealed: Boolean, onToggle: () -> Unit, onCopy: () -> Unit) {
    Column(Modifier.padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(18.dp))
            Column(Modifier.weight(1f)) {
                Text("Contraseña", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AnimatedContent(revealed, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "reveal") { show ->
                    if (show) {
                        Text(colorizedPassword(password), style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
                    } else {
                        Text("•".repeat(password.length.coerceIn(8, 16)), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            IconButton(onClick = onToggle) {
                Icon(
                    if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (revealed) "Ocultar contraseña" else "Mostrar contraseña",
                )
            }
            IconButton(onClick = onCopy) { Icon(Icons.Rounded.ContentCopy, contentDescription = "Copiar contraseña") }
        }
        StrengthMeter(password, Modifier.padding(start = 40.dp, end = 10.dp, top = 8.dp))
    }
}

// ------------------------------------------------------------------------------------------------
// Create / edit
// ------------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditScreen(initial: Entry?, onClose: () -> Unit, onSave: (Entry) -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var username by remember { mutableStateOf(initial?.username.orEmpty()) }
    var email by remember { mutableStateOf(initial?.email.orEmpty()) }
    var password by remember { mutableStateOf(initial?.password.orEmpty()) }
    var website by remember { mutableStateOf(initial?.website.orEmpty()) }
    var notes by remember { mutableStateOf(initial?.notes.orEmpty()) }
    var showGenerator by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val dirty = name != initial?.name.orEmpty() ||
        username != initial?.username.orEmpty() ||
        email != initial?.email.orEmpty() ||
        password != initial?.password.orEmpty() ||
        website != initial?.website.orEmpty() ||
        notes != initial?.notes.orEmpty()
    val canSave = name.isNotBlank() && password.isNotEmpty() && dirty
    val close = { if (dirty) confirmDiscard = true else onClose() }

    fun save() {
        if (!canSave) return
        val now = System.currentTimeMillis()
        val base = initial ?: Entry(name = "", createdAt = now)
        onSave(
            base.copy(
                name = name.trim(),
                username = username.trim(),
                email = email.trim(),
                password = password,
                website = website.trim(),
                notes = notes.trim(),
                updatedAt = now,
            ),
        )
    }

    BackHandler(enabled = dirty) { confirmDiscard = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initial == null) "Nueva clave" else "Editar clave") },
                navigationIcon = { IconButton(onClick = close) { Icon(Icons.Rounded.Close, contentDescription = "Cerrar") } },
                actions = {
                    Button(onClick = ::save, enabled = canSave, modifier = Modifier.padding(end = 8.dp)) { Text("Guardar") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EntryAvatar(name.ifBlank { "?" }, size = 56.dp)
                Spacer(Modifier.size(14.dp))
                Text(
                    "El nombre identifica el perfil: Netflix, Gmail, el banco…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TextInput(
                name, { name = it }, "Nombre *", Icons.AutoMirrored.Rounded.Label,
                capitalization = KeyboardCapitalization.Sentences,
            )
            TextInput(username, { username = it }, "Usuario (opcional)", Icons.Rounded.Person)
            TextInput(email, { email = it }, "Correo (opcional)", Icons.Rounded.AlternateEmail, keyboardType = KeyboardType.Email)

            PasswordField(
                value = password,
                onValueChange = { password = it },
                label = "Contraseña *",
                imeAction = ImeAction.Next,
                extraTrailing = {
                    IconButton(onClick = { showGenerator = true }, modifier = Modifier.focusProperties { canFocus = false }) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = "Generar contraseña")
                    }
                },
            )
            StrengthMeter(password)
            FilledTonalButton(onClick = { showGenerator = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Generar contraseña segura")
            }

            TextInput(website, { website = it }, "Web o app (opcional)", Icons.Rounded.Language, keyboardType = KeyboardType.Uri)
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notas (opcional)") },
                leadingIcon = { Icon(Icons.Rounded.Description, contentDescription = null) },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Las notas también se guardan cifradas. Útil para códigos de recuperación o preguntas de seguridad.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showGenerator) {
        PasswordGeneratorSheet(
            onDismiss = { showGenerator = false },
            onUse = { password = it; showGenerator = false },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("¿Descartar cambios?") },
            text = { Text("Lo que has escrito no se guardará.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onClose() }) { Text("Descartar") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Seguir editando") } },
        )
    }
}

@Composable
private fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            capitalization = capitalization,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Next,
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    )
}
