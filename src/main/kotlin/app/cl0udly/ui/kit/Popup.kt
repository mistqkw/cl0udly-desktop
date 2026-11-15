package app.cl0udly.ui.kit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.cl0udly.ui.theme.Oc

/**
 * Menus like Claude desktop's: a flat card, opens in ~110 ms, flips up when there's no room below,
 * submenus open to the side. No Material DropdownMenu: it measures its content by intrinsics and
 * falls over on lists.
 */
enum class Side { Below, Above, Right, AtPointer }

private class Place(val side: Side, val gap: Int, val at: IntOffset?) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val w = popupContentSize.width; val h = popupContentSize.height
        fun fitX(x: Int) = x.coerceIn(8, (windowSize.width - w - 8).coerceAtLeast(8))
        fun fitY(y: Int) = y.coerceIn(8, (windowSize.height - h - 8).coerceAtLeast(8))
        return when (side) {
            Side.AtPointer -> {
                val p = at ?: IntOffset(anchorBounds.left, anchorBounds.bottom)
                val x = if (p.x + w > windowSize.width - 8) p.x - w else p.x
                val y = if (p.y + h > windowSize.height - 8) p.y - h else p.y
                IntOffset(fitX(x), fitY(y))
            }
            Side.Right -> {
                val x = if (anchorBounds.right + gap + w <= windowSize.width - 8) anchorBounds.right + gap else anchorBounds.left - gap - w
                IntOffset(fitX(x), fitY(anchorBounds.top - 6))
            }
            Side.Below, Side.Above -> {
                val below = anchorBounds.bottom + gap
                val above = anchorBounds.top - gap - h
                val down = if (side == Side.Below) below + h <= windowSize.height - 8 || above < 8 else above < 8
                IntOffset(fitX(anchorBounds.left), if (down) below else above)
            }
        }
    }
}

/** A popup card anchored to the composable it sits in (put it inside a Box with the trigger). */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Pop(
    open: Boolean,
    onDismiss: () -> Unit,
    side: Side = Side.Below,
    at: IntOffset? = null,
    width: Dp? = null,
    minWidth: Dp = 220.dp,
    focusable: Boolean = true,
    onKey: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)? = null,
    onBounds: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (!open) return
    androidx.compose.runtime.DisposableEffect(Unit) { Overlay.count++; onDispose { Overlay.count-- } }
    val c = Oc.c
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(110)) }
    val gap = with(androidx.compose.ui.platform.LocalDensity.current) { 6.dp.roundToPx() }
    Popup(
        popupPositionProvider = remember(side, at) { Place(side, gap, at) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = focusable),
        onKeyEvent = {
            if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { onDismiss(); true }
            else onKey?.invoke(it) ?: false
        },
    ) {
        Column(
            Modifier
                .graphicsLayer {
                    alpha = a.value; val s = 0.97f + 0.03f * a.value; scaleX = s; scaleY = s
                    transformOrigin = TransformOrigin(0f, if (side == Side.Above) 1f else 0f)
                }
                .shadow(14.dp, Oc.round(12.dp), ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.5f))
                .clip(Oc.round(12.dp)).background(c.surface).border(1.dp, c.border, Oc.round(12.dp))
                .then(if (width != null) Modifier.width(width) else Modifier.widthIn(min = minWidth, max = 420.dp))
                .heightIn(max = 560.dp)
                .then(if (onBounds != null) Modifier.onGloballyPositioned { onBounds(androidx.compose.ui.geometry.Rect(it.positionOnScreen(), it.size.toSize())) } else Modifier)
                .verticalScroll(rememberScrollState())
                .padding(5.dp),
        ) { content() }
    }
}

/**
 * One menu row: icon, label, an optional second line, a key hint or a check on the right.
 * [sub] makes it open a submenu to the side on hover or click.
 */
