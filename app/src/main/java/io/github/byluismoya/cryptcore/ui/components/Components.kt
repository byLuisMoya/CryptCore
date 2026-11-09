package io.github.byluismoya.cryptcore.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.byluismoya.cryptcore.R
import io.github.byluismoya.cryptcore.core.PasswordStrength
import io.github.byluismoya.cryptcore.core.Strength
import io.github.byluismoya.cryptcore.ui.theme.Brand

@Composable
fun AppLogo(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(Brand.gradient),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_shield),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.56f),
        )
    }
}

/** Two soft colour glows behind the lock and setup screens. */
@Composable
fun GlowBackdrop(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    Canvas(modifier.fillMaxSize()) {
        val a = Offset(size.width * 0.15f, size.height * 0.1f)
        val b = Offset(size.width * 0.95f, size.height * 0.9f)
        val r = size.width * 0.95f
        drawCircle(Brush.radialGradient(listOf(primary.copy(alpha = 0.26f), Color.Transparent), a, r), r, a)
        drawCircle(Brush.radialGradient(listOf(secondary.copy(alpha = 0.16f), Color.Transparent), b, r), r, b)
    }
}

private val AvatarGradients = listOf(
    Color(0xFF6D5DFC) to Color(0xFF9F7AEA),
    Color(0xFF22C3B5) to Color(0xFF0EA5E9),
    Color(0xFFF97316) to Color(0xFFF43F5E),
    Color(0xFFEC4899) to Color(0xFF8B5CF6),
    Color(0xFF10B981) to Color(0xFF65A30D),
    Color(0xFF3B82F6) to Color(0xFF6366F1),
    Color(0xFFF59E0B) to Color(0xFFEF4444),
    Color(0xFF14B8A6) to Color(0xFF6D5DFC),
)

/** Coloured initial; no favicons because the app never touches the network. */
@Composable
fun EntryAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val key = name.trim().lowercase()
    val (from, to) = AvatarGradients[(key.hashCode() and Int.MAX_VALUE) % AvatarGradients.size]
    val initial = key.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "•"
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(Brush.linearGradient(listOf(from, to))),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
    }
}

@Composable
fun strengthColor(strength: Strength): Color = when (strength) {
    Strength.Empty -> MaterialTheme.colorScheme.outline
    Strength.VeryWeak -> Color(0xFFEF4444)
    Strength.Weak -> Color(0xFFF97316)
    Strength.Fair -> Color(0xFFEAB308)
    Strength.Strong -> Color(0xFF22C55E)
    Strength.VeryStrong -> Color(0xFF14B8A6)
}

@Composable
fun StrengthMeter(password: String, modifier: Modifier = Modifier) {
    val strength = remember(password) { PasswordStrength.estimate(password) }
    val color by animateColorAsState(strengthColor(strength), label = "strength")
    Column(modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(4) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(if (i < strength.level) color else MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
        if (strength != Strength.Empty) {
            Text(
                strength.label,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Digits and symbols tinted so a revealed password is easy to read out or type elsewhere. */
@Composable
fun colorizedPassword(password: String): AnnotatedString {
    val digit = MaterialTheme.colorScheme.secondary
    val symbol = MaterialTheme.colorScheme.tertiary
    return remember(password, digit, symbol) {
        buildAnnotatedString {
            password.forEach { c ->
                when {
                    c.isDigit() -> withStyle(SpanStyle(color = digit)) { append(c) }
                    !c.isLetter() -> withStyle(SpanStyle(color = symbol)) { append(c) }
                    else -> append(c)
                }
            }
        }
    }
}

@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    isError: Boolean = false,
    supportingText: String? = null,
    enabled: Boolean = true,
    extraTrailing: (@Composable () -> Unit)? = null,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
            autoCorrectEnabled = false,
        ),
        // Only Done/Go are custom; Next keeps its default "move focus" behaviour.
        keyboardActions = when (imeAction) {
            ImeAction.Done -> KeyboardActions(onDone = { onImeAction() })
            ImeAction.Go -> KeyboardActions(onGo = { onImeAction() })
            else -> KeyboardActions.Default
        },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                extraTrailing?.invoke()
                // Not focusable, so the keyboard's "Next" jumps to the next field instead of here.
                IconButton(onClick = { visible = !visible }, modifier = Modifier.focusProperties { canFocus = false }) {
                    Icon(
                        if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (visible) "Ocultar" else "Mostrar",
                    )
                }
            }
        },
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    )
}
