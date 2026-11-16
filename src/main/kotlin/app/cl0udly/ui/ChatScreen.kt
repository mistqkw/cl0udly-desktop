package app.cl0udly.ui

import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import app.cl0udly.data.Helpers
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
    // at the very bottom it follows the reply as it grows (every frame, so the typed-out text too);
    // scrolled up to read, it stays put and a "down" button shows
    var stick by remember(id) { mutableStateOf(true) }
    var auto by remember { mutableStateOf(false) }
    // "at the bottom" is near it, not exactly at it: while a reply grows the end moves away, and an
    // exact check left the chat unstuck after you had scrolled all the way down
    val density = androidx.compose.ui.platform.LocalDensity.current
    fun nearEnd(): Boolean {
        val i = list.layoutInfo
        val last = i.visibleItemsInfo.lastOrNull() ?: return true
        if (last.index < i.totalItemsCount - 1) return false
        return last.offset + last.size - i.viewportEndOffset < with(density) { 160.dp.toPx() }
    }
    LaunchedEffect(id) {
        // near the end counts only on the way down: a small scroll up from the bottom stays where you left it
        snapshotFlow { list.isScrollInProgress }.collect { if (!it && !auto) stick = !list.canScrollForward || (list.lastScrolledForward && nearEnd()) }
    }
    LaunchedEffect(id) {
        while (true) {
            withFrameNanos { }
            if (stick && list.canScrollForward) {
                auto = true
                runCatching {
                    val info = list.layoutInfo
                    val last = info.visibleItemsInfo.lastOrNull()
                    if (last == null || last.index < info.totalItemsCount - 2) list.scrollToItem(info.totalItemsCount - 1, Int.MAX_VALUE / 2)
                    else list.scrollBy(maxOf(2f, (last.offset + last.size - info.viewportEndOffset) * 0.4f))
                }
                auto = false
            }
        }
    }
    // the chat's own requests first, then its helpers' (they would wait unseen otherwise)
    val perm = hub.permissions.firstOrNull { it.sessionID == id } ?: hub.permissions.firstOrNull { it.sessionID != id && hub.rootOf(it.sessionID) == id }
    val question = hub.questions.firstOrNull { it.sessionID == id }
    val links = remember {
        object : androidx.compose.ui.platform.UriHandler {
            override fun openUri(uri: String) {
                val path = localPath(uri) ?: uri.takeIf { it.startsWith("/") || it.startsWith("~/") }?.replaceFirst("~", System.getProperty("user.home"))
                when {
                    path != null && java.io.File(path).isFile -> if (Images.isImageSrc(path)) Lightbox.src = path else FileView.open(path)
                    path != null && java.io.File(path).isDirectory -> app.cl0udly.data.Desktop.open(path)
                    path != null -> hub.toasts.tryEmit("File not found: $path")
                    else -> Nav.openUrl(uri)
                }
            }
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalUriHandler provides links) {
    BoxWithConstraints(Modifier.fillMaxSize().fileDrop()) {
        val col = minOf(if (Graph.prefs.transcriptWidth == "wide") 1240.dp else M.column, maxWidth - 64.dp)
        // the messages run under the input, which floats over them half see-through: no edge the text
        // stops at, and the padding keeps the last line clear of it
        val dens = androidx.compose.ui.platform.LocalDensity.current
        var bottomPad by remember { mutableStateOf(0.dp) }
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                SelectionContainer {
                    LazyColumn(Modifier.fillMaxSize(), state = list, horizontalAlignment = Alignment.CenterHorizontally) {
                        item { Spacer(Modifier.height(32.dp)) }
                        items(chat.messages, key = { it.id }) { m ->
                            Box(Modifier.width(col).animateItem(fadeInSpec = tween(140), fadeOutSpec = null)) {
                                if (m.info.isUser) UserMessage(m) else AssistantMessage(m)
                            }
                        }
                        if (busy) item(key = "working") { Box(Modifier.width(col).padding(top = 6.dp, bottom = 10.dp)) { Working(chat.messages.lastOrNull(), id) } }
                        // right after a reply ends the mascot reacts for a moment, by its eyes (as on the phone)
                        item(key = "outcome") { Box(Modifier.width(col)) { OutcomeNote(id, busy) } }
                        item { Spacer(Modifier.height(24.dp + bottomPad)) }
                    }
                }
                val scope = rememberCoroutineScope()
                androidx.compose.animation.AnimatedVisibility(
                    !stick, Modifier.align(Alignment.BottomCenter).padding(bottom = bottomPad + 10.dp),
                    enter = androidx.compose.animation.fadeIn(tween(140)) + androidx.compose.animation.scaleIn(tween(140), initialScale = 0.8f),
                    exit = androidx.compose.animation.fadeOut(tween(140)) + androidx.compose.animation.scaleOut(tween(140), targetScale = 0.8f),
                ) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(c.surfaceHigh).border(1.dp, c.border, CircleShape)
                            .hoverTap(CircleShape) { stick = true; scope.launch { auto = true; list.animateScrollToItem(maxOf(0, list.layoutInfo.totalItemsCount - 1), Int.MAX_VALUE / 2); auto = false } },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Ic.ArrowDown, 18.dp, c.text) }
                }
                if (chat.loading && chat.messages.isEmpty()) Txt("Loading…", color = c.textDim, modifier = Modifier.align(Alignment.Center))
                chat.loadError?.let { Txt(it, color = c.danger, modifier = Modifier.align(Alignment.Center)) }
                androidx.compose.foundation.VerticalScrollbar(
                    androidx.compose.foundation.rememberScrollbarAdapter(list),
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 3.dp, top = 4.dp, bottom = bottomPad + 4.dp).width(8.dp),
                    style = androidx.compose.foundation.defaultScrollbarStyle().copy(unhoverColor = c.border, hoverColor = c.textDim, thickness = 6.dp),
                )
            }
            // the top darkens the messages that scroll under it, fading out instead of a hard edge
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().height(44.dp)
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(0f to c.bg.copy(alpha = 0.92f), 1f to c.bg.copy(alpha = 0f))),
            )
            Column(Modifier.align(Alignment.BottomCenter).width(col + M.composerExtra * 2).onSizeChanged { bottomPad = with(dens) { it.height.toDp() } }.padding(bottom = 14.dp, top = 6.dp)) {
                HelperClouds(id)
                when {
                    perm != null && perm.permission == "task" -> TaskPermissionPanel(perm)
                    perm != null -> {
                        val agent = if (perm.sessionID != id) hub.session(perm.sessionID)?.agent ?: hub.session(perm.sessionID)?.title?.substringAfter("(@")?.substringBefore(" ") else null
                        PermissionPanel(perm, agent?.let(Helpers::label) ?: if (perm.sessionID != id) Helpers.label(null) else null, agent.orEmpty())
                    }
                    question != null -> QuestionPanel(question)
                    else -> Composer(big = false, busy = busy, onSend = { t, files -> Graph.scope.launchIt { hub.send(id, t, files) } }, onStop = { hub.stop(id) }, placeholder = if (busy) "Add to the queue…" else "Reply to cl0udly", draftKey = id, floating = true)
                }
            }
        }
    }
    }
}

