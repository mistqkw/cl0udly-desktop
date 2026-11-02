package app.cl0udly.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import app.cl0udly.ui.kit.Pop
import app.cl0udly.ui.kit.PopItem
import app.cl0udly.ui.kit.Side
import app.cl0udly.ui.kit.PopDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cl0udly.Graph
import app.cl0udly.ui.kit.Ic
import app.cl0udly.ui.kit.Icon
import app.cl0udly.ui.kit.Txt
import app.cl0udly.ui.kit.hoverTap
import app.cl0udly.ui.theme.Looks
import app.cl0udly.ui.theme.Oc
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun Menu(open: Boolean, onDismiss: () -> Unit, side: Side = Side.Below, content: @Composable () -> Unit) = Pop(open, onDismiss, side) { content() }

@Composable
fun MenuItem(ic: Ic?, label: String, hint: String? = null, danger: Boolean = false, checked: Boolean = false, onClick: () -> Unit) =
    PopItem(label, ic, hint = hint, danger = danger, checked = checked, onClick = onClick)

/** The ☰ menu: Claude desktop's menu bar (File, Edit, View, Go, Help) folded into one button. */
@Composable
fun AppMenu(open: Boolean, onDismiss: () -> Unit) {
    val p = Graph.prefs
    Pop(open, onDismiss, minWidth = 140.dp) {
        PopItem("File", submenu = { close ->
            PopItem("New chat", Ic.Plus, hint = "Ctrl+N") { close(); onDismiss(); p.mode = "chat"; Nav.go(Screen.Home) }
            PopItem("Open folder…", Ic.Folder, hint = "Ctrl+O") { close(); onDismiss(); pickProject() }
            PopItem("Open file…", Ic.File, hint = "Ctrl+Shift+O") { close(); onDismiss(); Pickers.openFile() }
            PopDivider()
            PopItem("Settings", Ic.Settings, hint = "Ctrl+,") { close(); onDismiss(); Nav.settings = "general" }
            PopDivider()
            PopItem("Quit", hint = "Ctrl+Q") { close(); onDismiss(); Nav.quit() }
        })
        PopItem("View", submenu = { close ->
            PopItem("Sidebar", Ic.Sidebar, hint = "Ctrl+B", checked = p.sidebarOpen) { close(); onDismiss(); p.sidebarOpen = !p.sidebarOpen }
            PopItem("Context", Ic.Info, hint = "Ctrl+I", checked = Nav.pane == "context") { close(); onDismiss(); Nav.toggle("context") }
            PopItem("Terminal", Ic.Terminal, hint = "Ctrl+`", checked = Nav.pane == "terminal") { close(); onDismiss(); Nav.toggle("terminal") }
            PopItem("Changes", Ic.Diff, hint = "Ctrl+Shift+D", checked = Nav.pane == "diff") { close(); onDismiss(); Nav.toggle("diff") }
            PopItem("Browser", Ic.Globe, hint = "Ctrl+Shift+B", checked = Nav.pane == "browser") { close(); onDismiss(); Nav.toggle("browser") }
            PopItem("Files", Ic.Folder, hint = "Ctrl+Shift+E", checked = Nav.pane == "files") { close(); onDismiss(); Nav.toggle("files") }
            PopDivider()
            PopItem("Bigger text", hint = "Ctrl+=") { close(); onDismiss(); p.textSize = if (p.textSize == "small") "default" else "large" }
            PopItem("Smaller text", hint = "Ctrl+-") { close(); onDismiss(); p.textSize = if (p.textSize == "large") "default" else "small" }
        })
        PopItem("Go", submenu = { close ->
            PopItem("Back", Ic.Back, hint = "Ctrl+[", enabled = Nav.canBack) { close(); onDismiss(); Nav.back() }
            PopItem("Forward", Ic.Forward, hint = "Ctrl+]", enabled = Nav.canForward) { close(); onDismiss(); Nav.forward() }
            PopItem("Search", Ic.Search, hint = "Ctrl+K") { close(); onDismiss(); Nav.search = true }
            PopItem("Quick entry", Ic.Cloud, hint = "Ctrl+Shift+Space") { close(); onDismiss(); Nav.quick = true }
            PopItem("Projects", Ic.Folder) { close(); onDismiss(); Nav.go(Screen.Projects) }
            PopItem("Usage", Ic.Gauge) { close(); onDismiss(); Nav.settings = "usage" }
        })
        PopItem("Help", submenu = { close ->
            PopItem("Keyboard shortcuts", Ic.Key) { close(); onDismiss(); Nav.settings = "shortcuts" }
            PopItem("Server log", Ic.File) { close(); onDismiss(); app.cl0udly.data.Desktop.open(Graph.runtime.logFile.absolutePath) }
            PopItem("OpenCode docs", Ic.Globe) { close(); onDismiss(); Nav.openUrl("https://opencode.ai/docs") }
            PopItem("About cl0udly", Ic.Info) { close(); onDismiss(); Nav.settings = "about" }
        })
    }
}

