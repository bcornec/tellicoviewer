package org.hyper_linux.tellicoviewer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Palette de couleurs TellicoViewer
// Inspirée de l'UI Airtable : tons neutres, accents teal/indigo
// ---------------------------------------------------------------------------

private val PrimaryLight      = Color(0xFF1A6B5C)   // teal profond
private val OnPrimaryLight    = Color(0xFFFFFFFF)
private val SecondaryLight    = Color(0xFF4A5568)   // gris ardoise
private val SurfaceLight      = Color(0xFFF7F8FA)
private val BackgroundLight   = Color(0xFFFFFFFF)
private val OutlineLight      = Color(0xFFD1D5DB)

private val PrimaryDark       = Color(0xFF4ECDC4)   // teal clair
private val OnPrimaryDark     = Color(0xFF003731)
private val SecondaryDark     = Color(0xFF9CA3AF)
private val SurfaceDark       = Color(0xFF1F2937)
private val BackgroundDark    = Color(0xFF111827)
private val OutlineDark       = Color(0xFF374151)

private val LightColorScheme = lightColorScheme(
    primary          = PrimaryLight,
    onPrimary        = OnPrimaryLight,
    secondary        = SecondaryLight,
    surface          = SurfaceLight,
    background       = BackgroundLight,
    outline          = OutlineLight,
    surfaceVariant   = Color(0xFFEFF1F5),
    onSurfaceVariant = Color(0xFF64748B),
)

private val DarkColorScheme = darkColorScheme(
    primary          = PrimaryDark,
    onPrimary        = OnPrimaryDark,
    secondary        = SecondaryDark,
    surface          = SurfaceDark,
    background       = BackgroundDark,
    outline          = OutlineDark,
    surfaceVariant   = Color(0xFF2D3748),
    onSurfaceVariant = Color(0xFF9CA3AF),
)

@Composable
fun TellicoViewerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography  = TellicoTypography,
        content     = content
    )
}

// Couleurs sémantiques utilitaires accessibles globalement
object TellicoColors {
    val RowAlternate   @Composable get() = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val HeaderBg       @Composable get() = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    val FrozenColumn   @Composable get() = MaterialTheme.colorScheme.surface
    val SelectedRow    @Composable get() = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val ChipBg         @Composable get() = MaterialTheme.colorScheme.secondaryContainer
}