@Composable
fun PopItem(
    label: String,
    ic: Ic? = null,
    sub: String? = null,
    hint: String? = null,
    value: String? = null,
    checked: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    submenu: (@Composable (close: () -> Unit) -> Unit)? = null,
    onClick: () -> Unit = {},
) {
    val c = Oc.c
    val col = when { !enabled -> c.textDim.copy(alpha = 0.5f); danger -> c.danger; else -> c.text }
    var subOpen by remember { mutableStateOf(false) }
    // A submenu stays while the pointer is on its row or inside it and closes 300 ms after it leaves both.
    // Popups get no Exit once the pointer is outside them, so the real pointer position is checked
    // instead, in screen pixels (menus may be windows of their own).
    var rowRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    var subRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    LaunchedEffect(subOpen) {
        if (!subOpen) return@LaunchedEffect
        var seen = System.currentTimeMillis()
        while (true) {
            kotlinx.coroutines.delay(60)
            val m = runCatching { java.awt.MouseInfo.getPointerInfo().location }.getOrNull() ?: continue
            val p = Offset(m.x * density, m.y * density)
            val now = System.currentTimeMillis()
            if (rowRect.inflate(4f).contains(p) || subRect.inflate(4f).contains(p)) seen = now
            else if (now - seen > 300) { subOpen = false; break }
        }
    }
    Box {
        Row(
            Modifier.fillMaxWidth().heightIn(min = if (sub != null) 46.dp else 34.dp)
                .hoverTap(Oc.round(8.dp), subOpen, enabled) { if (submenu != null) subOpen = true else onClick() }
                .then(if (submenu != null) Modifier.onGloballyPositioned { rowRect = androidx.compose.ui.geometry.Rect(it.positionOnScreen(), it.size.toSize()) }.hoverOpens { subOpen = true } else Modifier)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (ic != null) { Icon(ic, 16.dp, if (danger) c.danger else c.textDim); Spacer(Modifier.width(11.dp)) }
            Column(Modifier.weight(1f, fill = false).widthIn(min = 120.dp)) {
                Txt(label, size = 14.sp, color = col, maxLines = 1)
                if (sub != null) Txt(sub, size = 12.sp, color = c.textDim, maxLines = 2, lineHeight = 16.sp)
            }
            Spacer(Modifier.weight(1f))
            if (value != null) { Spacer(Modifier.width(16.dp)); Txt(value, size = 13.sp, color = c.textDim, maxLines = 1) }
            if (hint != null) { Spacer(Modifier.width(16.dp)); Txt(hint, size = 12.sp, color = c.textDim, family = Oc.mono) }
            if (checked) { Spacer(Modifier.width(12.dp)); Icon(Ic.Check, 15.dp, c.link) }
            if (submenu != null) { Spacer(Modifier.width(10.dp)); Icon(Ic.ChevronRight, 14.dp, c.textDim) }
        }
        if (submenu != null) Pop(subOpen, { subOpen = false }, Side.Right, focusable = false, onBounds = { subRect = it }) { submenu { subOpen = false } }
    }
}

@Composable
fun PopDivider() = Box(Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 6.dp).height(1.dp).background(Oc.c.border.copy(alpha = 0.7f)))

@Composable
fun PopHeader(text: String) =
    Txt(text, size = 12.sp, color = Oc.c.textDim, weight = FontWeight.Medium, modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 4.dp))

/** Hovering a while over a submenu row opens it, like native menus. */
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.hoverOpens(open: () -> Unit): Modifier = composed {
    var inside by remember { mutableStateOf(false) }
    LaunchedEffect(inside) { if (inside) { kotlinx.coroutines.delay(200); if (inside) open() } }
    onPointerEvent(PointerEventType.Enter) { inside = true }
        .onPointerEvent(PointerEventType.Exit) { inside = false }
}

/** Right click inside opens [menu] at the pointer (the session list, messages, files). */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ContextArea(modifier: Modifier = Modifier, menu: @Composable (close: () -> Unit) -> Unit, content: @Composable () -> Unit) {
    var at by remember { mutableStateOf<IntOffset?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier.onGloballyPositioned { origin = it.positionInWindow() }
            .onPointerEvent(PointerEventType.Press) { e ->
                if (e.button == PointerButton.Secondary) {
                    val p = e.changes.first().position
                    at = IntOffset((origin.x + p.x).toInt(), (origin.y + p.y).toInt())
                }
            },
    ) {
        content()
        Pop(at != null, { at = null }, Side.AtPointer, at) { menu { at = null } }
    }
}

/** How many menus are open: Swing views (terminal, browser) draw over them, so they step aside meanwhile. */
object Overlay { var count by mutableStateOf(0) }