fun kotlinx.coroutines.CoroutineScope.launchIt(block: suspend () -> Unit) = launch { block() }

/** Your message: pictures and files first (like Claude), the text bubble under them. */
@Composable
private fun UserMessage(m: ChatMessage) = Column(Modifier.fillMaxWidth()) { UserMessageBody(m) }

@Composable
private fun UserMessageBody(m: ChatMessage) {
    val c = Oc.c
    val text = m.parts.filter { it.type == "text" && it.raw["synthetic"]?.let { s -> (s as? JsonPrimitive)?.contentOrNull } != "true" }.joinToString("\n") { it.text }
    val files = m.parts.filter { it.type == "file" }
    val pics = files.mapNotNull { f -> f.raw.str("url")?.takeIf { Images.isImageSrc(it, f.raw.str("mime")) } }
    val others = files.filter { f -> f.raw.str("url")?.let { Images.isImageSrc(it, f.raw.str("mime")) } != true }
    if (files.isNotEmpty()) Column(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalAlignment = if (Oc.look.user == "plain") Alignment.Start else Alignment.End) {
        ImageStrip(pics)
        if (others.isNotEmpty()) Row(Modifier.padding(top = if (pics.isEmpty()) 0.dp else 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            others.forEach { f -> FileChip(f.raw.str("filename") ?: "file", f.raw.str("mime") == "application/x-directory", localPath(f.raw.str("url"))) }
        }
    }
    if (text.isBlank() && files.isNotEmpty()) { Spacer(Modifier.height(22.dp)); return }
    when (Oc.look.user) {
        "plain" -> Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
            Txt("You", size = 13.sp, color = c.accent, weight = FontWeight.SemiBold, family = Oc.label)
            Spacer(Modifier.height(6.dp))
            Txt(text, size = M.body, lineHeight = M.bodyLine, raw = true)
        }
        else -> Box(Modifier.fillMaxWidth().padding(top = if (files.isEmpty()) 20.dp else 8.dp, bottom = 22.dp), contentAlignment = Alignment.CenterEnd) {
            val shape = Oc.round(M.bubbleRadius)
            val line = Oc.look.user == "line"
            Box(
                Modifier.widthIn(max = M.bubbleMax).clip(shape)
                    .then(if (line) Modifier.border(1.dp, c.border, shape) else Modifier.background(c.userBubble))
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) { Txt(text, size = M.body, lineHeight = M.bodyLine, raw = true) }
        }
    }
}

