package app.cl0udly.ui.kit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cl0udly.ui.theme.Oc

/** What the agent is doing: the indicator's look and label depend on it (same list as on the phone). */
enum class Work(val label: String) {
    Think("Thinking"), Read("Reading files"), Write("Writing"), Edit("Editing code"),
    Run("Running a command"), Search("Searching"), Web("Browsing the web"), Plan("Planning"), Wait("Waiting for the provider"), Other("Working"),
}

@Composable
fun Txt(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 15.sp,
    color: Color = Oc.c.text,
    weight: FontWeight? = null,
    family: FontFamily? = null,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    italic: Boolean = false,
    /** User or agent text: shown as it is, never translated. */
    raw: Boolean = false,
) {
    Text(
        if (raw) text else app.cl0udly.data.I18n.t(text), modifier = modifier, color = color, fontSize = size, fontWeight = weight, fontFamily = family ?: Oc.body,
        fontStyle = if (italic) androidx.compose.ui.text.font.FontStyle.Italic else null,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = align, lineHeight = lineHeight,
    )
}

/** A click with a short press (scale and fade, ~100 ms) and a hand cursor. */
fun Modifier.tap(enabled: Boolean = true, scale: Float = 0.97f, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed && enabled) scale else 1f, tween(90), label = "press")
    this.graphicsLayer { scaleX = s; scaleY = s }
        .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
        .clickable(source, null, enabled = enabled, onClick = onClick)
}

/** A row or button with Claude's hover: a flat tint, no ripple. */
fun Modifier.hoverTap(shape: Shape, selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val c = Oc.c
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val a by animateFloatAsState(if (selected) 1f else if (hovered && enabled) 0.6f else 0f, tween(110), label = "hover")
    // a press sinks a touch and springs back, like on the phone
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (pressed && enabled) 0.97f else 1f,
        if (pressed) androidx.compose.animation.core.spring(1f, 1500f) else androidx.compose.animation.core.spring(0.45f, 900f),
        label = "press",
    )
    this.graphicsLayer { scaleX = s; scaleY = s }
        .clip(shape)
        .background(c.surfaceHigh.copy(alpha = a), shape)
        .hoverable(source)
        .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
        .clickable(source, null, enabled = enabled, onClick = onClick)
}

