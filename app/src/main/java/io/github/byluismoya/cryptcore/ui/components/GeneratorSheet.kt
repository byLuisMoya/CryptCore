package io.github.byluismoya.cryptcore.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.byluismoya.cryptcore.core.PasswordGenerator
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordGeneratorSheet(onDismiss: () -> Unit, onUse: (String) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var length by remember { mutableIntStateOf(20) }
    var upper by remember { mutableStateOf(true) }
    var lower by remember { mutableStateOf(true) }
    var digits by remember { mutableStateOf(true) }
    var symbols by remember { mutableStateOf(true) }
    var avoidAmbiguous by remember { mutableStateOf(false) }
    var nonce by remember { mutableIntStateOf(0) }

    val password = remember(length, upper, lower, digits, symbols, avoidAmbiguous, nonce) {
        PasswordGenerator.generate(
            PasswordGenerator.Options(length, upper, lower, digits, symbols, avoidAmbiguous),
        )
    }
    val enabledCount = listOf(upper, lower, digits, symbols).count { it }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Generar contraseña", style = MaterialTheme.typography.titleLarge)

            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(Modifier.padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AnimatedContent(
                        targetState = password,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        modifier = Modifier.weight(1f),
                        label = "generated",
                    ) { pw ->
                        Text(
                            colorizedPassword(pw),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 18.sp,
                            lineHeight = 26.sp,
                            modifier = Modifier.padding(vertical = 10.dp),
                        )
                    }
                    FilledTonalIconButton(onClick = { nonce++ }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Generar otra")
                    }
                }
            }
            StrengthMeter(password)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Longitud", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("$length", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = length.toFloat(),
                onValueChange = { length = it.roundToInt() },
                valueRange = PasswordGenerator.MIN_LENGTH.toFloat()..PasswordGenerator.MAX_LENGTH.toFloat(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                @Composable
                fun chip(label: String, selected: Boolean, onToggle: (Boolean) -> Unit) {
                    FilterChip(
                        selected = selected,
                        // Never allow turning off the last remaining character class.
                        onClick = { if (!selected || enabledCount > 1) onToggle(!selected) },
                        label = {
                            Text(
                                label,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                chip("A-Z", upper) { upper = it }
                chip("a-z", lower) { lower = it }
                chip("0-9", digits) { digits = it }
                chip("#$%", symbols) { symbols = it }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Evitar caracteres ambiguos", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Sin l, I, 1, O, 0…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = avoidAmbiguous, onCheckedChange = { avoidAmbiguous = it })
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onUse(password) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text("Usar esta contraseña")
            }
        }
    }
}
