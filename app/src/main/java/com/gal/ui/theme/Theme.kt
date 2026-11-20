package com.gal.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

val GalShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(12.dp),
    medium     = RoundedCornerShape(16.dp),
    large      = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val brown = Color(0xFF191411)
private val gold = Color(0xFFE8BB88)
private val ivory = Color(0xFFFAEFE3)
val galleryGradient = Brush.linearGradient(listOf(Color(0xFF171210), Color(0xFF34271D), Color(0xFF100E0D), brown))
private val palette = darkColorScheme(
    primary = gold, secondary = Color(0xFFD9C1A7), tertiary = Color(0xFFDBAE78),
    onPrimary = brown, onSecondary = brown, onTertiary = brown,
    primaryContainer = Color(0xFF5B412C), secondaryContainer = Color(0xFF443629), tertiaryContainer = Color(0xFF503B29),
    onPrimaryContainer = ivory, onSecondaryContainer = ivory, onTertiaryContainer = ivory,
    background = brown, surface = brown, onSurface = ivory, onBackground = ivory,
    surfaceVariant = Color(0xFF40352E), onSurfaceVariant = Color(0xFFD8C8B9),
    surfaceContainerLowest = Color(0xFF110E0C), surfaceContainerLow = Color(0xFF231C18),
    surfaceContainer = Color(0xFF2A221C), surfaceContainerHigh = Color(0xFF352B24),
    surfaceContainerHighest = Color(0xFF42362D), outline = Color(0xFF9D8978), surfaceTint = gold
)

@Composable
fun GalTheme(appFont: String = "nunito", content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }
    MaterialTheme(colorScheme = palette, shapes = GalShapes,
        typography = remember(appFont) { buildGalTypography(appFontFromKey(appFont).family) }, content = content)
}
