package com.facecam.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// FaceCam palette - a refined, modern dark scheme: warm gold accent against a
// deep, cool near-black with softly raised neutral surfaces.
val Gold = Color(0xFFF0B45C)
val GoldDeep = Color(0xFFC98A2E)
val Cream = Color(0xFFF4F1EA)
val Ink = Color(0xFF0B0B0F)
val Surface = Color(0xFF141419)
val SurfaceHigh = Color(0xFF1E1E24)
val WarmGrey = Color(0xFF232329)
val LeakRed = Color(0xFFE0796A)

// Kept for source compatibility with older references.
val Amber = Gold
val AmberDark = GoldDeep
val Charcoal = Surface
val FilmBlack = Ink

private val DarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = Ink,
    primaryContainer = GoldDeep,
    onPrimaryContainer = Cream,
    secondary = LeakRed,
    onSecondary = Ink,
    background = Ink,
    onBackground = Cream,
    surface = Surface,
    onSurface = Cream,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = Color(0xFFC9C5BC),
    outline = WarmGrey,
    outlineVariant = Color(0xFF2C2C33)
)

private val LightColors = lightColorScheme(
    primary = GoldDeep,
    onPrimary = Cream,
    primaryContainer = Gold,
    onPrimaryContainer = Ink,
    secondary = LeakRed,
    onSecondary = Cream,
    background = Cream,
    onBackground = Ink,
    surface = Color(0xFFFBF9F4),
    onSurface = Ink,
    surfaceVariant = Color(0xFFEAE5DA),
    onSurfaceVariant = Color(0xFF4A4740),
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
