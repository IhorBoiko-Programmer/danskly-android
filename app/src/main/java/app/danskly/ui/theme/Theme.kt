package app.danskly.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Danskly's colours. Surfaces are translucent to give the frosted-glass look. */
@Immutable
data class Palette(
    val background: Color,
    val glass: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    val ok: Color,
    val bad: Color,
    val blobWarm: Color,
    val blobCool: Color,
)

val LightPalette = Palette(
    background = Color(0xFFF2F2F7),
    glass = Color(0xA8FFFFFF),
    ink = Color(0xFF1C1C1E),
    muted = Color(0xFF6C6C70),
    line = Color(0x293C3C43),
    accent = Color(0xFFC8102E),
    ok = Color(0xFF248A3D),
    bad = Color(0xFFD70015),
    blobWarm = Color(0xFFFFD9DE),
    blobCool = Color(0xFFD2E5FF),
)

val DarkPalette = Palette(
    background = Color(0xFF000000),
    glass = Color(0x9E2C2C2E),
    ink = Color(0xFFF5F5F7),
    muted = Color(0xFF98989F),
    line = Color(0x29EBEBF5),
    accent = Color(0xFFFF6B81),
    ok = Color(0xFF30D158),
    bad = Color(0xFFFF6961),
    blobWarm = Color(0xFF4A1420),
    blobCool = Color(0xFF10284A),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

/** Colours of the home-screen tiles: (main, light). */
object TileColors {
    val today = Color(0xFFFF9500) to Color(0xFFFFD60A)
    val words = Color(0xFF0A84FF) to Color(0xFF64D2FF)
    val exercises = Color(0xFF5E5CE6) to Color(0xFF7DD3FC)
    val reading = Color(0xFF00A8A0) to Color(0xFF7EE8DF)
    val grammar = Color(0xFFFF9500) to Color(0xFFFFD60A)
    val pronunciation = Color(0xFFFF2D55) to Color(0xFFFF8FA3)
    val writing = Color(0xFF28CD41) to Color(0xFFA3F0A0)
    val quiz = Color(0xFFFF3B30) to Color(0xFFFF9F0A)
    val tutor = Color(0xFFAF52DE) to Color(0xFF5E5CE6)

    /** Rotating colours for list icons. */
    val list = listOf(
        Color(0xFF0A84FF), Color(0xFFFF9F0A), Color(0xFF30D158), Color(0xFFBF5AF2),
        Color(0xFFFF453A), Color(0xFF64D2FF), Color(0xFFFF375F), Color(0xFF5E5CE6),
    )
}

@Composable
fun DansklyTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(primary = palette.accent, background = palette.background, surface = palette.background, onSurface = palette.ink, onBackground = palette.ink)
    } else {
        lightColorScheme(primary = palette.accent, background = palette.background, surface = palette.background, onSurface = palette.ink, onBackground = palette.ink)
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalPalette provides palette, content = content)
    }
}