/** One reply: steps (thoughts and tools) fold into a line, the text is markdown, actions show on hover. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun AssistantMessage(m: ChatMessage) {
    val c = Oc.c
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val groups = remember(m.parts.toList()) { group(m.parts) }
    val live = m.info.time.completed == null
    Column(Modifier.fillMaxWidth().hoverable(hover).padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(M.paragraphGap)) {
        groups.forEach { g ->
            when (g) {
                // a live reply is typed out word by word and the block grows smoothly, like Claude's
                is Group.Text -> if (live) Box(Modifier.animateContentSize(androidx.compose.animation.core.spring(stiffness = 900f))) { Markdown(smoothText(g.text, true)) }
                    else Markdown(g.text)
                is Group.Steps -> Steps(g.parts)
                is Group.Remember -> RememberCard(g.part)
            }
        }
        m.info.errorMessage?.takeIf { m.info.errorName != "MessageAbortedError" }?.let {
            if (app.cl0udly.data.Limits.isLimit(m.info.errorName, it)) LimitCard(it, m.info.sessionID)
            else ErrorCard(m.info.errorName, it, m.info.sessionID)
        }
        val text = groups.filterIsInstance<Group.Text>().joinToString("\n\n") { it.text }
        val pics = remember(text, m.parts.size) { picturesIn(m, text) }
        val docs = remember(text, m.parts.size, m.info.time.completed) { if (m.info.time.completed == null) emptyList() else filesIn(m, text) }
        // pictures, videos and sounds as small square tiles in a row, other files as cards (as on the phone)
        val media = docs.filter { isMedia(it) }
        if (pics.isNotEmpty() || media.isNotEmpty()) androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            pics.forEach { ImageTile(it) }
            media.forEach { MediaTile(it) }
        }
        val rest = docs.filterNot { isMedia(it) }
        if (rest.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { rest.forEach { FileCard(it) } }
        if (text.isNotBlank() && m.info.time.completed != null) Row(
            Modifier.graphicsLayer { alpha = if (hovered) 1f else 0f }.height(30.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var copied by remember { mutableStateOf(false) }
            Box(Modifier.size(30.dp).hoverTap(Oc.round(8.dp)) { copyText(text); copied = true }, contentAlignment = Alignment.Center) { Icon(if (copied) Ic.Check else Ic.Copy, 16.dp, c.textDim) }
            // read aloud, as on the phone; the same button stops it
            val reading = app.cl0udly.data.Speech.reading == text
            Box(Modifier.size(30.dp).hoverTap(Oc.round(8.dp), reading) { app.cl0udly.data.Speech.toggle(text) }, contentAlignment = Alignment.Center) { Icon(if (reading) Ic.Stop else Ic.Volume, 16.dp, c.textDim) }
            Spacer(Modifier.width(8.dp))
            Txt(m.info.modelName.orEmpty(), size = 13.sp, color = c.textDim)
        }
    }
}

/** A sent file or folder under the message: an icon and its name. */
@Composable
private fun FileChip(name: String, folder: Boolean, path: String?) {
    val c = Oc.c
    val open = path?.let { p -> { if (folder) app.cl0udly.data.Desktop.open(p) else FileView.open(p) } }
    Row(
        Modifier.height(40.dp).clip(Oc.round(10.dp)).border(1.dp, c.border, Oc.round(10.dp))
            .then(if (open != null) Modifier.hoverTap(Oc.round(10.dp), onClick = open) else Modifier).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (folder) Ic.Folder else iconFor(name), 15.dp, c.textDim)
        Spacer(Modifier.width(8.dp))
        Txt(name, size = 13.sp, maxLines = 1, modifier = Modifier.widthIn(max = 220.dp))
    }
}

