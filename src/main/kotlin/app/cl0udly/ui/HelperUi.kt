package app.cl0udly.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import app.cl0udly.ui.cloud.CloudMood
import app.cl0udly.ui.cloud.INK
import app.cl0udly.ui.cloud.drawIconCloud
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import app.cl0udly.Graph
import app.cl0udly.api.Part
import app.cl0udly.api.PermissionReq
import app.cl0udly.api.obj
import app.cl0udly.api.str
import app.cl0udly.data.Helpers
import app.cl0udly.ui.cloud.CH
import app.cl0udly.ui.cloud.CW
import app.cl0udly.ui.kit.Ic
import app.cl0udly.ui.kit.Icon
import app.cl0udly.ui.kit.Txt
import app.cl0udly.ui.kit.hoverTap
import app.cl0udly.ui.theme.Oc
import kotlinx.coroutines.launch

/** One helper the agent called: what it was asked, which model, and how it is going. */
data class HelperRun(
    val callId: String,
    val agent: String,
    val description: String,
    val prompt: String,
    /** asking (waits for the user), working, done, failed */
    val status: String,
    val childId: String?,
    val permission: PermissionReq?,
) {
    val label get() = Helpers.label(agent)
}

/** The helpers of a session, from its task calls, newest last; with the OK each one waits for. */
fun helperRuns(sessionId: String): List<HelperRun> {
    val hub = Graph.hub
    val chat = hub.chat(sessionId)
    val parts = chat.messages.flatMap { m -> m.parts.filter { it.type == "tool" && it.raw.str("tool") == "task" } }
    val children = hub.children(sessionId)
    return parts.map { p ->
        val state = p.raw.obj("state")
        val input = state?.obj("input")
        val call = p.raw.str("callID") ?: p.id
        val child = state?.obj("metadata")?.str("sessionId")?.takeIf { id -> children.isEmpty() || children.any { it.id == id } || hub.session(id) == null }
        val own = hub.permissions.firstOrNull { it.sessionID == sessionId && it.permission == "task" && it.callID == call }
        val inside = child?.let { cc -> hub.permissions.firstOrNull { it.sessionID == cc } }
        HelperRun(
            callId = call,
            agent = input?.str("subagent_type") ?: "",
            description = input?.str("description") ?: "A task",
            prompt = input?.str("prompt") ?: "",
            status = when {
                own != null || inside != null -> "asking"
                state?.str("status") == "completed" -> "done"
                state?.str("status") == "error" -> "failed"
                else -> "working"
            },
            childId = child,
            permission = own ?: inside,
        )
    }
}

/** The helper card that is open, and where its cloud was (the card grows out of it). */
object HelperView {
    var sessionId by mutableStateOf<String?>(null)
    var callId by mutableStateOf<String?>(null)
    var from by mutableStateOf(Rect.Zero)
    fun open(session: String, call: String, at: Rect) { sessionId = session; callId = call; from = at }
}

/**
 * The little clouds above the field, one per helper. A working one bobs and blinks, one that waits
 * for an OK pulses in the accent, a finished one wears a check. A click opens its card.
 */
@Composable
fun HelperClouds(sessionId: String) {
    val runs = helperRuns(sessionId)
    // your sub-agents wait here too: each one that's on and not busy right now sits as a still cloud
    @Suppress("UNUSED_VARIABLE") val added = app.cl0udly.data.Helpers.justAdded
    val busy = runs.filter { it.status == "working" || it.status == "asking" }.map { it.agent }.toSet()
    val team = app.cl0udly.data.Helpers.chosen().filter { app.cl0udly.data.Helpers.nameOf(it) !in busy }
    if (runs.isEmpty() && team.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp, start = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        runs.takeLast(8).forEach { r -> key(r.callId) { HelperCloud(sessionId, r, variantOf(runs, r)) } }
        team.forEach { p -> key("team-" + p.key) { TeamCloud(p) } }
    }
}

