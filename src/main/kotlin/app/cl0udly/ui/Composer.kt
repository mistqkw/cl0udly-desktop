package app.cl0udly.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cl0udly.Graph
import app.cl0udly.data.Pick
import app.cl0udly.ui.kit.Ic
import app.cl0udly.ui.kit.Icon
import app.cl0udly.ui.kit.IconButton
import app.cl0udly.ui.kit.Pop
import app.cl0udly.ui.kit.PopDivider
import app.cl0udly.ui.kit.PopHeader
import app.cl0udly.ui.kit.PopItem
import app.cl0udly.ui.kit.Side
import app.cl0udly.ui.kit.Txt
import app.cl0udly.ui.kit.hoverTap
import app.cl0udly.ui.theme.Oc
import kotlinx.coroutines.delay

/** What the input is doing right now: the mascot looks down at it while someone types. */
object ComposerState {
    var typing by mutableStateOf(false)
}

/** One suggestion under "/" or "@". */
private data class Suggest(val insert: String, val label: String, val sub: String?, val ic: Ic)

/**
 * The input. [big] is the home screen's (text on top, tools under it); the chat's is the measured
 * one-row field (56 px, radius 14) with the model and the permission mode under it on the right.
 * "/" lists slash commands, "@" finds files in the project, "+" adds files, folders, skills.
 */
