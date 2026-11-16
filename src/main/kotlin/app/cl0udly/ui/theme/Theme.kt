package app.cl0udly.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cl0udly.Graph

@Immutable
class OcColors(
    val isDark: Boolean,
    val bg: Color,
    /** The sidebar: a shade off the page, with a 1px seam between them (Claude desktop). */
    val sidebar: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val text: Color,
    val textDim: Color,
    val border: Color,
    val accent: Color,
    val link: Color,
    val danger: Color,
    val ok: Color,
    val warn: Color,
    val userBubble: Color,
    val codeBg: Color,
    val scrim: Color,
)

/**
 * A look: palettes, fonts and how messages are drawn. The window frame is the same for every look:
 * it is measured off Claude desktop (see docs/measurements.md); looks only repaint and reshape it.
 */
class Look(
    val id: String,
    val title: String,
    val sub: String,
    val dark: OcColors,
    val light: OcColors,
    /** Corner radius multiplier and cap. */
    val radius: Float = 1f,
    val radiusMax: Float? = null,
    /** User message: bubble | line (1px border) | plain (label above). */
    val user: String = "bubble",
    /** Pixel clouds behind the home screen and the cloud mascot. */
    val clouds: Boolean = false,
)

private fun pal(
    dark: Boolean, bg: Long, side: Long, surface: Long, high: Long, text: Long, dim: Long, border: Long, accent: Long, link: Long,
    user: Long, code: Long,
) = OcColors(
    isDark = dark, bg = Color(bg), sidebar = Color(side), surface = Color(surface), surfaceHigh = Color(high), text = Color(text),
    textDim = Color(dim), border = Color(border), accent = Color(accent), link = Color(link),
    danger = Color(if (dark) 0xFFE5645C else 0xFFC8433B), ok = Color(if (dark) 0xFF5CB885 else 0xFF2E8B57),
    warn = Color(if (dark) 0xFFE0A030 else 0xFFB87A10), userBubble = Color(user), codeBg = Color(code),
    scrim = Color(if (dark) 0x99000000 else 0x66000000),
)

object Looks {
    // cl0udly (as on the phone): the sky of a photo of dark clouds at night (cold blue-black) under
    // neutral grey text, frames and icons; the clouds' warm stone color is kept for the clouds alone.
    val cloud = Look(
        "cloud", "cl0udly", "Night sky, pixel clouds, a little cloud mascot",
        pal(true, 0xFF04070C, 0xFF080B10, 0xFF111317, 0xFF1B1C1F, 0xFFE6E8EB, 0xFF8B9199, 0xFF24272C, 0xFFC9CFD8, 0xFFC9CFD8, 0xFF1B1C20, 0xFF080B10),
        pal(false, 0xFFF7F8FA, 0xFFEEF0F3, 0xFFEEF0F3, 0xFFE4E7EB, 0xFF15171A, 0xFF5F656D, 0xFFDDE1E6, 0xFF3E4652, 0xFF3E4652, 0xFFE9ECF0, 0xFFEEF0F3),
        clouds = true,
    )

    // Warm (was "Claude" on the phone): warm near-black, coral accent, serif replies
    val warm = Look(
        "claude", "Warm", "Soft and warm, with serif replies",
        pal(true, 0xFF262624, 0xFF1F1E1D, 0xFF30302E, 0xFF3A3936, 0xFFF5F4EF, 0xFF9B9A94, 0xFF3C3B38, 0xFFD97757, 0xFF5EA0F2, 0xFF141413, 0xFF1F1E1D),
        pal(false, 0xFFFAF9F5, 0xFFF0EEE6, 0xFFF0EEE6, 0xFFE6E3D8, 0xFF1F1E1D, 0xFF73726C, 0xFFD6D3C7, 0xFFC6613F, 0xFF1F6FD6, 0xFFF0EEE6, 0xFFEFEDE4),
    )

