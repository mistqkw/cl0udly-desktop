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
import androidx.compose.material3.DropdownMenu
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
fun Menu(open: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val c = Oc.c
    DropdownMenu(
        open, onDismiss,
        modifier = Modifier.background(c.surface).border(1.dp, c.border, Oc.round(12.dp)).padding(horizontal = 6.dp),
        shape = Oc.round(12.dp), containerColor = c.surface, shadowElevation = 6.dp, tonalElevation = 0.dp,
    ) { Column(Modifier.widthIn(min = 220.dp)) { content() } }
}

@Composable
fun MenuItem(ic: Ic?, label: String, hint: String? = null, danger: Boolean = false, checked: Boolean = false, onClick: () -> Unit) {
    val c = Oc.c
    val col = if (danger) c.danger else c.text
    Row(
        Modifier.fillMaxWidth().height(38.dp).hoverTap(Oc.round(8.dp), onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ic != null) { Icon(ic, 17.dp, col); Spacer(Modifier.width(12.dp)) }
        Txt(label, size = 15.sp, color = col, modifier = Modifier.weight(1f), maxLines = 1)
        if (hint != null) Txt(hint, size = 13.sp, color = c.textDim, family = Oc.mono)
        if (checked) Icon(Ic.Check, 16.dp, c.accent)
    }
}

/** The ☰ menu: what Claude desktop puts in its app menu. */
@Composable
fun AppMenu(open: Boolean, onDismiss: () -> Unit) {
    Menu(open, onDismiss) {
        MenuItem(Ic.Plus, "New chat", "Ctrl+N") { onDismiss(); Graph.prefs.mode = "chat"; Nav.go(Screen.Home) }
        MenuItem(Ic.Code, "New session in a folder", "Ctrl+O") { onDismiss(); pickProject() }
        MenuItem(Ic.Search, "Search", "Ctrl+K") { onDismiss(); Nav.search = true }
        MenuItem(Ic.Cloud, "Quick entry", "Ctrl+Shift+Space") { onDismiss(); Nav.quick = true }
        MenuItem(Ic.Sidebar, "Toggle sidebar", "Ctrl+B") { onDismiss(); Graph.prefs.sidebarOpen = !Graph.prefs.sidebarOpen }
        MenuItem(Ic.Settings, "Settings", "Ctrl+,") { onDismiss(); Nav.go(Screen.Settings()) }
        MenuItem(Ic.Info, "About cl0udly") { onDismiss(); Nav.go(Screen.Settings("about")) }
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

/** A folder for Code: the system's folder picker (Swing on Linux works through XWayland too). */
fun pickProject() {
    val ch = javax.swing.JFileChooser(Nav.project ?: System.getProperty("user.home")).apply {
        fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
        dialogTitle = "Open a project folder"
    }
    if (ch.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
        val d = ch.selectedFile.absolutePath
        Graph.prefs.addProject(d); Graph.prefs.mode = "code"; Nav.project = d; Nav.go(Screen.Home)
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
        val all = Graph.hub.sessions
        val found = remember(q, all) {
            if (q.isBlank()) all.take(30) else all.filter { it.displayTitle.contains(q, true) || it.directory.contains(q, true) }.take(30)
        }
        val list = rememberLazyListState()
        LaunchedEffect(sel) { if (found.isNotEmpty()) list.animateScrollToItem(sel.coerceIn(0, found.lastIndex)) }
        fun open(i: Int) { found.getOrNull(i)?.let { Nav.search = false; Nav.go(Screen.Chat(it.id)) } }
        Column(Modifier.width(640.dp).clip(Oc.round(16.dp)).background(c.surface).border(1.dp, c.border, Oc.round(16.dp))) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.Search, 18.dp, c.textDim)
                Spacer(Modifier.width(12.dp))
                Field(q, { q = it; sel = 0 }, "Search chats and sessions", Modifier.weight(1f).focusRequester(focus).onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionDown -> { sel = (sel + 1).coerceAtMost(found.lastIndex.coerceAtLeast(0)); true }
                        Key.DirectionUp -> { sel = (sel - 1).coerceAtLeast(0); true }
                        Key.Enter -> { open(sel); true }
                        Key.Escape -> { Nav.search = false; true }
                        else -> false
                    }
                })
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            LazyColumn(Modifier.heightIn(max = 420.dp).padding(6.dp), state = list) {
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
                if (found.isEmpty()) item { Txt("Nothing found", size = 14.sp, color = c.textDim, modifier = Modifier.padding(16.dp)) }
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
