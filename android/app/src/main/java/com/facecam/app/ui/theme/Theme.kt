package com.facecam.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// FaceCam palette - warm analogue browns and amber against near-black.
val Amber = Color(0xFFE8A33D)
val AmberDark = Color(0xFFB4771F)
val Cream = Color(0xFFF5F2EA)
val Charcoal = Color(0xFF141414)
val FilmBlack = Color(0xFF0A0A0A)
val WarmGrey = Color(0xFF2A2724)
val LeakRed = Color(0xFFD96A5A)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = FilmBlack,
    primaryContainer = AmberDark,
    onPrimaryContainer = Cream,
    secondary = LeakRed,
    onSecondary = Cream,
    background = FilmBlack,
    onBackground = Cream,
    surface = Charcoal,
    onSurface = Cream,
    surfaceVariant = WarmGrey,
    onSurfaceVariant = Cream,
    outline = WarmGrey
)

private val LightColors = lightColorScheme(
    primary = AmberDark,
    onPrimary = Cream,
    primaryContainer = Amber,
    onPrimaryContainer = FilmBlack,
    secondary = LeakRed,
    onSecondary = Cream,
    background = Cream,
    onBackground = Charcoal,
    surface = Cream,
    onSurface = Charcoal,
    surfaceVariant = Color(0xFFE7E2D6),
    onSurfaceVariant = Charcoal,
    outline = Color(0xFFCFC8B8)
)

/**
 * FaceCam theme. The app is intentionally dark by default (a camera viewfinder
 * looks best against black), but a light scheme is provided for accessibility.
 */
@Composable
fun FaceCamTheme(
    darkTheme: Boolean = isSystemInDarkTheme().not(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = FaceCamTypography,
        content = content
    )
}