    // Graphite: the neutral grey measured off Claude desktop on this computer (#19191b page, #1f1f20 sidebar)
    val graphite = Look(
        "graphite", "Graphite", "Neutral grey, like the desktop app on this PC",
        pal(true, 0xFF19191B, 0xFF1F1F20, 0xFF2E2F30, 0xFF3D3D3E, 0xFFE8E8EA, 0xFF8E8F92, 0xFF3D3E3F, 0xFFD97757, 0xFF6AA4F0, 0xFF242526, 0xFF151516),
        pal(false, 0xFFFFFFFF, 0xFFF6F6F7, 0xFFF3F3F4, 0xFFE8E8EA, 0xFF1A1A1C, 0xFF6E6F73, 0xFFE0E0E3, 0xFFC6613F, 0xFF2F6FD6, 0xFFF0F0F1, 0xFFF5F5F6),
    )

    // TexFi v4: black, 1px lines, Unbounded in headings, a single #4a7dfb accent
    val hairline = Look(
        "hairline", "Hairline", "Black, 1px lines, wide headings, one blue accent",
        pal(true, 0xFF000000, 0xFF050505, 0xFF0A0A0A, 0xFF151515, 0xFFF2F2F2, 0xFF8C8C8C, 0xFF262626, 0xFF4A7DFB, 0xFF4A7DFB, 0xFF000000, 0xFF0A0A0A),
        pal(false, 0xFFFFFFFF, 0xFFFAFAFA, 0xFFF6F6F6, 0xFFECECEC, 0xFF0A0A0A, 0xFF7A7A7A, 0xFFE2E2E2, 0xFF4A7DFB, 0xFF4A7DFB, 0xFFFFFFFF, 0xFFF6F6F6),
        radius = 0.35f, radiusMax = 10f, user = "line",
    )

    // Notebook: paper, serifs, no bubbles
    val notebook = Look(
        "notebook", "Notebook", "Paper, serif text, no bubbles — reads like a journal",
        pal(true, 0xFF1C1A16, 0xFF181612, 0xFF25221D, 0xFF2F2B25, 0xFFEDE6D6, 0xFF9C927F, 0xFF3A352C, 0xFFD0794A, 0xFF7FA7D9, 0xFF25221D, 0xFF211E19),
        pal(false, 0xFFF7F3EA, 0xFFEFE9DC, 0xFFEFE9DC, 0xFFE5DDCC, 0xFF2B2721, 0xFF857C6C, 0xFFD9D0BD, 0xFFB5562F, 0xFF2F6FB0, 0xFFEFE9DC, 0xFFEFE9DC),
        radius = 0.3f, radiusMax = 8f, user = "plain",
    )

    // OpenCode desktop: tokens from the app's stylesheets (dark #151515, text #ededed, accent #9dbefe, Inter)
    val desktop = Look(
        "desktop", "OpenCode · Desktop", "Flat panels and Inter, like the desktop app",
        pal(true, 0xFF151515, 0xFF101010, 0xFF1C1C1C, 0xFF262626, 0xFFEDEDED, 0xFF9B9B9B, 0xFF2C2C2C, 0xFF9DBEFE, 0xFF9DBEFE, 0xFF222222, 0xFF101010),
        pal(false, 0xFFFCFCFC, 0xFFF4F4F4, 0xFFF4F4F4, 0xFFEAEAEA, 0xFF171717, 0xFF6F6F6F, 0xFFDADADA, 0xFF3D6FE8, 0xFF3D6FE8, 0xFFF0F0F0, 0xFFF4F4F4),
        radius = 0.5f, radiusMax = 12f,
    )

    // OpenCode in the terminal: near-black, peach accent, monospace, square corners
    val terminal = Look(
        "terminal", "OpenCode · Terminal", "Monospace, square corners, like the TUI",
        pal(true, 0xFF0A0A0A, 0xFF0A0A0A, 0xFF141414, 0xFF1E1E1E, 0xFFEEEEEE, 0xFF808080, 0xFF3C3C3C, 0xFFFAB283, 0xFF5C9CF5, 0xFF141414, 0xFF0F0F0F),
        pal(false, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFF3F3F3, 0xFFE8E8E8, 0xFF1A1A1A, 0xFF8A8A8A, 0xFFC8C8C8, 0xFFD68C27, 0xFF3B7DD8, 0xFFF3F3F3, 0xFFF3F3F3),
        radius = 0f, radiusMax = 0f, user = "line",
    )

