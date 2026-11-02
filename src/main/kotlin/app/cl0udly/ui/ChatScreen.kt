package app.cl0udly.ui

import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cl0udly.Graph
import app.cl0udly.api.Part
import app.cl0udly.api.PermissionReq
import app.cl0udly.api.QuestionReq
import app.cl0udly.api.obj
import app.cl0udly.api.str
import app.cl0udly.data.ChatMessage
import app.cl0udly.ui.cloud.CloudThinking
import app.cl0udly.ui.kit.Ic
import app.cl0udly.ui.kit.Icon
import app.cl0udly.ui.kit.Txt
import app.cl0udly.ui.kit.Work
import app.cl0udly.ui.kit.hoverTap
import app.cl0udly.ui.theme.Oc
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Composable
fun ChatScreen(id: String) {
    val c = Oc.c
    val hub = Graph.hub
    val chat = remember(id) { hub.chat(id) }
    val busy = hub.statuses[id] != null
    val list = rememberLazyListState()
    val atBottom by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= list.layoutInfo.totalItemsCount - 2 } ?: true } }
    // follow the reply while it grows, unless the user scrolled up to read
    LaunchedEffect(id) {
        snapshotFlow { chat.messages.size to chat.messages.lastOrNull()?.parts?.sumOf { it.text.length } }.collect {
            if (atBottom && list.layoutInfo.totalItemsCount > 0) list.scrollToItem(list.layoutInfo.totalItemsCount - 1, Int.MAX_VALUE / 2)
        }
    }
    val perm = hub.permissions.firstOrNull { it.sessionID == id }
    val question = hub.questions.firstOrNull { it.sessionID == id }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val col = minOf(if (Graph.prefs.transcriptWidth == "wide") 1240.dp else M.column, maxWidth - 64.dp)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                SelectionContainer {
                    LazyColumn(Modifier.fillMaxSize(), state = list, horizontalAlignment = Alignment.CenterHorizontally) {
                        item { Spacer(Modifier.height(32.dp)) }
                        items(chat.messages, key = { it.id }) { m ->
                            Box(Modifier.width(col).animateItem(fadeInSpec = tween(140), fadeOutSpec = null)) {
                                if (m.info.isUser) UserMessage(m) else AssistantMessage(m)
                            }
                        }
                        if (busy) item(key = "working") { Box(Modifier.width(col).padding(top = 6.dp, bottom = 10.dp)) { Working(chat.messages.lastOrNull(), id) } }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
                if (chat.loading && chat.messages.isEmpty()) Txt("Loading…", color = c.textDim, modifier = Modifier.align(Alignment.Center))
                chat.loadError?.let { Txt(it, color = c.danger, modifier = Modifier.align(Alignment.Center)) }
                androidx.compose.foundation.VerticalScrollbar(
                    androidx.compose.foundation.rememberScrollbarAdapter(list),
                    Modifier.align(Alignment.CenterEnd).fillMaxSize().padding(end = 3.dp, top = 4.dp, bottom = 4.dp).width(8.dp),
                    style = androidx.compose.foundation.defaultScrollbarStyle().copy(unhoverColor = c.border, hoverColor = c.textDim, thickness = 6.dp),
                )
            }
            Box(Modifier.width(col + M.composerExtra * 2).padding(bottom = 14.dp, top = 6.dp)) {
                when {
                    perm != null -> PermissionPanel(perm)
                    question != null -> QuestionPanel(question)
                    else -> Composer(big = false, busy = busy, onSend = { t -> Graph.scope.launchIt { hub.send(id, t) } }, onStop = { hub.stop(id) }, placeholder = if (busy) "Add to the queue…" else "Reply to cl0udly")
                }
            }
        }
    }
}

fun kotlinx.coroutines.CoroutineScope.launchIt(block: suspend () -> Unit) = launch { block() }