@Composable
fun LookMenu(open: Boolean, onDismiss: () -> Unit) {
    Menu(open, onDismiss) {
        Looks.all.forEach { l -> MenuItem(null, l.title, checked = Graph.prefs.edition == l.id) { Graph.prefs.edition = l.id; onDismiss() } }
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(Oc.c.border))
        listOf("system" to "Match system", "dark" to "Dark", "light" to "Light").forEach { (id, t) ->
            MenuItem(null, t, checked = Graph.prefs.colorMode == id) { Graph.prefs.colorMode = id; onDismiss() }
        }
    }
}

/** A dimmed layer with a card in the middle; a click outside or Esc closes it. */
@Composable
fun Scrim(visible: Boolean, onDismiss: () -> Unit, top: Boolean = false, content: @Composable () -> Unit) {
    val c = Oc.c
    AnimatedVisibility(visible, enter = fadeIn(tween(120)), exit = fadeOut(tween(100))) {
        Box(
            Modifier.fillMaxSize().background(c.scrim).clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss)
                .onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { onDismiss(); true } else false },
            contentAlignment = if (top) Alignment.TopCenter else Alignment.Center,
        ) {
            Box(Modifier.padding(top = if (top) 110.dp else 0.dp).clickable(remember { MutableInteractionSource() }, null) { }) { content() }
        }
    }
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, size: Int = 16, singleLine: Boolean = true, mask: Boolean = false) {
    val c = Oc.c
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) Txt(placeholder, size = size.sp, color = c.textDim, maxLines = 1)
        BasicTextField(
            value, onChange, singleLine = singleLine, modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(color = c.text, fontSize = size.sp, fontFamily = Oc.body),
            cursorBrush = SolidColor(c.accent),
            visualTransformation = if (mask) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        )
    }
}

@Composable
fun RenameDialog(current: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val c = Oc.c
    var text by remember { mutableStateOf(current) }
    val focus = remember { FocusRequester() }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(440.dp).clip(Oc.round(16.dp)).background(c.surface).border(1.dp, c.border, Oc.round(16.dp)).padding(20.dp)) {
            Txt("Rename chat", size = 18.sp, weight = FontWeight.SemiBold, family = Oc.heading)
            Spacer(Modifier.height(14.dp))
            Field(text, { text = it }, "Title", Modifier.fillMaxWidth().clip(Oc.round(10.dp)).border(1.dp, c.border, Oc.round(10.dp)).padding(12.dp).focusRequester(focus)
                .onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown && it.key == Key.Enter) { onDone(text.trim()); true } else false })
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                Button("Cancel", primary = false, onClick = onDismiss)
                Button("Save") { onDone(text.trim()) }
            }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