/** A file part's address as a path on this computer (file:// only). */
private fun localPath(url: String?): String? = url?.takeIf { it.startsWith("file:") }?.let { runCatching { java.io.File(java.net.URI(it)).path }.getOrNull() }

/**
 * Files the agent points to (not pictures, those show above): its own file parts and absolute paths in
 * the text — a link `[name](/path)`, `/path` in backticks or alone on a line — that exist on this computer.
 */
private fun filesIn(m: ChatMessage, text: String): List<String> {
    val out = LinkedHashSet<String>()
    m.parts.filter { it.type == "file" }.forEach { f -> localPath(f.raw.str("url"))?.takeIf { !Images.isImageSrc(it, f.raw.str("mime")) }?.let(out::add) }
    listOf(Regex("""]\(((?:/|~/|file:)[^)\s]+)\)"""), Regex("""`((?:/|~/)[^`\s]+)`"""), Regex("""(?m)^\s*((?:/|~/)[^\s`*]+)\s*$""")).forEach { re ->
        re.findAll(text).forEach { out.add(it.groupValues[1].trimEnd('.', ',', ';', ':').let { p -> localPath(p) ?: p.replaceFirst("~", System.getProperty("user.home")) }) }
    }
    // what it made with its write tool: documents, media, archives (not the code it edits)
    m.parts.filter { it.type == "tool" && it.raw.str("tool") == "write" }.forEach { p ->
        p.raw.obj("state")?.obj("input")?.str("filePath")?.takeIf { it.substringAfterLast('.').lowercase() in HANDOUT && !it.substringAfterLast('/').startsWith("README", true) }?.let(out::add)
    }
    return out.filter { val f = java.io.File(it); f.isFile && !Images.isImageSrc(it) }.take(8)
}

private val HANDOUT = setOf(
    "pdf", "mp3", "wav", "ogg", "flac", "m4a", "mp4", "webm", "mov", "zip", "tar", "gz", "7z", "apk",
    "docx", "xlsx", "pptx", "odt", "ods", "csv", "epub", "html", "txt", "md",
)

/**
 * Pictures the agent shows: its own file parts, Markdown images, and paths to picture files it
 * mentions (it saved a picture and named it). Only files that exist on this computer.
 */
private fun picturesIn(m: ChatMessage, text: String): List<String> {
    val out = LinkedHashSet<String>()
    m.parts.filter { it.type == "file" }.forEach { f -> f.raw.str("url")?.takeIf { Images.isImageSrc(it, f.raw.str("mime")) }?.let(out::add) }
    Regex("""!\[[^\]]*]\(([^)\s]+)\)""").findAll(text).forEach { out.add(it.groupValues[1].removePrefix("file://")) }
    Regex("""(?<![\w/.])((?:/|~/)[^\s`'"()<>\[\]]+\.(?:png|jpe?g|gif|webp|bmp))""", RegexOption.IGNORE_CASE).findAll(text).forEach {
        out.add(it.groupValues[1].replaceFirst("~", System.getProperty("user.home")))
    }
    return out.filter { it.startsWith("data:") || java.io.File(it).isFile }.take(8)
}