@Composable
private fun UserMessage(m: ChatMessage) {
    val c = Oc.c
    val text = m.parts.filter { it.type == "text" && it.raw["synthetic"]?.let { s -> (s as? JsonPrimitive)?.contentOrNull } != "true" }.joinToString("\n") { it.text }
    when (Oc.look.user) {
        "plain" -> Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
            Txt("You", size = 13.sp, color = c.accent, weight = FontWeight.SemiBold, family = Oc.label)
            Spacer(Modifier.height(6.dp))
            Txt(text, size = M.body, lineHeight = M.bodyLine)
        }
        else -> Box(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 22.dp), contentAlignment = Alignment.CenterEnd) {
            val shape = Oc.round(M.bubbleRadius)
            val line = Oc.look.user == "line"
            Box(
                Modifier.widthIn(max = M.bubbleMax).clip(shape)
                    .then(if (line) Modifier.border(1.dp, c.border, shape) else Modifier.background(c.userBubble))
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) { Txt(text, size = M.body, lineHeight = M.bodyLine) }
        }
    }
}

/** One reply: steps (thoughts and tools) fold into a line, the text is markdown, actions show on hover. */
@Composable
private fun AssistantMessage(m: ChatMessage) {
    val c = Oc.c
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val groups = remember(m.parts.toList()) { group(m.parts) }
    Column(Modifier.fillMaxWidth().hoverable(hover).padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(M.paragraphGap)) {
        groups.forEach { g ->
            when (g) {
                is Group.Text -> Markdown(g.text)
                is Group.Steps -> Steps(g.parts)
            }
        }
        m.info.errorMessage?.takeIf { m.info.errorName != "MessageAbortedError" }?.let {
            Row(Modifier.clip(Oc.round(10.dp)).background(c.danger.copy(alpha = 0.12f)).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Txt(it, size = 14.sp, color = c.danger)
            }
        }
        val text = groups.filterIsInstance<Group.Text>().joinToString("\n\n") { it.text }
        if (text.isNotBlank() && m.info.time.completed != null) Row(
            Modifier.graphicsLayer { alpha = if (hovered) 1f else 0f }.height(30.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var copied by remember { mutableStateOf(false) }
            Box(Modifier.size(30.dp).hoverTap(Oc.round(8.dp)) { copyText(text); copied = true }, contentAlignment = Alignment.Center) { Icon(if (copied) Ic.Check else Ic.Copy, 16.dp, c.textDim) }
            Spacer(Modifier.width(8.dp))
            Txt(m.info.modelName.orEmpty(), size = 13.sp, color = c.textDim)
        }
    }
}

private sealed interface Group {
    data class Text(val text: String) : Group
    data class Steps(val parts: List<Part>) : Group
}

private fun group(parts: List<Part>): List<Group> {
    val out = ArrayList<Group>()
    val steps = ArrayList<Part>()
    fun flush() { if (steps.isNotEmpty()) { out.add(Group.Steps(steps.toList())); steps.clear() } }
    parts.forEach { p ->
        when (p.type) {
            "text" -> if (p.text.isNotBlank()) { flush(); out.add(Group.Text(p.text)) }
            "reasoning" -> if (p.text.isNotBlank()) steps.add(p)
            "tool" -> steps.add(p)
        }
    }
    flush()
    return out
}

private val Part.tool get() = raw.str("tool") ?: ""
private val Part.state get() = raw.obj("state")
private val Part.status get() = state?.str("status") ?: ""
private val Part.input get() = state?.obj("input")

/** What a tool did, in a few words (the phone's StepsGroup wording). */
private fun Part.title(): String {
    val inp = input
    val t = state?.str("title")
    return when (tool) {
        "bash" -> inp?.str("description") ?: inp?.str("command") ?: "Ran a command"
        "read" -> "Read " + (inp?.str("filePath")?.substringAfterLast('/') ?: "a file")
        "edit" -> "Edited " + (inp?.str("filePath")?.substringAfterLast('/') ?: "a file")
        "write" -> "Wrote " + (inp?.str("filePath")?.substringAfterLast('/') ?: "a file")
        "grep", "glob" -> "Searched for " + (inp?.str("pattern") ?: "files")
        "webfetch" -> "Opened " + (inp?.str("url") ?: "a page")
        "todowrite" -> "Updated the plan"
        "task" -> inp?.str("description") ?: "Ran a helper"
        else -> t ?: tool
    }
}

private fun summary(parts: List<Part>): String {
    val bits = ArrayList<String>()
    if (parts.any { it.type == "reasoning" }) bits.add("Thought")
    val tools = parts.filter { it.type == "tool" }
    fun count(n: Int, one: String, many: String) { if (n > 0) bits.add(if (n == 1) one else many.format(n)) }
    count(tools.count { it.tool == "bash" }, "ran a command", "ran %d commands")
    count(tools.count { it.tool == "read" }, "read a file", "read %d files")
    count(tools.count { it.tool in setOf("edit", "write", "patch") }, "edited a file", "edited %d files")
    count(tools.count { it.tool in setOf("grep", "glob", "list") }, "searched", "searched %d times")
    count(tools.count { it.tool !in setOf("bash", "read", "edit", "write", "patch", "grep", "glob", "list") }, "used a tool", "used %d tools")
    return bits.joinToString(", ").replaceFirstChar { it.uppercase() }.ifBlank { "Worked" }
}

@Composable
private fun Steps(parts: List<Part>) {
    val c = Oc.c
    val view = Graph.prefs.transcriptView
    var open by remember(view) { mutableStateOf(view == "verbose" || view == "thinking" && parts.any { it.type == "reasoning" }) }
    val failed = parts.any { it.status == "error" }
    Column(Modifier.fillMaxWidth().animateContentSize(tween(140))) {
        Row(Modifier.hoverTap(Oc.round(8.dp)) { open = !open }.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt(summary(parts), size = 15.sp, color = if (failed) c.danger else c.textDim)
            Spacer(Modifier.width(4.dp))
            Icon(if (open) Ic.Chevron else Ic.ChevronRight, 14.dp, c.textDim)
        }
        AnimatedVisibility(open, enter = expandVertically(tween(140)) + fadeIn(tween(120)), exit = shrinkVertically(tween(110)) + fadeOut(tween(80))) {
            Column(Modifier.padding(start = 10.dp, top = 6.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                parts.forEach { p -> Step(p) }
            }
        }
    }
}

@Composable
private fun Step(p: Part) {
    val c = Oc.c
    val view = Graph.prefs.transcriptView
    var open by remember(view) { mutableStateOf(view == "verbose" || view == "thinking" && p.type == "reasoning") }
    if (p.type == "reasoning") {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(2.dp).heightIn(min = 20.dp).background(c.border))
            Spacer(Modifier.width(12.dp))
            Txt(p.text.trim(), size = 14.sp, color = c.textDim, lineHeight = 22.sp, maxLines = if (open) Int.MAX_VALUE else 4, modifier = Modifier.hoverTap(Oc.round(6.dp)) { open = !open })
        }
        return
    }
    val ic = when (p.tool) { "bash" -> Ic.Terminal; "read" -> Ic.File; "edit", "write", "patch" -> Ic.Edit; "grep", "glob", "list" -> Ic.Search; "webfetch" -> Ic.Globe; "todowrite" -> Ic.Check; else -> Ic.Box }
    val out = p.state?.str("output") ?: p.state?.str("error")
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.hoverTap(Oc.round(8.dp)) { open = !open }.padding(horizontal = 6.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(ic, 16.dp, if (p.status == "error") c.danger else c.textDim)
            Spacer(Modifier.width(10.dp))
            Txt(p.title(), size = 14.sp, color = c.text.copy(alpha = 0.85f), maxLines = 1, modifier = Modifier.widthIn(max = 760.dp))
            if (p.status == "running" || p.status == "pending") { Spacer(Modifier.width(8.dp)); Dots() }
        }
        if (open) {
            val cmd = p.input?.str("command")
            Column(Modifier.padding(start = 32.dp, top = 4.dp, bottom = 6.dp).fillMaxWidth().clip(Oc.round(10.dp)).background(c.codeBg).border(1.dp, c.border, Oc.round(10.dp)).padding(12.dp)) {
                if (cmd != null) Txt("$ $cmd", size = 13.sp, family = Oc.mono, color = c.text)
                if (!out.isNullOrBlank()) {
                    if (cmd != null) Spacer(Modifier.height(8.dp))
                    Txt(out.take(4000), size = 13.sp, family = Oc.mono, color = if (p.status == "error") c.danger else c.textDim, lineHeight = 19.sp)
                }
                if (cmd == null && out.isNullOrBlank()) Txt(p.input?.toString()?.take(600) ?: "", size = 12.sp, family = Oc.mono, color = c.textDim)
            }
        }
    }
}