fun Button(label: String, primary: Boolean = true, danger: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Oc.c
    val bg = when { danger -> c.danger; primary -> c.text; else -> c.surfaceHigh }
    val fg = when { danger -> c.bg; primary -> c.bg; else -> c.text }
    Box(
        modifier.clip(Oc.round(10.dp)).background(bg).hoverTap(Oc.round(10.dp), onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) { Txt(label, size = 14.sp, color = fg, weight = FontWeight.Medium) }
}

// ---------- search (Ctrl+K) ----------

@Composable
fun SearchOverlay() {
    val c = Oc.c
    Scrim(Nav.search, { Nav.search = false }, top = true) {
        var q by remember { mutableStateOf("") }
        var sel by remember { mutableIntStateOf(0) }
        val focus = remember { FocusRequester() }
        var tab by remember { mutableStateOf("all") }
        val all = Graph.hub.sessions.filter { s -> when (tab) { "chats" -> Graph.hub.isChat(s); "code" -> !Graph.hub.isChat(s); else -> true } }
        val dirs = (Graph.prefs.projectList + Graph.hub.sessions.filter { !Graph.hub.isChat(it) }.map { it.directory }).distinct()
            .filter { q.isBlank() || it.contains(q, true) }
        val found = remember(q, all, tab) {
            if (tab == "projects") emptyList()
            else if (q.isBlank()) all.take(30) else all.filter { it.displayTitle.contains(q, true) || it.directory.contains(q, true) }.take(30)
        }
        val list = rememberLazyListState()
        LaunchedEffect(sel) { if (found.isNotEmpty()) list.animateScrollToItem(sel.coerceIn(0, found.lastIndex)) }
        fun open(i: Int) {
            if (tab == "projects") dirs.getOrNull(i)?.let { d -> Nav.search = false; Graph.prefs.mode = "code"; Nav.project = d; Graph.prefs.addProject(d); Nav.go(Screen.Home) }
            else found.getOrNull(i)?.let { Nav.search = false; Nav.go(Screen.Chat(it.id)) }
        }
        Column(Modifier.width(640.dp).clip(Oc.round(16.dp)).background(c.surface).border(1.dp, c.border, Oc.round(16.dp))) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.Search, 18.dp, c.textDim)
                Spacer(Modifier.width(12.dp))
                Field(q, { q = it; sel = 0 }, "Search chats and sessions", Modifier.weight(1f).focusRequester(focus).onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionDown -> { sel = (sel + 1).coerceAtMost((if (tab == "projects") dirs.lastIndex else found.lastIndex).coerceAtLeast(0)); true }
                        Key.DirectionUp -> { sel = (sel - 1).coerceAtLeast(0); true }
                        Key.Enter -> { open(sel); true }
                        Key.Escape -> { Nav.search = false; true }
                        else -> false
                    }
                })
            }
            Row(Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("all" to "All", "chats" to "Chats", "code" to "Code", "projects" to "Projects").forEach { (id, l) ->
                    Box(Modifier.hoverTap(Oc.round(8.dp), tab == id) { tab = id; sel = 0 }.padding(horizontal = 11.dp, vertical = 5.dp)) {
                        Txt(l, size = 13.sp, color = if (tab == id) c.text else c.textDim)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            LazyColumn(Modifier.heightIn(max = 420.dp).padding(6.dp), state = list) {
                if (tab == "projects") itemsIndexed(dirs, key = { _, d -> "d:$d" }) { i, d ->
                    Row(
                        Modifier.fillMaxWidth().height(44.dp).hoverTap(Oc.round(10.dp), i == sel) { open(i) }.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Ic.Folder, 17.dp, c.textDim)
                        Spacer(Modifier.width(12.dp))
                        Txt(java.io.File(d).name, size = 15.sp, maxLines = 1)
                        Spacer(Modifier.width(10.dp))
                        Txt(d, size = 13.sp, color = c.textDim, maxLines = 1, modifier = Modifier.weight(1f))
                    }
                }
                itemsIndexed(found, key = { _, s -> s.id }) { i, s ->
                    Row(
                        Modifier.fillMaxWidth().height(44.dp).hoverTap(Oc.round(10.dp), i == sel) { open(i) }.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (Graph.hub.isChat(s)) Ic.Chat else Ic.Code, 17.dp, c.textDim)
                        Spacer(Modifier.width(12.dp))
                        Txt(s.displayTitle, size = 15.sp, maxLines = 1, modifier = Modifier.weight(1f))
                        Txt(ago(s.time.updated), size = 13.sp, color = c.textDim)
                    }
                }
                if (found.isEmpty() && (tab != "projects" || dirs.isEmpty())) item { Txt("Nothing found", size = 14.sp, color = c.textDim, modifier = Modifier.padding(16.dp)) }
            }
        }
        LaunchedEffect(Unit) { delay(30); runCatching { focus.requestFocus() } }
    }
}

