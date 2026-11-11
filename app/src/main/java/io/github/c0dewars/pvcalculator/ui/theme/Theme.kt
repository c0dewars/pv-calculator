package io.github.c0dewars.pvcalculator.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Light = lightColorScheme(
    primary = Color(0xFFC25E00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC2),
    onPrimaryContainer = Color(0xFF3A1A00),
    secondary = Color(0xFF0B7A5F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB4F1D9),
    onSecondaryContainer = Color(0xFF002118),
    tertiary = Color(0xFF2E62C9),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDAE2FF),
    onTertiaryContainer = Color(0xFF001946),
    background = Color(0xFFFFF8F4),
    onBackground = Color(0xFF221A14),
    surface = Color(0xFFFFF8F4),
    onSurface = Color(0xFF221A14),
    surfaceVariant = Color(0xFFF4DED0),
    onSurfaceVariant = Color(0xFF52443B),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF1E8),
    surfaceContainer = Color(0xFFFBEBE0),
    surfaceContainerHigh = Color(0xFFF5E5DA),
    surfaceContainerHighest = Color(0xFFEFDFD4),
    outline = Color(0xFF85746A),
    outlineVariant = Color(0xFFD8C2B5),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB77D),
    onPrimary = Color(0xFF552700),
    primaryContainer = Color(0xFF793A00),
    onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = Color(0xFF8BD6B9),
    onSecondary = Color(0xFF00382A),
    secondaryContainer = Color(0xFF00513E),
    onSecondaryContainer = Color(0xFFA7F2D4),
    tertiary = Color(0xFFB2C5FF),
    onTertiary = Color(0xFF002C71),
    tertiaryContainer = Color(0xFF00419F),
    onTertiaryContainer = Color(0xFFDAE2FF),
    background = Color(0xFF19120D),
    onBackground = Color(0xFFF0DFD6),
    surface = Color(0xFF19120D),
    onSurface = Color(0xFFF0DFD6),
    surfaceVariant = Color(0xFF52443B),
    onSurfaceVariant = Color(0xFFD8C2B5),
    surfaceContainerLowest = Color(0xFF130D08),
    // Cards a bit lighter than the background so they stand out in the dark.
    surfaceContainerLow = Color(0xFF261D16),
    surfaceContainer = Color(0xFF2B221B),
    surfaceContainerHigh = Color(0xFF362C25),
    surfaceContainerHighest = Color(0xFF41372F),
    outline = Color(0xFFA08D82),
    outlineVariant = Color(0xFF52443B),
)

/** Warm sunrise gradient used on the hero card. */
val SunGradient = Brush.linearGradient(listOf(Color(0xFFFFB13B), Color(0xFFF57C00), Color(0xFFD84315)))

/** Deeper version for dark mode: less glare, white text stays readable. */
val SunGradientDark = Brush.linearGradient(listOf(Color(0xFFD9822B), Color(0xFFB8520F), Color(0xFF8C2F12)))

/** True when the app is drawn with the dark color scheme. */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** Theme preference stored in the app data. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

fun themeModeOf(value: String?): ThemeMode = ThemeMode.entries.firstOrNull { it.name == value } ?: ThemeMode.SYSTEM

/** Colours used in charts and breakdowns, readable on both themes. */
object PvColors {
    val solar = Color(0xFFF59E0B)
    val savings = Color(0xFF10A37F)
    val export = Color(0xFF3B82F6)
    val grid = Color(0xFF94A3B8)
}

private val AppTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun PvTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) Dark else Light,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