@Composable
private fun Dots() {
    val c = Oc.c
    val t = rememberInfiniteTransition(label = "dots")
    val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "a")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { i -> Box(Modifier.size(4.dp).graphicsLayer { alpha = if (i == 1) 1.25f - a else a }.clip(CircleShape).background(c.textDim)) }
    }
}

/** What the agent is doing now: the cl0udly cloud at work, or the measured "working 46s ›" line. */
@Composable
private fun Working(last: ChatMessage?, id: String) {
    val c = Oc.c
    val running = last?.takeIf { !it.info.isUser }?.parts?.lastOrNull { it.type == "tool" && it.status in setOf("running", "pending") }
    val work = when {
        Graph.hub.statuses[id] == "retry" -> Work.Wait
        running != null -> when (running.tool) {
            "bash" -> Work.Run; "read" -> Work.Read; "edit", "patch" -> Work.Edit; "write" -> Work.Write
            "grep", "glob", "list" -> Work.Search; "webfetch" -> Work.Web; "todowrite" -> Work.Plan; else -> Work.Other
        }
        last?.info?.isUser != false -> Work.Think
        last.parts.lastOrNull()?.type == "reasoning" -> Work.Think
        else -> Work.Write
    }
    val since = remember(id) { System.currentTimeMillis() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    val label = Graph.hub.retryNote[id] ?: running?.title() ?: work.label
    if (Oc.look.clouds) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CloudThinking(work, Modifier)
            Graph.hub.retryNote[id]?.let { Spacer(Modifier.width(10.dp)); Txt(it, size = 14.sp, color = c.warn, maxLines = 1, modifier = Modifier.widthIn(max = 520.dp)) }
            Spacer(Modifier.width(10.dp))
            Txt("${(now - since) / 1000}s", size = 14.sp, color = c.textDim.copy(alpha = 0.7f))
        }
    } else Row(verticalAlignment = Alignment.CenterVertically) {
        SpinDots()
        Spacer(Modifier.width(14.dp))
        Txt(label, size = 16.sp, color = c.textDim, maxLines = 1, modifier = Modifier.widthIn(max = 640.dp))
        Spacer(Modifier.width(10.dp))
        Txt("${(now - since) / 1000}s", size = 15.sp, color = c.textDim.copy(alpha = 0.7f))
    }
}

