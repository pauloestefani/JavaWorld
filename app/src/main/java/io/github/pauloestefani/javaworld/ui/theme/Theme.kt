package io.github.pauloestefani.javaworld.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object JW {
    val Grass = Color(0xFF6BD66F)
    val GrassDeep = Color(0xFF2E7D32)
    val Gold = Color(0xFFF2B45A)
    val Diamond = Color(0xFF5CC8E8)
    val Redstone = Color(0xFFFF6E66)
    val Amethyst = Color(0xFFB79CFF)
    val Muted = Color(0xFF7C8C85)
    val Mono = FontFamily.Monospace
}

private val colors = darkColorScheme(
    primary = JW.Grass,
    onPrimary = Color(0xFF06210A),
    primaryContainer = Color(0xFF1B4721),
    onPrimaryContainer = Color(0xFFC0F5BC),
    secondary = JW.Gold,
    onSecondary = Color(0xFF2B1A00),
    secondaryContainer = Color(0xFF48330F),
    onSecondaryContainer = Color(0xFFFFDDB0),
    tertiary = JW.Diamond,
    onTertiary = Color(0xFF00222C),
    tertiaryContainer = Color(0xFF0F3E4D),
    onTertiaryContainer = Color(0xFFC0ECFA),
    background = Color(0xFF0C110F),
    onBackground = Color(0xFFE3EAE5),
    surface = Color(0xFF0C110F),
    onSurface = Color(0xFFE3EAE5),
    surfaceVariant = Color(0xFF1D2723),
    onSurfaceVariant = Color(0xFFA6B6AE),
    surfaceTint = JW.Grass,
    surfaceContainerLowest = Color(0xFF080C0A),
    surfaceContainerLow = Color(0xFF111815),
    surfaceContainer = Color(0xFF151D19),
    surfaceContainerHigh = Color(0xFF1B2520),
    surfaceContainerHighest = Color(0xFF222D28),
    surfaceBright = Color(0xFF2A3631),
    surfaceDim = Color(0xFF0C110F),
    outline = Color(0xFF3B4B44),
    outlineVariant = Color(0xFF26332D),
    error = JW.Redstone,
    onError = Color(0xFF3A0905),
    errorContainer = Color(0xFF5A1A14),
    onErrorContainer = Color(0xFFFFDAD5),
    inverseSurface = Color(0xFFE3EAE5),
    inverseOnSurface = Color(0xFF1A211E),
    inversePrimary = JW.GrassDeep,
    scrim = Color(0xFF000000),
)

private val base = Typography()
private val typography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun JavaWorldTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
}