private sealed interface Group {
    data class Text(val text: String) : Group
    data class Steps(val parts: List<Part>) : Group
    data class Remember(val part: Part) : Group
}

private fun group(parts: List<Part>): List<Group> {
    val out = ArrayList<Group>()
    val steps = ArrayList<Part>()
    fun flush() { if (steps.isNotEmpty()) { out.add(Group.Steps(steps.toList())); steps.clear() } }
    parts.forEach { p ->
        when (p.type) {
            "text" -> if (p.text.isNotBlank()) { flush(); out.add(Group.Text(p.text)) }
            "reasoning" -> if (p.text.isNotBlank()) steps.add(p)
            "tool" -> if (p.raw.str("tool") == "remember") { flush(); out.add(Group.Remember(p)) } else steps.add(p)
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
private fun OutcomeNote(id: String, busy: Boolean) {
    val c = Oc.c
    val outcome = app.cl0udly.ui.cloud.MascotSignals.outcome
    var reacting by remember { mutableStateOf(false) }
    LaunchedEffect(outcome) { if (outcome != null && System.currentTimeMillis() - outcome.at < 1000) { reacting = true; delay(1800); reacting = false } }
    androidx.compose.animation.AnimatedVisibility(
        !busy && reacting && outcome != null && outcome.sessionId == id && Oc.look.clouds,
        enter = androidx.compose.animation.fadeIn(tween(120)), exit = androidx.compose.animation.fadeOut(tween(160)),
    ) {
        var last by remember { mutableStateOf(outcome) }
        if (outcome != null) last = outcome
        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            app.cl0udly.ui.cloud.CloudOutcomeGlyph(last?.ok == true, 28.dp)
            Spacer(Modifier.width(10.dp))
            Txt(if (last?.ok == true) "Done" else "That didn't work", size = 14.sp, color = c.textDim)
        }
    }
}

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
internal fun PermissionPanel(r: PermissionReq, helper: String? = null, helperKey: String = "") {
    val c = Oc.c
    Column(
        Modifier.fillMaxWidth().clip(Oc.round(M.composerRadius)).background(c.surface).border(1.dp, c.accent.copy(alpha = 0.5f), Oc.round(M.composerRadius)).padding(18.dp),
    ) {
        if (helper != null) Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            HelperGlyph("asking", 22.dp, helperKey); Spacer(Modifier.width(8.dp)); Txt("$helper asks", size = 13.sp, color = c.accent)
        }
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

/**
 * The reply as it should look right now: new text comes out at a steady pace (faster when it falls
 * behind, so it catches up within ~0.15 s) instead of in the server's lumps, and words come out whole,
 * so a half-typed word never jumps to the next line. The phone's copy works the same way.
 */
@Composable
private fun smoothText(target: String, live: Boolean): String {
    val latest = rememberUpdatedState(target)
    var shown by remember { mutableIntStateOf(if (live) 0 else target.length) }
    LaunchedEffect(Unit) {
        var acc = 0f
        var lastT = 0L
        while (true) {
            if (shown >= latest.value.length) {
                if (shown > latest.value.length) shown = latest.value.length
                snapshotFlow { latest.value.length }.first { it > shown }
                lastT = 0L; acc = 0f
            }
            val t = withFrameNanos { it }
            val dt = if (lastT == 0L) 1f / 60 else ((t - lastT) / 1e9f).coerceAtMost(0.05f)
            lastT = t
            acc += maxOf(260f, (latest.value.length - shown) / 0.15f) * dt
            val n = acc.toInt()
            if (n > 0) { acc -= n; shown = minOf(latest.value.length, shown + n) }
        }
    }
    val words by remember {
        derivedStateOf {
            val t = latest.value
            if (shown >= t.length) t else {
                var e = shown
                while (e < t.length && !t[e].isWhitespace()) e++
                t.substring(0, e)
            }
        }
    }
    return words
}

/**
 * The model's allowance ran out: not a red error but what happened and a way on: another free
 * model, with the last message sent to it again.
 */
/** What went wrong in plain words, a way on, and the raw message under "Details" (as on the phone). */
@Composable
private fun ErrorCard(name: String?, message: String, sessionId: String) {
    val c = Oc.c
    val m = message.lowercase()
    val (title, hint) = when {
        name == "ProviderAuthError" || m.contains("unauthorized") || m.contains("api key") || m.contains("401") ->
            "The model's service didn't accept the sign-in" to "Sign in again in Settings, or pick a model that needs no account."
        m.contains("context") && (m.contains("length") || m.contains("too long") || m.contains("maximum") || m.contains("window")) ->
            "This chat got too long for the model" to "Type /compact to free up room, or switch to a model with a bigger memory."
        m.contains("function.name") || m.contains("expected '") || m.contains("tool call") || m.contains("invalid json") ->
            "The model sent a broken step" to "It happens with some free models. Try again, or pick another model."
        m.contains("timeout") || m.contains("timed out") || m.contains("network") || m.contains("fetch failed") || m.contains("econn") || m.contains("unable to connect") ->
            "Couldn't reach the model's server" to "Check the connection and try again."
        m.contains("overloaded") || m.contains("503") || m.contains("502") || m.contains("unavailable") ->
            "The model's server is busy right now" to "Try again in a moment, or pick another model."
        else -> "Something went wrong with this reply" to "Try again, or pick another model."
    }
    var details by remember { mutableStateOf(false) }
    Column(Modifier.widthIn(max = 560.dp).clip(Oc.round(12.dp)).background(c.surface).border(1.dp, c.danger.copy(alpha = 0.3f), Oc.round(12.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.Info, 16.dp, c.danger); Spacer(Modifier.width(10.dp))
            Txt(title, size = 14.sp, weight = FontWeight.Medium)
        }
        Txt(hint, size = 13.sp, color = c.textDim, modifier = Modifier.padding(top = 6.dp))
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(Oc.round(8.dp)).background(c.text).hoverTap(Oc.round(8.dp)) { Graph.hub.retryLast(sessionId) }.padding(horizontal = 14.dp, vertical = 7.dp)) {
                Txt("Try again", size = 13.sp, color = c.bg, weight = FontWeight.Medium)
            }
            Spacer(Modifier.width(14.dp))
            Txt(if (details) "Hide details" else "Details", size = 13.sp, color = c.textDim, modifier = Modifier.hoverTap(Oc.round(6.dp)) { details = !details }.padding(4.dp))
        }
        if (details) Txt(message, size = 12.sp, color = c.textDim, family = Oc.mono, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun LimitCard(message: String, sessionId: String) {
    val c = Oc.c
    val scope = rememberCoroutineScope()
    val next = remember { app.cl0udly.data.Limits.nextFree() }
    var details by remember { mutableStateOf(false) }
    Column(Modifier.widthIn(max = 560.dp).clip(Oc.round(12.dp)).background(c.surface).border(1.dp, c.border, Oc.round(12.dp)).padding(16.dp)) {
        Txt("This model's limit is used up for now", size = 15.sp, weight = FontWeight.Medium)
        Txt("Free models have their own daily allowance on OpenCode's side. Another model usually still works, and this one comes back later.", size = 14.sp, color = c.textDim, modifier = Modifier.padding(top = 6.dp))
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (next != null) Button("Go on with ${next.label}") {
                val p = Graph.prefs
                p.providerId = next.provider.id; p.modelId = next.model.id; p.variant = ""
                val last = Graph.hub.chat(sessionId).messages.lastOrNull { it.info.isUser }
                val text = last?.parts?.filter { it.type == "text" && it.raw.str("synthetic") != "true" }?.joinToString("\n") { it.text }.orEmpty()
                if (text.isNotBlank()) scope.launch { Graph.hub.send(sessionId, text) }
            }
            Spacer(Modifier.width(12.dp))
            Txt(if (details) "Hide details" else "Details", size = 13.sp, color = c.textDim, modifier = Modifier.hoverTap(Oc.round(6.dp)) { details = !details }.padding(4.dp))
        }
        if (details) Txt(message, size = 13.sp, color = c.textDim, modifier = Modifier.padding(top = 8.dp))
    }
}