@Composable
fun Composer(
    big: Boolean,
    busy: Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit = {},
    placeholder: String = "How can I help you today?",
    modifier: Modifier = Modifier,
    directory: String? = null,
    sessionId: String? = null,
    below: @Composable (() -> Unit)? = null,
) {
    val c = Oc.c
    var value by remember { mutableStateOf(TextFieldValue("")) }
    var focused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val border by animateColorAsState(if (focused) c.textDim.copy(alpha = 0.6f) else c.border, tween(120), label = "border")
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(ComposerPrefill.text) {
        val t = ComposerPrefill.text ?: return@LaunchedEffect
        value = TextFieldValue(t, TextRange(t.length)); ComposerPrefill.text = null
        runCatching { focus.requestFocus() }
    }
    LaunchedEffect(value.text, focused) { ComposerState.typing = focused && value.text.isNotEmpty() }

    // the word under the cursor decides the suggestions: "/cmd" at the start, "@file" anywhere
    val word = remember(value) {
        val t = value.text; val at = value.selection.start.coerceIn(0, t.length)
        val start = t.lastIndexOfAny(charArrayOf(' ', '\n'), (at - 1).coerceAtLeast(0)).let { if (it < 0 || at == 0) 0 else it + 1 }
        if (start <= at) t.substring(start, at) to start else "" to at
    }
    var files by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(word.first) {
        if (word.first.startsWith("@") && word.first.length >= 2) {
            delay(120)
            files = runCatching { Graph.api.findFiles(word.first.drop(1), directory) }.getOrDefault(emptyList())
        } else files = emptyList()
    }
    val suggestions = remember(word, files, Graph.hub.commands) {
        val w = word.first
        when {
            w.startsWith("/") && word.second == 0 && !value.text.contains(' ') -> (
                listOf(Suggest("/compact ", "compact", "Summarize the session to free up context", Ic.Layers)) +
                    Graph.hub.commands.map { Suggest("/${it.name} ", it.name, it.description, if (it.source == "skill") Ic.Bulb else Ic.Slash) }
                ).filter { it.label.startsWith(w.drop(1), true) }.take(10)
            w.startsWith("@") -> files.map { Suggest("@$it ", it.substringAfterLast('/'), it, iconFor(it)) }
            else -> emptyList()
        }
    }
    var sel by remember(suggestions) { mutableIntStateOf(0) }
    fun accept(s: Suggest) {
        val t = value.text; val start = word.second; val end = value.selection.start
        val nt = t.substring(0, start) + s.insert + t.substring(end)
        value = TextFieldValue(nt, TextRange(start + s.insert.length))
    }
    fun insert(s: String) {
        val t = value.text; val at = value.selection.start
        value = TextFieldValue(t.substring(0, at) + s + t.substring(value.selection.end), TextRange(at + s.length))
        runCatching { focus.requestFocus() }
    }
    fun send() {
        val t = value.text.trim()
        if (t.isEmpty()) return
        onSend(t); value = TextFieldValue("")
    }

    val field = @Composable { mod: Modifier ->
        Box(mod, contentAlignment = Alignment.CenterStart) {
            if (value.text.isEmpty()) Txt(placeholder, size = M.body, color = c.textDim, maxLines = 1)
            BasicTextField(
                value, { value = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { focused = it.isFocused }
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        if (suggestions.isNotEmpty()) when (e.key) {
                            Key.DirectionDown -> { sel = (sel + 1) % suggestions.size; return@onPreviewKeyEvent true }
                            Key.DirectionUp -> { sel = (sel - 1 + suggestions.size) % suggestions.size; return@onPreviewKeyEvent true }
                            Key.Tab, Key.Enter -> { accept(suggestions[sel.coerceIn(0, suggestions.lastIndex)]); return@onPreviewKeyEvent true }
                            else -> {}
                        }
                        when {
                            e.key == Key.Enter && e.isShiftPressed -> { insert("\n"); true }
                            e.key == Key.Enter -> { send(); true }
                            e.key == Key.U && e.isCtrlPressed -> { Pickers.files { l -> insert(l.joinToString(" ", postfix = " ") { "@$it" }) }; true }
                            else -> false
                        }
                    },
                textStyle = TextStyle(color = c.text, fontSize = M.body, lineHeight = M.bodyLine, fontFamily = Oc.body),
                cursorBrush = SolidColor(c.accent),
                maxLines = 12,
            )
        }
    }
    val shape = Oc.round(if (big) 20.dp else M.composerRadius)
    Column(modifier) {
        // "/" and "@" suggestions sit right above the field
        Box(Modifier.fillMaxWidth()) {
            Pop(suggestions.isNotEmpty() && focused, { value = TextFieldValue(value.text + " ", TextRange(value.text.length + 1)) }, Side.Above, width = 420.dp, focusable = false) {
                PopHeader(if (word.first.startsWith("/")) "Commands" else "Files")
                suggestions.forEachIndexed { i, s ->
                    Box(Modifier.background(if (i == sel) c.surfaceHigh.copy(alpha = 0.6f) else c.surface, Oc.round(8.dp))) {
                        PopItem(s.label, s.ic, sub = s.sub?.take(90)) { accept(s); runCatching { focus.requestFocus() } }
                    }
                }
            }
        }
        if (big) {
            Column(Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, border, shape).padding(start = 18.dp, end = 12.dp, top = 18.dp, bottom = 12.dp)) {
                field(Modifier.fillMaxWidth().heightIn(min = 30.dp).padding(end = 6.dp))
                Spacer(Modifier.size(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlusMenu(::insert)
                    Spacer(Modifier.weight(1f))
                    SendButton(busy, value.text.isNotBlank(), ::send, onStop)
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().heightIn(min = M.composerHeight).clip(shape).background(c.surface).border(1.dp, border, shape).padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlusMenu(::insert)
                Spacer(Modifier.width(8.dp))
                field(Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                SendButton(busy, value.text.isNotBlank(), ::send, onStop)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            below?.invoke()
            Spacer(Modifier.weight(1f))
            if (sessionId != null) ContextRing(sessionId)
            ModelPicker()
            PermissionChip()
        }
    }
}

/** Send turns into a stop square while the agent works (Claude desktop: same spot). */
@Composable
private fun SendButton(busy: Boolean, ready: Boolean, onSend: () -> Unit, onStop: () -> Unit) {
    val c = Oc.c
    val bg by animateColorAsState(if (busy || ready) c.text else c.surfaceHigh, tween(120), label = "send")
    Box(Modifier.size(34.dp).clip(Oc.pill).background(bg).hoverTap(Oc.pill) { if (busy && !ready) onStop() else onSend() }, contentAlignment = Alignment.Center) {
        AnimatedContent(busy && !ready, transitionSpec = { (fadeIn(tween(110)) + scaleIn(tween(110), 0.7f)) togetherWith fadeOut(tween(80)) }, label = "sendIcon") { stop ->
            if (stop) Icon(Ic.Stop, 14.dp, c.bg) else Icon(Ic.ArrowUp, 18.dp, if (ready) c.bg else c.textDim, stroke = 2f)
        }
    }
}

/** "+": files, a folder, slash commands, skills, MCP servers, the agent (Claude desktop's + menu). */
@Composable
private fun PlusMenu(insert: (String) -> Unit) {
    val hub = Graph.hub
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(Ic.Plus, { open = true }, tint = Oc.c.textDim, selected = open)
        Pop(open, { open = false }, Side.Above, minWidth = 250.dp) {
            PopItem("Add files or photos", Ic.File, hint = "Ctrl+U") { open = false; Pickers.files { l -> insert(l.joinToString(" ", postfix = " ") { "@$it" }) } }
            PopItem("Add folder", Ic.Folder) { open = false; Pickers.folder("Add a folder") { insert("@$it ") } }
            PopItem("Slash commands", Ic.Slash) { open = false; insert("/") }
            PopDivider()
            PopItem("Skills", Ic.Bulb, submenu = { close ->
                val skills = hub.skills
                if (skills.isEmpty()) PopItem("No skills yet", enabled = false)
                skills.take(20).forEach { s -> PopItem(s.name, sub = s.description?.take(80)) { close(); open = false; insert("/${s.name} ") } }
            })
            PopItem("Agent", Ic.Layers, value = Graph.prefs.agent.replaceFirstChar { it.uppercase() }, submenu = { close ->
                hub.agents.forEach { a ->
                    PopItem(a.replaceFirstChar { it.uppercase() }, sub = when (a) { "build" -> "Edits files and runs commands"; "plan" -> "Reads and plans, changes nothing"; else -> null }, checked = Graph.prefs.agent == a) {
                        Graph.prefs.agent = a; close(); open = false
                    }
                }
            })
            PopItem("Connectors (MCP)", Ic.Box) { open = false; Nav.settings = "capabilities" }
        }
    }
}

/** Claude's picker: the four latest models with number keys, Effort and More models to the side. */
@Composable
fun ModelPicker() {
    val c = Oc.c
    val prefs = Graph.prefs
    val hub = Graph.hub
    var open by remember { mutableStateOf(false) }
    val pick = hub.pick
    val visible = hub.picks.filter { it.key !in prefs.hiddenModelSet }
    val top = (prefs.recentModelList.mapNotNull { k -> visible.firstOrNull { it.key == k } } + listOfNotNull(pick) + visible).distinct().take(4)
    fun choose(p: Pick) {
        prefs.providerId = p.provider.id; prefs.modelId = p.model.id
        if (prefs.variant !in p.model.variantNames) prefs.variant = ""
        prefs.useModel(p.key); open = false
    }
    Box {
        Row(Modifier.hoverTap(Oc.round(8.dp), open) { open = true }.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt(pick?.label ?: "Pick a model", size = 15.sp, maxLines = 1, modifier = Modifier.widthIn(max = 260.dp))
            if (prefs.variant.isNotBlank()) Txt("  " + prefs.variant.replaceFirstChar { it.uppercase() }, size = 15.sp, color = c.textDim)
        }
        Pop(open, { open = false }, Side.Above, width = 340.dp, onKey = { e ->
            if (e.type == KeyEventType.KeyDown) {
                val n = when (e.key) { Key.One -> 0; Key.Two -> 1; Key.Three -> 2; Key.Four -> 3; else -> -1 }
                if (n in top.indices) { choose(top[n]); true } else false
            } else false
        }) {
            top.forEachIndexed { i, p ->
                PopItem(p.label, sub = p.provider.name + (if (p.model.id.endsWith("-free")) " · free" else ""), hint = "${i + 1}", checked = p == pick) { choose(p) }
            }
            if (top.isEmpty()) PopItem("No models yet", sub = "Add a provider in Settings → Models", enabled = false)
            PopDivider()
            val variants = pick?.model?.variantNames.orEmpty()
            if (variants.isNotEmpty()) PopItem("Effort", value = prefs.variant.ifBlank { "Default" }.replaceFirstChar { it.uppercase() }, submenu = { close ->
                (listOf("") + variants).forEach { v ->
                    PopItem(if (v.isBlank()) "Default" else v.replaceFirstChar { it.uppercase() }, checked = prefs.variant == v) { prefs.variant = v; close(); open = false }
                }
            })
            PopItem("More models", submenu = { close ->
                var q by remember { mutableStateOf("") }
                val qf = remember { FocusRequester() }
                Field(q, { q = it }, "Search models", Modifier.width(300.dp).padding(horizontal = 10.dp, vertical = 8.dp).focusRequester(qf), size = 14)
                LaunchedEffect(Unit) { runCatching { qf.requestFocus() } }
                visible.filter { q.isBlank() || it.label.contains(q, true) || it.provider.name.contains(q, true) }
                    .groupBy { it.provider.name }.forEach { (prov, list) ->
                        PopHeader(prov)
                        list.forEach { p -> PopItem(p.label, checked = p == pick) { choose(p); close() } }
                    }
            })
            PopItem("Manage models…", Ic.Settings) { open = false; Nav.settings = "models" }
        }
    }
}

/** Manual / Auto, like Claude's permission mode under the field. */
@Composable
private fun PermissionChip() {
    val prefs = Graph.prefs
    var open by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.hoverTap(Oc.round(8.dp), open) { open = true }.padding(horizontal = 9.dp, vertical = 6.dp)) {
            Txt(if (prefs.permMode == "auto") "Auto" else "Manual", size = 15.sp)
        }
        Pop(open, { open = false }, Side.Above, width = 290.dp) {
            PopHeader("Permission mode")
            PopItem("Manual", sub = "cl0udly asks before running commands or editing files.", checked = prefs.permMode != "auto") { prefs.permMode = "manual"; open = false }
            PopItem("Auto", sub = "cl0udly goes on by itself and never stops to ask.", checked = prefs.permMode == "auto") { prefs.permMode = "auto"; open = false }
        }
    }
}