/** A sub-agent at rest. A new one flies in from above and lands with a little bounce; hover says who it is. */
@Composable
private fun TeamCloud(p: app.cl0udly.data.Pick) {
    val c = Oc.c
    val h = app.cl0udly.data.Helpers
    val fresh = h.justAdded == p.key
    val drop = remember { Animatable(if (fresh) 1f else 0f) }
    LaunchedEffect(fresh) {
        if (fresh) { drop.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 260f)); h.justAdded = null }
    }
    val (name, role) = h.meta(p.key)
    Column(
        Modifier.graphicsLayer { translationY = -drop.value * 260.dp.toPx(); alpha = 1f - drop.value * 0.6f; val s = 1f + drop.value * 0.5f; scaleX = s; scaleY = s }
            .hoverTap(Oc.round(10.dp)) { Graph.hub.toasts.tryEmit(name.ifBlank { p.label } + (if (role.isNotBlank()) ": $role" else "") + " — " + p.label) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HelperGlyph("idle", 34.dp, h.nameOf(p))
        Spacer(Modifier.height(4.dp))
        Txt(short(name.ifBlank { p.label }), size = 11.sp, color = c.textDim, maxLines = 1, raw = true)
    }
}

@Composable
private fun HelperCloud(sessionId: String, r: HelperRun, variant: Int) {
    val c = Oc.c
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(160, easing = FastOutSlowInEasing)) }
    val open = HelperView.callId == r.callId
    Column(
        Modifier.graphicsLayer { alpha = appear.value * (if (open) 0f else 1f); val s = 0.6f + 0.4f * appear.value; scaleX = s; scaleY = s }
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .hoverTap(Oc.round(10.dp)) { HelperView.open(sessionId, r.callId, bounds) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HelperGlyph(r.status, 34.dp, r.agent, variant)
        Spacer(Modifier.height(4.dp))
        Txt(short(r.label) + if (variant > 0) " ${variant + 1}" else "", size = 11.sp, color = if (r.status == "asking") c.accent else c.textDim, maxLines = 1)
    }
}

/**
 * A helper is a small cl0udly: the mascot itself (same drawing, same shape), only smaller.
 * The state shows in the eyes, as with the big one: glancing while it works, wide open while it
 * waits for the user's OK, "^" when done, "x" when it failed. The body stays still.
 * Each helper has its own color (by its model, [key]) and its own rhythm, so several side by side differ;
 * [variant] tells apart two helpers of the same model.
 */
@Composable
fun HelperGlyph(status: String, size: Dp, key: String = "", variant: Int = 0) {
    val c = Oc.c
    val seed = (key.hashCode() and 0x7fffffff) + variant * 3
    val tint = HELPER_TINTS[seed % HELPER_TINTS.size]
    val t = rememberInfiniteTransition(label = "helper")
    val look by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(1100 + seed % 5 * 160, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "look")
    val blink by t.animateFloat(0f, 1f, infiniteRepeatable(androidx.compose.animation.core.keyframes { val d = 3300 + seed % 7 * 340; durationMillis = d; 0f at 0; 0f at d - 250; 1f at d - 180; 1f at d - 100; 0f at d - 30 }), label = "blink")
    val mood = when (status) { "done" -> CloudMood.Happy; "failed" -> CloudMood.Dizzy; "asking" -> CloudMood.Surprised; else -> CloudMood.Idle }
    Box(Modifier.size(width = size + 6.dp, height = size * CH / CW + 6.dp)) {
        Canvas(Modifier.align(Alignment.BottomStart).size(width = size, height = size * CH / CW)) {
            drawIconCloud(
                0f, 0f, this.size.width, mood,
                eyeDx = if (status == "working") look * 0.03f else 0f,
                blink = if (status == "working" && blink > 0.5f) 1f else 0f,
                eye = if (c.isDark) c.bg else INK, outline = if (c.isDark) null else INK,
                body = tint.first, shade = tint.second,
            )
        }
        if (status == "done") Box(Modifier.align(Alignment.TopEnd).size(12.dp).clip(Oc.pill).background(c.ok), contentAlignment = Alignment.Center) { Icon(Ic.Check, 9.dp, c.bg, stroke = 2.4f) }
        if (status == "failed") Box(Modifier.align(Alignment.TopEnd).size(12.dp).clip(Oc.pill).background(c.danger), contentAlignment = Alignment.Center) { Icon(Ic.Close, 8.dp, c.bg, stroke = 2.4f) }
    }
}

