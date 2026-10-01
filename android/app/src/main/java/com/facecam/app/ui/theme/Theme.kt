package com.facecam.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// FaceCam palette - a current-generation social-camera look: a deep cool
// near-black canvas, vivid gradient accents and softly raised glass surfaces.
// ---------------------------------------------------------------------------

// Gradient accent stops (violet -> magenta -> coral -> amber).
val Violet = Color(0xFF7C5CFF)
val Magenta = Color(0xFFE24BC0)
val Coral = Color(0xFFFF6B6B)
val Gold = Color(0xFFF0B45C)
val GoldDeep = Color(0xFFC98A2E)

// Neutrals.
val Cream = Color(0xFFF6F3EE)
val Ink = Color(0xFF0A0A0F)
val Surface = Color(0xFF14141C)
val SurfaceHigh = Color(0xFF1D1D27)
val WarmGrey = Color(0xFF26262F)
val LeakRed = Color(0xFFE0796A)

// Kept for source compatibility with older references.
val Amber = Gold
val AmberDark = GoldDeep
val Charcoal = Surface
val FilmBlack = Ink

/** The signature accent gradient used for buttons, chips and highlights. */
val FaceCamGradient = Brush.linearGradient(listOf(Violet, Magenta, Coral))

/** A softer gradient for large surfaces / headers. */
val FaceCamGradientSoft = Brush.linearGradient(
    listOf(Violet.copy(alpha = 0.85f), Magenta.copy(alpha = 0.75f), Gold.copy(alpha = 0.85f))
)

/** Vertical scrim for the top and bottom of the viewfinder. */
val ViewfinderScrim = Brush.verticalGradient(
    colors = listOf(Color(0xCC000000), Color(0x33000000), Color(0x00000000))
)

private val DarkColors = darkColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = Magenta,
    onPrimaryContainer = Color.White,
    secondary = Coral,
    onSecondary = Color.White,
    tertiary = Gold,
    onTertiary = Ink,
    background = Ink,
    onBackground = Cream,
    surface = Surface,
    onSurface = Cream,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = Color(0xFFC9C5D2),
    outline = WarmGrey,
    outlineVariant = Color(0xFF2C2C36)
)

private val LightColors = lightColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = Magenta,
    onPrimaryContainer = Color.White,
    secondary = Coral,
    onSecondary = Color.White,
    tertiary = GoldDeep,
    onTertiary = Color.White,
    background = Cream,
    onBackground = Ink,
    surface = Color(0xFFFCFAF7),
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDE8F2),
    onSurfaceVariant = Color(0xFF4A4753),
    outline = Color(0xFFCFC8DA)
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
