package io.github.byluismoya.cryptcore.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import io.github.byluismoya.cryptcore.data.ThemeMode

object Brand {
    val Violet = Color(0xFF6D5DFC)
    val Teal = Color(0xFF22C3B5)
    val gradient = Brush.linearGradient(listOf(Violet, Teal))
}

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA195FF),
    onPrimary = Color(0xFF1B1150),
    primaryContainer = Color(0xFF2F2675),
    onPrimaryContainer = Color(0xFFE6E0FF),
    secondary = Color(0xFF4FD8CB),
    onSecondary = Color(0xFF00201D),
    secondaryContainer = Color(0xFF0E3F3A),
    onSecondaryContainer = Color(0xFFB4F4EC),
    tertiary = Color(0xFFFFB86B),
    onTertiary = Color(0xFF3D2200),
    tertiaryContainer = Color(0xFF5A3500),
    onTertiaryContainer = Color(0xFFFFDDB8),
    error = Color(0xFFFF7A8A),
    onError = Color(0xFF4A0010),
    errorContainer = Color(0xFF6B0F22),
    onErrorContainer = Color(0xFFFFD9DD),
    background = Color(0xFF0B0D14),
    onBackground = Color(0xFFE6E8F2),
    surface = Color(0xFF0B0D14),
    onSurface = Color(0xFFE6E8F2),
    surfaceVariant = Color(0xFF22263A),
    onSurfaceVariant = Color(0xFFA9AEC6),
    surfaceContainerLowest = Color(0xFF07080D),
    surfaceContainerLow = Color(0xFF12151F),
    surfaceContainer = Color(0xFF161A26),
    surfaceContainerHigh = Color(0xFF1C2030),
    surfaceContainerHighest = Color(0xFF23283A),
    surfaceBright = Color(0xFF2A2F44),
    surfaceDim = Color(0xFF0B0D14),
    outline = Color(0xFF41465E),
    outlineVariant = Color(0xFF2A2E42),
    inverseSurface = Color(0xFFE6E8F2),
    inverseOnSurface = Color(0xFF181B27),
    inversePrimary = Color(0xFF5B4BE0),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B4BE0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E1FF),
    onPrimaryContainer = Color(0xFF1A1050),
    secondary = Color(0xFF0E8C82),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8F3EE),
    onSecondaryContainer = Color(0xFF00201D),
    tertiary = Color(0xFFB45309),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE2C2),
    onTertiaryContainer = Color(0xFF3D2200),
    error = Color(0xFFD12C48),
    onError = Color.White,
    errorContainer = Color(0xFFFFDADF),
    onErrorContainer = Color(0xFF410010),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF151827),
    surface = Color(0xFFF6F7FB),
    onSurface = Color(0xFF151827),
    surfaceVariant = Color(0xFFE3E5EF),
    onSurfaceVariant = Color(0xFF575C72),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEFF0F7),
    surfaceContainerHigh = Color(0xFFE9EAF3),
    surfaceContainerHighest = Color(0xFFE2E4EE),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFDCDEE8),
    outline = Color(0xFFB4B8CC),
    outlineVariant = Color(0xFFDDDFEA),
    inverseSurface = Color(0xFF242838),
    inverseOnSurface = Color(0xFFF0F1F8),
    inversePrimary = Color(0xFFA195FF),
)

private val base = Typography()

private val AppTypography = Typography(
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun CryptCoreTheme(themeMode: ThemeMode, dynamicColor: Boolean, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
}