/**
 * The open helper: a card that grows out of its cloud (position, size and corners animate together,
 * 220 ms), with the model and its price, the task it got, its steps live, and its OK requests.
 * Esc, a click outside or the close button shrink it back into the cloud.
 */
@Composable
fun HelperHost() {
    val sid = HelperView.sessionId ?: return
    val call = HelperView.callId ?: return
    val c = Oc.c
    val runs = helperRuns(sid)
    val run = runs.firstOrNull { it.callId == call }
    val t = remember(call) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(call) { t.animateTo(1f, tween(220, easing = FastOutSlowInEasing)) }
    fun close() = scope.launch { t.animateTo(0f, tween(170, easing = FastOutSlowInEasing)); HelperView.callId = null; HelperView.sessionId = null }
    DisposableEffect(Unit) { app.cl0udly.ui.kit.Overlay.count++; onDispose { app.cl0udly.ui.kit.Overlay.count-- } }
    val focus = remember { FocusRequester() }
    LaunchedEffect(call) { runCatching { focus.requestFocus() } }
    val d = LocalDensity.current
    BoxWithConstraints(
        Modifier.fillMaxSize().background(c.scrim.copy(alpha = c.scrim.alpha * t.value))
            .clickable(remember { MutableInteractionSource() }, null) { close() }
            .focusRequester(focus).focusable()
            .onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { close(); true } else false },
    ) {
        val W = with(d) { maxWidth.toPx() }; val H = with(d) { maxHeight.toPx() }
        val tw = minOf(W - with(d) { 64.dp.toPx() }, with(d) { 760.dp.toPx() }); val th = H * 0.82f
        val to = Rect(Offset((W - tw) / 2, (H - th) / 2), Size(tw, th))
        val from = HelperView.from.takeIf { it.width > 0 } ?: Rect(to.center, Size(1f, 1f))
        val k = t.value
        val r = Rect(lerp(from.left, to.left, k), lerp(from.top, to.top, k), lerp(from.right, to.right, k), lerp(from.bottom, to.bottom, k))
        val shape = Oc.round(lerp(12f, 18f, k).dp)
        Box(
            Modifier.offset(x = with(d) { r.left.toDp() }, y = with(d) { r.top.toDp() }).size(with(d) { r.width.toDp() }, with(d) { r.height.toDp() })
                .clip(shape).background(c.bg).border(1.dp, c.border, shape)
                .clickable(remember { MutableInteractionSource() }, null) { },
        ) {
            // the content comes in once the card has nearly grown, so text never squeezes
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = ((k - 0.55f) / 0.45f).coerceIn(0f, 1f) }) {
                if (run != null) HelperCard(sid, run, variantOf(runs, run), ::close) else Txt("This helper is gone.", color = c.textDim, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun HelperCard(sessionId: String, r: HelperRun, variant: Int, close: () -> Unit) {
    val c = Oc.c
    val pick = Helpers.pickFor(r.agent)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 12.dp, top = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            HelperGlyph(r.status, 40.dp, r.agent, variant)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Txt(r.label, size = 17.sp, weight = FontWeight.SemiBold, family = Oc.heading, maxLines = 1)
                Txt(listOfNotNull(pick?.provider?.name, pick?.let(Helpers::cost), statusText(r.status)).joinToString("  ·  "), size = 13.sp, color = c.textDim, maxLines = 1)
            }
            app.cl0udly.ui.kit.IconButton(Ic.Close, { close() }, size = 30.dp, icon = 15.dp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 16.dp)) {
            Txt("Task", size = 12.sp, color = c.textDim, weight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Txt(r.description, size = 15.sp)
            if (r.prompt.isNotBlank()) {
                var full by remember { mutableStateOf(false) }
                Spacer(Modifier.height(10.dp))
                Column(Modifier.fillMaxWidth().clip(Oc.round(10.dp)).background(c.surface).hoverTap(Oc.round(10.dp)) { full = !full }.padding(12.dp)) {
                    Txt(if (full) r.prompt else r.prompt.take(280) + if (r.prompt.length > 280) "…" else "", size = 13.sp, color = c.textDim, lineHeight = 19.sp)
                    if (r.prompt.length > 280) Txt(if (full) "Show less" else "Show the whole brief", size = 12.sp, color = c.link, modifier = Modifier.padding(top = 6.dp))
                }
            }
            Spacer(Modifier.height(18.dp))
            Txt("What it does", size = 12.sp, color = c.textDim, weight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            val child = r.childId
            if (child == null) Txt(if (r.status == "asking") "Waiting for your OK to start." else "Starting…", size = 14.sp, color = c.textDim)
            else {
                val chat = remember(child) { Graph.hub.chat(child) }
                val replies = chat.messages.filter { !it.info.isUser }
                if (replies.isEmpty()) Txt(if (Graph.hub.statuses[child] != null) "Thinking…" else "Nothing yet.", size = 14.sp, color = c.textDim)
                replies.forEach { m -> AssistantMessage(m) }
            }
        }
        // its question for the user sits where the field would be, like in a chat
        r.permission?.let { p ->
            Box(Modifier.fillMaxWidth().padding(16.dp)) { if (p.permission == "task") TaskPermissionPanel(p) else PermissionPanel(p, Helpers.label(r.agent), r.agent) }
        }
    }
}

private fun statusText(s: String) = when (s) { "asking" -> "waits for your OK"; "done" -> "done"; "failed" -> "failed"; else -> "working" }

/** The agent wants a helper: which model, what it costs, what for; allow once, always for this model, or not. */
@Composable
fun TaskPermissionPanel(r: PermissionReq) {
    val c = Oc.c
    val agent = r.metadata.str("subagent_type") ?: r.patterns.firstOrNull().orEmpty()
    val pick = Helpers.pickFor(agent)
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().clip(Oc.round(M.composerRadius)).background(c.surface).border(1.dp, c.accent.copy(alpha = 0.5f), Oc.round(M.composerRadius)).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HelperGlyph("asking", 30.dp, agent)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Txt("Ask ${Helpers.label(agent)} for help?", size = 16.sp, weight = FontWeight.SemiBold)
                Txt(listOfNotNull(pick?.provider?.name, pick?.let(Helpers::cost)).joinToString("  ·  ").ifBlank { "A helper model" }, size = 13.sp, color = c.textDim)
            }
        }
        r.metadata.str("description")?.let {
            Spacer(Modifier.height(10.dp))
            Txt(it, size = 14.sp, modifier = Modifier.fillMaxWidth().clip(Oc.round(8.dp)).background(c.codeBg).padding(10.dp))
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button("Allow once") { Graph.hub.replyPermission(r, "once") }
            if (pick != null) Button("Always allow this model", primary = false) {
                scope.launch { Helpers.alwaysAllow(agent); Graph.hub.replyPermission(r, "once") }
            }
            Spacer(Modifier.weight(1f))
            Button("Deny", primary = false) { Graph.hub.replyPermission(r, "reject") }
        }
    }
}

