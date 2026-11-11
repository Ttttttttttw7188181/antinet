package com.antinet.vpn.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antinet.vpn.data.AppSettings

data class AccentPalette(val id: String, val title: String, val hue: Float)

val AccentPalettes = listOf(
    AccentPalette("ocean", "Океан", 172f),
    AccentPalette("violet", "Фиалка", 268f),
    AccentPalette("rose", "Роза", 338f),
    AccentPalette("amber", "Янтарь", 36f),
    AccentPalette("forest", "Лес", 142f),
    AccentPalette("steel", "Сталь", 218f),
)

private val ErrorDark = Color(0xFFFFB4AB)
private val OnErrorDark = Color(0xFF690005)
private val ErrorContainerDark = Color(0xFF93000A)
private val OnErrorContainerDark = Color(0xFFFFDAD6)
private val ErrorLight = Color(0xFFBA1A1A)
private val OnErrorLight = Color(0xFFFFFFFF)
private val ErrorContainerLight = Color(0xFFFFDAD6)
private val OnErrorContainerLight = Color(0xFF410002)

private fun darkScheme(hue: Float) = darkColorScheme(
    primary = Color.hsl(hue, 0.60f, 0.80f),
    onPrimary = Color.hsl(hue, 0.60f, 0.15f),
    primaryContainer = Color.hsl(hue, 0.42f, 0.30f),
    onPrimaryContainer = Color.hsl(hue, 0.70f, 0.92f),
    inversePrimary = Color.hsl(hue, 0.55f, 0.42f),
    secondary = Color.hsl((hue + 28f) % 360f, 0.30f, 0.80f),
    onSecondary = Color.hsl((hue + 28f) % 360f, 0.25f, 0.15f),
    secondaryContainer = Color.hsl((hue + 28f) % 360f, 0.25f, 0.25f),
    onSecondaryContainer = Color.hsl((hue + 28f) % 360f, 0.35f, 0.91f),
    tertiary = Color.hsl((hue + 65f) % 360f, 0.50f, 0.81f),
    onTertiary = Color.hsl((hue + 65f) % 360f, 0.35f, 0.15f),
    tertiaryContainer = Color.hsl((hue + 65f) % 360f, 0.35f, 0.28f),
    onTertiaryContainer = Color.hsl((hue + 65f) % 360f, 0.55f, 0.93f),
    background = Color.hsl(hue, 0.22f, 0.055f),
    onBackground = Color.hsl(hue, 0.16f, 0.93f),
    surface = Color.hsl(hue, 0.20f, 0.075f),
    onSurface = Color.hsl(hue, 0.14f, 0.93f),
    surfaceVariant = Color.hsl(hue, 0.15f, 0.17f),
    onSurfaceVariant = Color.hsl(hue, 0.11f, 0.78f),
    surfaceTint = Color.hsl(hue, 0.60f, 0.80f),
    inverseSurface = Color.hsl(hue, 0.10f, 0.92f),
    inverseOnSurface = Color.hsl(hue, 0.16f, 0.17f),
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    outline = Color.hsl(hue, 0.09f, 0.40f),
    outlineVariant = Color.hsl(hue, 0.10f, 0.27f),
    scrim = Color.Black,
    surfaceBright = Color.hsl(hue, 0.15f, 0.22f),
    surfaceDim = Color.hsl(hue, 0.18f, 0.09f),
    surfaceContainerLowest = Color.hsl(hue, 0.21f, 0.05f),
    surfaceContainerLow = Color.hsl(hue, 0.20f, 0.085f),
    surfaceContainer = Color.hsl(hue, 0.18f, 0.11f),
    surfaceContainerHigh = Color.hsl(hue, 0.17f, 0.14f),
    surfaceContainerHighest = Color.hsl(hue, 0.16f, 0.17f),
)

private fun lightScheme(hue: Float) = lightColorScheme(
    primary = Color.hsl(hue, 0.64f, 0.34f),
    onPrimary = Color.White,
    primaryContainer = Color.hsl(hue, 0.72f, 0.90f),
    onPrimaryContainer = Color.hsl(hue, 0.65f, 0.13f),
    inversePrimary = Color.hsl(hue, 0.55f, 0.76f),
    secondary = Color.hsl((hue + 28f) % 360f, 0.36f, 0.37f),
    onSecondary = Color.White,
    secondaryContainer = Color.hsl((hue + 28f) % 360f, 0.48f, 0.92f),
    onSecondaryContainer = Color.hsl((hue + 28f) % 360f, 0.40f, 0.16f),
    tertiary = Color.hsl((hue + 65f) % 360f, 0.46f, 0.39f),
    onTertiary = Color.White,
    tertiaryContainer = Color.hsl((hue + 65f) % 360f, 0.60f, 0.91f),
    onTertiaryContainer = Color.hsl((hue + 65f) % 360f, 0.50f, 0.14f),
    background = Color.hsl(hue, 0.35f, 0.985f),
    onBackground = Color.hsl(hue, 0.25f, 0.10f),
    surface = Color.hsl(hue, 0.30f, 0.99f),
    onSurface = Color.hsl(hue, 0.25f, 0.11f),
    surfaceVariant = Color.hsl(hue, 0.22f, 0.92f),
    onSurfaceVariant = Color.hsl(hue, 0.15f, 0.32f),
    surfaceTint = Color.hsl(hue, 0.64f, 0.34f),
    inverseSurface = Color.hsl(hue, 0.20f, 0.18f),
    inverseOnSurface = Color.hsl(hue, 0.15f, 0.96f),
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    outline = Color.hsl(hue, 0.12f, 0.51f),
    outlineVariant = Color.hsl(hue, 0.15f, 0.80f),
    scrim = Color.Black,
    surfaceBright = Color.hsl(hue, 0.30f, 0.99f),
    surfaceDim = Color.hsl(hue, 0.22f, 0.88f),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.hsl(hue, 0.32f, 0.972f),
    surfaceContainer = Color.hsl(hue, 0.30f, 0.952f),
    surfaceContainerHigh = Color.hsl(hue, 0.26f, 0.93f),
    surfaceContainerHighest = Color.hsl(hue, 0.24f, 0.90f),
)

private val AntiNetTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.4.sp,
    ),
)

private val AntiNetShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
)

@Composable
fun AntiNetTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val palette = AccentPalettes.firstOrNull { it.id == settings.accent } ?: AccentPalettes.first()
    val scheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkScheme(palette.hue)
        else -> lightScheme(palette.hue)
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = AntiNetTypography,
        shapes = AntiNetShapes,
        content = content,
    )
}