/** Line icons on a 24 grid, drawn with a 1.6 stroke like the ones in Claude desktop. */
enum class Ic(val d: String, val fill: Boolean = false) {
    Plus("M12 5v14M5 12h14"),
    ArrowDown("M12 5v14M6 13l6 6 6-6"),
    Search("M10.5 4a6.5 6.5 0 1 1 0 13 6.5 6.5 0 0 1 0-13zM15.5 15.5 20 20"),
    Sidebar("M4 5h16v14H4zM9.5 5v14"),
    Menu("M4 7h16M4 12h16M4 17h10"),
    Back("M14.5 6 8.5 12l6 6"),
    Forward("M9.5 6l6 6-6 6"),
    Chat("M5 6.5A2.5 2.5 0 0 1 7.5 4h9A2.5 2.5 0 0 1 19 6.5v6a2.5 2.5 0 0 1-2.5 2.5H11l-4 4v-4h0.5A2.5 2.5 0 0 1 5 12.5z"),
    Code("M9 8l-4 4 4 4M15 8l4 4-4 4M13.5 5l-3 14"),
    Folder("M3.5 7.5A1.5 1.5 0 0 1 5 6h4l2 2h8a1.5 1.5 0 0 1 1.5 1.5v8A1.5 1.5 0 0 1 19 19H5a1.5 1.5 0 0 1-1.5-1.5z"),
    Box("M4 8l8-4 8 4v8l-8 4-8-4zM4 8l8 4 8-4M12 12v8"),
    Sliders("M5 7h9M18 7h1M5 17h3M12 17h7M14 5v4M8 15v4"),
    More("M6 12h0.01M12 12h0.01M18 12h0.01"),
    Settings("M12 9a3 3 0 1 1 0 6 3 3 0 0 1 0-6zM12 3v2.5M12 18.5V21M3 12h2.5M18.5 12H21M5.6 5.6l1.8 1.8M16.6 16.6l1.8 1.8M5.6 18.4l1.8-1.8M16.6 7.4l1.8-1.8"),
    ArrowUp("M12 19V5M6 11l6-6 6 6"),
    Stop("M8 8h8v8H8z", fill = true),
    Close("M6 6l12 12M18 6 6 18"),
    Chevron("M8 10l4 4 4-4"),
    ChevronRight("M10 8l4 4-4 4"),
    Copy("M8 8h11v11H8zM5 15V5h10"),
    Check("M5 12.5l4.5 4.5L19 7.5"),
    Retry("M19 12a7 7 0 1 1-2.05-4.95M19 4.5v3.5h-3.5"),
    Trash("M5 7h14M10 7V5h4v2M7 7l1 12h8l1-12"),
    Edit("M5 19h4l10-10-4-4L5 15zM13 7l4 4"),
    Terminal("M5 7l5 5-5 5M12 17h7"),
    Globe("M12 3.5a8.5 8.5 0 1 1 0 17 8.5 8.5 0 0 1 0-17zM3.5 12h17M12 3.5c2.5 2.3 3.5 5.1 3.5 8.5s-1 6.2-3.5 8.5c-2.5-2.3-3.5-5.1-3.5-8.5s1-6.2 3.5-8.5z"),
    File("M7 3.5h7l4 4V20.5H7zM14 3.5v4h4"),
    Bulb("M9 17h6M10 20h4M12 3.5a5.5 5.5 0 0 0-3 10.1V15h6v-1.4a5.5 5.5 0 0 0-3-10.1z"),
    Sun("M12 8a4 4 0 1 1 0 8 4 4 0 0 1 0-8zM12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2"),
    Server("M4 5h16v6H4zM4 13h16v6H4zM7.5 8h0.01M7.5 16h0.01"),
    Key("M8 11a4 4 0 1 1 0 0.01M11 12h9M17 12v3M20 12v2"),
    Info("M12 3.5a8.5 8.5 0 1 1 0 17 8.5 8.5 0 0 1 0-17zM12 11v5M12 8h0.01"),
    User("M12 4a4 4 0 1 1 0 8 4 4 0 0 1 0-8zM4.5 20a7.5 7.5 0 0 1 15 0"),
    Palette("M12 3.5a8.5 8.5 0 0 0 0 17c1.2 0 1.6-.9 1.2-1.8-.5-1-.1-2.2 1.3-2.2H17a3.5 3.5 0 0 0 3.5-3.5c0-5.2-3.8-9.5-8.5-9.5zM8 10h0.01M12 7.5h0.01M16 10h0.01"),
    Home("M4 11l8-6.5 8 6.5M6 9.5V19.5h12V9.5M10 19.5v-5h4v5"),
    Download("M12 4v11M7.5 10.5 12 15l4.5-4.5M5 19.5h14"),
    Image("M4 5h16v14H4zM4 16l4.5-4.5 4 4 2.5-2.5 5 5M15.5 8.5h0.01"),
    Diff("M12 4.5v8M8 8.5h8M8 18.5h8"),
    Gauge("M4.5 17a8.5 8.5 0 1 1 15 0M12 13l4-4.5"),
    Pin("M9 4h6l-1 5 3 3H7l3-3zM12 12v8"),
    Archive("M4 5h16v4H4zM5.5 9v10h13V9M10 13h4"),
    Fork("M7 4v5a3 3 0 0 0 3 3h4a3 3 0 0 1 3 3v5M17 4v4M7 20v-4"),
    Play("M8 5.5v13l10.5-6.5z"),
    Pause("M8.5 5.5v13M15.5 5.5v13"),
    Expand("M14 4h6v6M10 20H4v-6M20 4l-7 7M4 20l7-7"),
    Share("M12 15V4M7.5 8.5 12 4l4.5 4.5M5 13v6.5h14V13"),
    Layers("M12 4l8.5 4.5L12 13 3.5 8.5zM3.5 13 12 17.5l8.5-4.5"),
    Lock("M6 11h12v9H6zM8.5 11V8a3.5 3.5 0 0 1 7 0v3"),
    Bell("M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15zM10 20.5h4"),
    Return("M19 6v5a3 3 0 0 1-3 3H5.5M9.5 10 5.5 14l4 4"),
    Mic("M12 4a2.5 2.5 0 0 1 2.5 2.5v5a2.5 2.5 0 0 1-5 0v-5A2.5 2.5 0 0 1 12 4zM6.5 11.5a5.5 5.5 0 0 0 11 0M12 17v3"),
    Slash("M15 4 9 20"),
    Phone("M8 3.5h8v17H8zM11 17.5h2"),
    At("M12 8.5a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7zM15.5 12v1.5a2.5 2.5 0 0 0 5 0V12a8.5 8.5 0 1 0-3.3 6.7"),
    Minus("M6 12h12"),
    Square("M6.5 6.5h11v11h-11z"),
    Cloud("M7 18h10a4 4 0 0 0 .6-7.95A5.5 5.5 0 0 0 7 9.5 4.25 4.25 0 0 0 7 18z"),
}

private val parsed = HashMap<Ic, androidx.compose.ui.graphics.Path>()

@Composable
fun Icon(ic: Ic, size: Dp = 18.dp, color: Color = Oc.c.text, modifier: Modifier = Modifier, stroke: Float = 1.6f) {
    Canvas(modifier.size(size)) {
        val path = parsed.getOrPut(ic) { PathParser().parsePathString(ic.d).toPath() }
        scale(this.size.width / 24f, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            if (ic.fill) drawPath(path, color)
            else drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** A square icon button (Claude desktop: 32 px, 8 px radius, flat hover). */
@Composable
fun IconButton(ic: Ic, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 32.dp, icon: Dp = 18.dp, tint: Color = Oc.c.textDim, selected: Boolean = false) {
    Box(modifier.size(size).hoverTap(Oc.round(8.dp), selected, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(ic, icon, tint)
    }
}