/** Four dots in a square that pulse in turn, in the accent (the measured indicator left of "working"). */
@Composable
private fun SpinDots() {
    val c = Oc.c
    val t = rememberInfiniteTransition(label = "spin")
    val p by t.animateFloat(0f, 4f, infiniteRepeatable(tween(1100)), label = "p")
    Box(Modifier.size(18.dp)) {
        listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomEnd, Alignment.BottomStart).forEachIndexed { i, a ->
            val k = ((p - i + 4) % 4).let { if (it < 1f) 1f - it * 0.6f else 0.4f }
            Box(Modifier.align(a).size(6.dp).graphicsLayer { alpha = k }.clip(CircleShape).background(c.accent))
        }
    }
}

// ---------- the agent asks ----------

@Composable
private fun PermissionPanel(r: PermissionReq) {
    val c = Oc.c
    Column(
        Modifier.fillMaxWidth().clip(Oc.round(M.composerRadius)).background(c.surface).border(1.dp, c.accent.copy(alpha = 0.5f), Oc.round(M.composerRadius)).padding(18.dp),
    ) {
        Txt("Allow ${r.permission}?", size = 16.sp, weight = FontWeight.SemiBold)
        val what = r.patterns.joinToString("\n").ifBlank { r.metadata.str("command") ?: r.metadata.str("filepath") ?: "" }
        if (what.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Txt(what.take(800), size = 13.sp, family = Oc.mono, color = c.textDim, modifier = Modifier.fillMaxWidth().clip(Oc.round(8.dp)).background(c.codeBg).padding(10.dp))
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button("Allow once") { Graph.hub.replyPermission(r, "once") }
            Button("Always allow", primary = false) { Graph.hub.replyPermission(r, "always") }
            Spacer(Modifier.weight(1f))
            Button("Deny", primary = false) { Graph.hub.replyPermission(r, "reject") }
        }
    }
}