    val all = listOf(cloud, warm, graphite, hairline, notebook, desktop, terminal)
    fun of(id: String): Look = all.firstOrNull { it.id == id } ?: cloud
}

private fun res(name: String, w: FontWeight) = Font(resource = "font/$name.ttf", weight = w)

val InterFamily = FontFamily(res("inter_regular", FontWeight.Normal), res("inter_medium", FontWeight.Medium), res("inter_semibold", FontWeight.SemiBold), res("inter_semibold", FontWeight.Bold))
val SerifFamily = FontFamily(res("source_serif_regular", FontWeight.Normal), res("source_serif_semibold", FontWeight.Medium), res("source_serif_semibold", FontWeight.SemiBold))
val SerifDisplay = FontFamily(res("source_serif_display", FontWeight.Normal), res("source_serif_semibold", FontWeight.SemiBold))
val UnboundedFamily = FontFamily(res("unbounded", FontWeight.Normal), res("unbounded", FontWeight.SemiBold), res("unbounded", FontWeight.Bold))
val JetBrainsMono = FontFamily(res("jetbrains_mono", FontWeight.Normal), res("jetbrains_mono", FontWeight.Medium), res("jetbrains_mono", FontWeight.Bold))

val LocalOc = staticCompositionLocalOf { Looks.cloud.dark }

object Oc {
    val c: OcColors @Composable @ReadOnlyComposable get() = LocalOc.current
    val look: Look @Composable @ReadOnlyComposable get() = Looks.of(Graph.prefs.edition)
    val edition: String @Composable @ReadOnlyComposable get() = Graph.prefs.edition

    val heading: FontFamily
        @Composable @ReadOnlyComposable get() = when (edition) {
            "terminal" -> JetBrainsMono
            "hairline" -> UnboundedFamily
            "claude", "notebook" -> SerifDisplay
            else -> InterFamily
        }
    val body: FontFamily
        @Composable @ReadOnlyComposable get() = when (edition) {
            "terminal" -> JetBrainsMono
            "notebook" -> SerifFamily
            else -> InterFamily
        }
    /** Replies: serif in Warm and Notebook, like Claude. */
    val reply: FontFamily
        @Composable @ReadOnlyComposable get() = when (edition) {
            "claude", "notebook" -> SerifFamily
            else -> body
        }
    val label: FontFamily @Composable @ReadOnlyComposable get() = if (edition == "hairline" || edition == "terminal") JetBrainsMono else body
    val mono: FontFamily @Composable @ReadOnlyComposable get() = JetBrainsMono

    @Composable @ReadOnlyComposable
    fun round(r: Dp): Shape = look.let { l ->
        val x = r * l.radius
        RoundedCornerShape(l.radiusMax?.let { x.coerceAtMost(it.dp) } ?: x)
    }

    val pill: Shape @Composable @ReadOnlyComposable get() = when (edition) {
        "terminal" -> RoundedCornerShape(0.dp)
        "hairline", "notebook" -> RoundedCornerShape(8.dp)
        "desktop" -> RoundedCornerShape(10.dp)
        else -> CircleShape
    }
}

@Composable
fun OcTheme(content: @Composable () -> Unit) {
    val dark = when (Graph.prefs.colorMode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val look = Looks.of(Graph.prefs.edition)
    val colors = if (dark) look.dark else look.light
    val scale = when (Graph.prefs.textSize) { "small" -> 0.92f; "large" -> 1.1f; else -> 1f }
    val d = LocalDensity.current
    val scheme = if (dark) darkColorScheme(background = colors.bg, surface = colors.surface, onSurface = colors.text, primary = colors.link)
    else lightColorScheme(background = colors.bg, surface = colors.surface, onSurface = colors.text, primary = colors.link)
    CompositionLocalProvider(LocalOc provides colors, LocalDensity provides Density(d.density, d.fontScale * scale)) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