/** A small ring of how full the context window is; a click opens the Context panel. */
@Composable
private fun ContextRing(sessionId: String) {
    val c = Oc.c
    val ctx = contextOf(sessionId)
    Box(Modifier.size(30.dp).hoverTap(Oc.round(8.dp)) { Nav.toggle("context") }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(16.dp)) {
            val sw = 2.4.dp.toPx()
            drawArc(c.border, 0f, 360f, false, Offset(sw / 2, sw / 2), Size(size.width - sw, size.height - sw), style = Stroke(sw))
            drawArc(if (ctx.share > 0.85f) c.warn else c.link, -90f, 360f * ctx.share, false, Offset(sw / 2, sw / 2), Size(size.width - sw, size.height - sw), style = Stroke(sw, cap = StrokeCap.Round))
        }
    }
}

/** How full a session's context is: the last reply's input tokens against the model's window. */
data class ContextUse(val used: Long, val limit: Long) {
    val share get() = if (limit > 0) (used.toFloat() / limit).coerceIn(0f, 1f) else 0f
}

fun contextOf(sessionId: String): ContextUse {
    val hub = Graph.hub
    val last = hub.chat(sessionId).messages.lastOrNull { !it.info.isUser && it.info.tokens != null }?.info
    val t = last?.tokens
    val used = t?.let { it.input + it.cache.read + it.cache.write + it.output } ?: 0
    val limit = hub.picks.firstOrNull { it.model.id == last?.modelName && it.provider.id == last.providerName }?.model?.limit?.context
        ?: hub.pick?.model?.limit?.context ?: 0
    return ContextUse(used, limit)
}

/** A suggestion for chats only starts the sentence: it goes into the field, not straight to the model. */
object ComposerPrefill { var text by mutableStateOf<String?>(null) }