/** Claude's question card: numbered answers (keys 1–9), "Something else" with your own words, Skip. Several questions go one by one. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun QuestionPanel(q: QuestionReq) {
    val c = Oc.c
    var step by remember(q.id) { mutableStateOf(0) }
    val answers = remember(q.id) { androidx.compose.runtime.mutableStateListOf<List<String>>() }
    val item = q.questions.getOrNull(step) ?: return
    var own by remember(q.id, step) { mutableStateOf("") }
    var typing by remember(q.id, step) { mutableStateOf(false) }
    val picked = remember(q.id, step) { androidx.compose.runtime.mutableStateListOf<String>() }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    fun answer(a: List<String>) {
        answers.add(a)
        if (answers.size >= q.questions.size) Graph.hub.replyQuestion(q, answers.toList()) else step++
    }
    fun choose(i: Int) {
        val o = item.options.getOrNull(i) ?: return
        if (item.multiple) { if (o.label in picked) picked.remove(o.label) else picked.add(o.label) } else answer(listOf(o.label))
    }
    LaunchedEffect(q.id, step) { kotlinx.coroutines.delay(40); runCatching { focus.requestFocus() } }
    Column(
        Modifier.fillMaxWidth().clip(Oc.round(M.composerRadius)).background(c.surface).border(1.dp, c.border, Oc.round(M.composerRadius))
            .focusRequester(focus).focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || typing) return@onPreviewKeyEvent false
                val n = when (e.key) { Key.One -> 0; Key.Two -> 1; Key.Three -> 2; Key.Four -> 3; Key.Five -> 4; Key.Six -> 5; Key.Seven -> 6; Key.Eight -> 7; Key.Nine -> 8; else -> -1 }
                if (n >= 0) { if (n == item.options.size && item.custom) typing = true else choose(n); true } else false
            }
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.header.isNotBlank()) Txt(item.header.uppercase(), size = 11.sp, color = c.textDim, family = Oc.label, weight = FontWeight.Medium, modifier = Modifier.weight(1f))
            else Spacer(Modifier.weight(1f))
            if (q.questions.size > 1) Txt("${step + 1} of ${q.questions.size}", size = 12.sp, color = c.textDim)
        }
        Txt(item.question, size = 16.sp, weight = FontWeight.SemiBold)
        item.options.forEachIndexed { i, o ->
            val on = o.label in picked
            Row(
                Modifier.fillMaxWidth().clip(Oc.round(10.dp)).border(1.dp, if (on) c.accent else c.border, Oc.round(10.dp))
                    .hoverTap(Oc.round(10.dp), on) { choose(i) }.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(24.dp).clip(Oc.round(6.dp)).background(c.surfaceHigh), contentAlignment = Alignment.Center) {
                    if (on) Icon(Ic.Check, 14.dp, c.accent) else Txt("${i + 1}", size = 13.sp, family = Oc.mono, color = c.textDim)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Txt(o.label, size = 15.sp, weight = FontWeight.Medium)
                    if (o.description.isNotBlank()) Txt(o.description, size = 13.sp, color = c.textDim)
                }
            }
        }
        if (item.custom) {
            if (!typing) Row(
                Modifier.fillMaxWidth().clip(Oc.round(10.dp)).border(1.dp, c.border, Oc.round(10.dp)).hoverTap(Oc.round(10.dp)) { typing = true }.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(24.dp).clip(Oc.round(6.dp)).background(c.surfaceHigh), contentAlignment = Alignment.Center) {
                    Txt("${item.options.size + 1}", size = 13.sp, family = Oc.mono, color = c.textDim)
                }
                Spacer(Modifier.width(12.dp))
                Txt("Something else", size = 15.sp, color = c.textDim)
            } else Field(own, { own = it }, "Type your answer and press Enter", Modifier.fillMaxWidth().clip(Oc.round(10.dp)).border(1.dp, c.accent, Oc.round(10.dp)).padding(12.dp)
                .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.Enter && own.isNotBlank()) { answer(picked.toList() + own.trim()); true } else false }, size = 15)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (item.multiple || own.isNotBlank()) Button("Answer") { answer(picked.toList() + listOfNotNull(own.trim().takeIf { it.isNotEmpty() })) }
            Spacer(Modifier.weight(1f))
            Txt("Esc to skip", size = 12.sp, color = c.textDim)
            Button("Skip", primary = false) { Graph.hub.replyQuestion(q, null) }
        }
    }
}
