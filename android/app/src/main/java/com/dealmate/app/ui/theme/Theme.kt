package com.dealmate.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Brand: warm neutral surfaces + one orange accent (seed #F26B1D). No dynamic colour on purpose.
private val Orange = Color(0xFFD9540D)
private val OrangeBright = Color(0xFFFF9A5C)

private val LightColors = lightColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE9DC),
    onPrimaryContainer = Color(0xFF4A1B00),
    secondary = Color(0xFF6B5D53),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1E7DE),
    onSecondaryContainer = Color(0xFF2B211A),
    tertiary = Color(0xFF1F7A54),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDF3E7),
    onTertiaryContainer = Color(0xFF0B3523),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFBE4E1),
    onErrorContainer = Color(0xFF5C120D),
    background = Color(0xFFFAF7F3),
    onBackground = Color(0xFF211C18),
    surface = Color(0xFFFAF7F3),
    onSurface = Color(0xFF211C18),
    surfaceVariant = Color(0xFFF1EBE4),
    onSurfaceVariant = Color(0xFF6A6058),
    outline = Color(0xFFB9ADA2),
    outlineVariant = Color(0xFFE6DED6),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF5F0EA),
    surfaceContainerHigh = Color(0xFFEFE9E2),
    surfaceContainerHighest = Color(0xFFE9E2DA),
    inverseSurface = Color(0xFF362F2A),
    inverseOnSurface = Color(0xFFFBEEE5),
    inversePrimary = OrangeBright,
    scrim = Color.Black,
)

private val DarkColors = darkColorScheme(
    primary = OrangeBright,
    onPrimary = Color(0xFF4A1B00),
    primaryContainer = Color(0xFF55260A),
    onPrimaryContainer = Color(0xFFFFDCC8),
    secondary = Color(0xFFD6C3B6),
    onSecondary = Color(0xFF3A2E25),
    secondaryContainer = Color(0xFF3A322C),
    onSecondaryContainer = Color(0xFFF1E0D4),
    tertiary = Color(0xFF7FD6A9),
    onTertiary = Color(0xFF00391F),
    tertiaryContainer = Color(0xFF163B2A),
    onTertiaryContainer = Color(0xFFBFEFD5),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF4A1512),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF151311),
    onBackground = Color(0xFFEDE5DE),
    surface = Color(0xFF151311),
    onSurface = Color(0xFFEDE5DE),
    surfaceVariant = Color(0xFF2C2824),
    onSurfaceVariant = Color(0xFFB9ADA3),
    outline = Color(0xFF6E645C),
    outlineVariant = Color(0xFF38322D),
    surfaceContainerLowest = Color(0xFF100E0D),
    surfaceContainerLow = Color(0xFF1F1C19),
    surfaceContainer = Color(0xFF23201D),
    surfaceContainerHigh = Color(0xFF2B2723),
    surfaceContainerHighest = Color(0xFF36312D),
    inverseSurface = Color(0xFFEDE5DE),
    inverseOnSurface = Color(0xFF362F2A),
    inversePrimary = Orange,
    scrim = Color.Black,
)

/** Semantic colours that Material 3 has no role for. */
@Immutable
data class StatusColors(
    val verified: Color,
    val verifiedContainer: Color,
    val estimated: Color,
    val estimatedContainer: Color,
    val neutral: Color,
    val neutralContainer: Color,
    val priceDown: Color,
    val priceUp: Color,
)

private val LightStatus = StatusColors(
    verified = Color(0xFF1F7A54),
    verifiedContainer = Color(0xFFDDF3E7),
    estimated = Color(0xFF8A5A00),
    estimatedContainer = Color(0xFFFCEFCF),
    neutral = Color(0xFF6A6058),
    neutralContainer = Color(0xFFEFE9E2),
    priceDown = Color(0xFF1F7A54),
    priceUp = Color(0xFFB3261E),
)

private val DarkStatus = StatusColors(
    verified = Color(0xFF7FD6A9),
    verifiedContainer = Color(0xFF163B2A),
    estimated = Color(0xFFF2C46B),
    estimatedContainer = Color(0xFF41310B),
    neutral = Color(0xFFB9ADA3),
    neutralContainer = Color(0xFF2B2723),
    priceDown = Color(0xFF7FD6A9),
    priceUp = Color(0xFFFFB4AB),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

private val base = Typography()
private val AppTypography = Typography(
    headlineSmall = base.headlineSmall.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    bodySmall = base.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.sp),
    labelLarge = base.labelLarge.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    labelSmall = base.labelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
)

/** Big price figures. */
val PriceLarge = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
val PriceMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)

@Composable
fun DealMateTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

object DealMateTheme {
    val status: StatusColors
        @Composable get() = LocalStatusColors.current
}