fun ago(ms: Long): String {
    if (ms <= 0) return ""
    val m = (System.currentTimeMillis() - ms) / 60_000
    return when {
        m < 1 -> "now"
        m < 60 -> "${m}m"
        m < 60 * 24 -> "${m / 60}h"
        m < 60 * 24 * 30 -> "${m / 60 / 24}d"
        else -> java.text.SimpleDateFormat("d MMM").format(java.util.Date(ms))
    }
}

// ---------- toasts ----------

@Composable
fun Toasts(modifier: Modifier) {
    val c = Oc.c
    val shown = remember { mutableStateListOf<Pair<Long, String>>() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(Unit) {
        Graph.hub.toasts.collect { t ->
            val id = System.nanoTime()
            if (shown.none { it.second == t }) shown.add(id to t)
            scope.launch { delay(4200); shown.removeAll { it.first == id } }
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        shown.forEach { (id, t) ->
            androidx.compose.runtime.key(id) {
                var vis by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { vis = true }
                AnimatedVisibility(vis, enter = fadeIn(tween(120)) + slideInVertically(tween(140)) { it / 2 }, exit = fadeOut(tween(100)) + slideOutVertically { it / 2 }) {
                    Row(
                        Modifier.widthIn(max = 560.dp).clip(Oc.round(12.dp)).background(c.surfaceHigh).border(1.dp, c.border, Oc.round(12.dp)).padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(c.danger))
                        Spacer(Modifier.width(10.dp))
                        Txt(t, size = 14.sp, maxLines = 3)
                    }
                }
            }
        }
    }
}

// ---------- quick entry ----------

/** Claude's Quick Entry: one field on top of everything; Enter starts a new chat with it. */
@Composable
fun QuickEntry() {
    val c = Oc.c
    Scrim(Nav.quick, { Nav.quick = false }, top = true) {
        var q by remember { mutableStateOf("") }
        val focus = remember { FocusRequester() }
        Row(
            Modifier.width(680.dp).clip(Oc.round(18.dp)).background(c.surface).border(1.dp, c.border, Oc.round(18.dp)).padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            app.cl0udly.ui.cloud.MiniCloudIcon(22.dp)
            Spacer(Modifier.width(14.dp))
            Field(q, { q = it }, "Ask cl0udly anything", Modifier.weight(1f).focusRequester(focus).onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.Enter && q.isNotBlank()) {
                    val text = q.trim(); Nav.quick = false; q = ""
                    Graph.scope.launchSend(app.cl0udly.data.CHAT_DIR, text); true
                } else false
            }, size = 18)
        }
        LaunchedEffect(Unit) { delay(30); runCatching { focus.requestFocus() } }
    }
}

fun kotlinx.coroutines.CoroutineScope.launchSend(dir: String, text: String) = launch {
    Graph.hub.startAndSend(dir, text)?.let { Nav.go(Screen.Chat(it)) }
}
