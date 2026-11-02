package app.cl0udly.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cl0udly.Graph
import app.cl0udly.ui.kit.Ic
import app.cl0udly.ui.kit.Icon
import app.cl0udly.ui.kit.IconButton
import app.cl0udly.ui.kit.Txt
import app.cl0udly.ui.kit.hoverTap
import app.cl0udly.ui.theme.Oc

/** What the input is doing right now: the mascot looks down at it while someone types. */
object ComposerState {
    var typing by mutableStateOf(false)
}

/**
 * The input. [big] is the home screen's (text on top, tools under it); the chat's is the measured
 * one-row field (56 px, radius 14) with the model under it on the right.
 */
@Composable
fun Composer(
    big: Boolean,
    busy: Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit = {},
    placeholder: String = "How can I help you today?",
    modifier: Modifier = Modifier,
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
                        if (e.type == KeyEventType.KeyDown && e.key == Key.Enter) {
                            if (e.isShiftPressed) {
                                val t = value.text; val s = value.selection.start
                                value = TextFieldValue(t.substring(0, s) + "\n" + t.substring(value.selection.end), TextRange(s + 1))
                            } else send()
                            true
                        } else false
                    },
                textStyle = TextStyle(color = c.text, fontSize = M.body, lineHeight = M.bodyLine, fontFamily = Oc.body),
                cursorBrush = SolidColor(c.accent),
                maxLines = 12,
            )
        }
    }
    val shape = Oc.round(if (big) 20.dp else M.composerRadius)
    Column(modifier) {
        if (big) {
            Column(Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, border, shape).padding(start = 18.dp, end = 12.dp, top = 18.dp, bottom = 12.dp)) {
                field(Modifier.fillMaxWidth().heightIn(min = 30.dp).padding(end = 6.dp))
                Spacer(Modifier.size(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(Ic.Plus, { attach { value = TextFieldValue(value.text + it, TextRange(value.text.length + it.length)) } }, tint = c.textDim)
                    Spacer(Modifier.width(6.dp))
                    AgentChip()
                    Spacer(Modifier.weight(1f))
                    ModelPicker()
                    Spacer(Modifier.width(8.dp))
                    SendButton(busy, value.text.isNotBlank(), ::send, onStop)
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().heightIn(min = M.composerHeight).clip(shape).background(c.surface).border(1.dp, border, shape).padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(Ic.Plus, { attach { value = TextFieldValue(value.text + it, TextRange(value.text.length + it.length)) } }, tint = c.textDim)
                Spacer(Modifier.width(8.dp))
                field(Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                SendButton(busy, value.text.isNotBlank(), ::send, onStop)
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, end = 6.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                AgentChip()
                Spacer(Modifier.width(6.dp))
                ModelPicker()
            }
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

/** Build / Plan: OpenCode's two main agents, like Claude Code's modes. */
@Composable
private fun AgentChip() {
    val c = Oc.c
    val prefs = Graph.prefs
    var open by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.hoverTap(Oc.round(8.dp)) { open = true }.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt(prefs.agent.replaceFirstChar { it.uppercase() }, size = 15.sp, color = c.textDim)
        }
        Menu(open, { open = false }) {
            Graph.hub.agents.forEach { a ->
                MenuItem(null, a.replaceFirstChar { it.uppercase() }, hint = when (a) { "build" -> "edits and runs"; "plan" -> "read only"; else -> null }, checked = prefs.agent == a) {
                    prefs.agent = a; open = false
                }
            }
        }
    }
}

/** The model under the field, like "Opus 5.5 Medium": name, then the reasoning level when the model has one. */
@Composable
fun ModelPicker() {
    val c = Oc.c
    val prefs = Graph.prefs
    val hub = Graph.hub
    var open by remember { mutableStateOf(false) }
    val pick = hub.pick
    Box {
        Row(Modifier.hoverTap(Oc.round(8.dp)) { open = true }.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt(pick?.label ?: "Pick a model", size = 15.sp, maxLines = 1, modifier = Modifier.widthIn(max = 260.dp))
            if (prefs.variant.isNotBlank()) Txt("  " + prefs.variant.replaceFirstChar { it.uppercase() }, size = 15.sp, color = c.textDim)
        }
        Menu(open, { open = false }) {
            var q by remember { mutableStateOf("") }
            Field(q, { q = it }, "Search models", Modifier.width(320.dp).padding(horizontal = 10.dp, vertical = 10.dp), size = 14)
            LazyColumn(Modifier.heightIn(max = 380.dp).width(340.dp)) {
                val list = hub.picks.filter { q.isBlank() || it.label.contains(q, true) || it.provider.name.contains(q, true) }
                items(list, key = { it.key }) { p ->
                    MenuItem(null, p.label, hint = p.provider.name.take(18), checked = p == pick) {
                        prefs.providerId = p.provider.id; prefs.modelId = p.model.id
                        if (prefs.variant !in p.model.variantNames) prefs.variant = ""
                        open = false
                    }
                }
                if (list.isEmpty()) item { Txt("No models — add a provider in Settings → Models", size = 13.sp, color = c.textDim, modifier = Modifier.padding(12.dp)) }
            }
            pick?.model?.variantNames?.takeIf { it.isNotEmpty() }?.let { vs ->
                Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).size(1.dp).background(c.border))
                Txt("Thinking", size = 12.sp, color = c.textDim, weight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                (listOf("") + vs).forEach { v ->
                    MenuItem(null, if (v.isBlank()) "Default" else v.replaceFirstChar { it.uppercase() }, checked = prefs.variant == v) { prefs.variant = v; open = false }
                }
            }
        }
    }
}

/** "+" picks files; their paths go into the message so the agent opens them (like @ mentions). */
private fun attach(add: (String) -> Unit) {
    val ch = javax.swing.JFileChooser(Nav.project ?: System.getProperty("user.home")).apply { isMultiSelectionEnabled = true; dialogTitle = "Attach files" }
    if (ch.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
        add(ch.selectedFiles.joinToString(" ", prefix = " ") { "@" + it.absolutePath })
    }
}