/** A cloud's caption: the model's name without "Free", cut with an ellipsis. */
private fun short(label: String): String {
    val t = label.removeSuffix(" Free").removeSuffix(" (free)").trim()
    return if (t.length <= 20) t else t.take(19).trimEnd() + "…"
}

/** Helper body colors: soft and flat, each with its darker step; the main cl0udly stays white. */
private val HELPER_TINTS = listOf(
    Color(0xFFA9C8FF) to Color(0xFF7F9FD6),
    Color(0xFFA8E6C8) to Color(0xFF79B898),
    Color(0xFFFFCFA6) to Color(0xFFD9A274),
    Color(0xFFCDBBFF) to Color(0xFF9F8CD6),
    Color(0xFFFFB8CC) to Color(0xFFD68A9E),
    Color(0xFFF2E49C) to Color(0xFFC7B86C),
    Color(0xFFA3E4EC) to Color(0xFF73B6BF),
    Color(0xFFE6DCCB) to Color(0xFFB8AD9B),
)

/** Which of the same model's helpers in this chat [r] is: 0 for the first, 1 for the second… */
internal fun variantOf(runs: List<HelperRun>, r: HelperRun) = runs.filter { it.agent == r.agent }.indexOfFirst { it.callId == r.callId }.coerceAtLeast(0)
