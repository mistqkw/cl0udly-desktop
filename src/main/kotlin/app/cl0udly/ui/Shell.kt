package app.cl0udly.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowScope
import androidx.compose.foundation.window.WindowDraggableArea
import app.cl0udly.Graph
import app.cl0udly.api.Session
import app.cl0udly.data.CHAT_DIR
import app.cl0udly.data.Desktop
import app.cl0udly.runtime.RuntimeState
import app.cl0udly.ui.cloud.GhostCloud
import app.cl0udly.ui.cloud.MiniCloudIcon
import app.cl0udly.ui.kit.ContextArea
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
import app.cl0udly.ui.theme.Looks
import app.cl0udly.ui.theme.Oc
import java.awt.Frame
import java.io.File

/** A rename asked from a menu: one dialog for the whole window. */
private object Renaming { var id by mutableStateOf<String?>(null) }

/** Title bar + sidebar + the work area + the panel on the right: the frame of Claude desktop. */
@Composable
fun WindowScope.Shell(onClose: () -> Unit) {
    val c = Oc.c
    val prefs = Graph.prefs
    val open = prefs.sidebarOpen
    val w by animateDpAsState(if (open) M.sidebar else 0.dp, tween(140), label = "sidebar")
    Row(Modifier.fillMaxSize().background(c.bg)) {
        if (w > 0.dp) {
            Column(Modifier.width(w).fillMaxHeight().background(c.sidebar)) {
                DragArea(Modifier.fillMaxWidth().height(M.titleBar)) { TitleLeft(true) }
                Sidebar()
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(c.border.copy(alpha = 0.6f)))
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            DragArea(Modifier.fillMaxWidth().height(M.titleBar)) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    if (!open) TitleLeft(false)
                    TitleCenter(Modifier.weight(1f))
                    TitleRight(onClose)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border.copy(alpha = 0.55f)))
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    AnimatedContent(
                        Nav.screen, label = "screen",
                        // the new screen rises a little out of the old one as it fades in, as on the phone
                        transitionSpec = {
                            (fadeIn(tween(150)) + androidx.compose.animation.scaleIn(tween(190, easing = androidx.compose.animation.core.FastOutSlowInEasing), initialScale = 0.97f)) togetherWith
                                (fadeOut(tween(100)) + androidx.compose.animation.scaleOut(tween(150), targetScale = 1.02f))
                        },
                        contentKey = { it::class },
                    ) { s ->
                        when (s) {
                            Screen.Home -> Home()
                            is Screen.Chat -> ChatScreen(s.id)
                            Screen.Projects -> ProjectsScreen()
                            Screen.Memory -> MemoryScreen()
                        }
                    }
                    ServerBanner(Modifier.align(Alignment.TopCenter).padding(top = 10.dp))
                    Toasts(Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
                }
                PaneHost()
            }
        }
    }
    Renaming.id?.let { id ->
        val s = Graph.hub.session(id)
        RenameDialog(s?.displayTitle.orEmpty(), { Renaming.id = null }) { Graph.hub.rename(id, it); Renaming.id = null }
    }
}

@Composable
private fun WindowScope.DragArea(modifier: Modifier, content: @Composable () -> Unit) {
    WindowDraggableArea(modifier) { content() }
}

/** Menu, sidebar, back, forward, then the Chat / Code switch at the sidebar's right edge. */
@Composable
private fun TitleLeft(inSidebar: Boolean) {
    val prefs = Graph.prefs
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.then(if (inSidebar) Modifier.fillMaxSize() else Modifier).padding(start = 17.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            IconButton(Ic.Menu, { menu = true })
            AppMenu(menu) { menu = false }
        }
        Spacer(Modifier.width(10.dp))
        IconButton(Ic.Sidebar, { prefs.sidebarOpen = !prefs.sidebarOpen })
        Spacer(Modifier.width(10.dp))
        IconButton(Ic.Back, { Nav.back() }, tint = if (Nav.canBack) Oc.c.textDim else Oc.c.textDim.copy(alpha = 0.35f))
        Spacer(Modifier.width(6.dp))
        IconButton(Ic.Forward, { Nav.forward() }, tint = if (Nav.canForward) Oc.c.textDim else Oc.c.textDim.copy(alpha = 0.35f))
        if (inSidebar) Spacer(Modifier.weight(1f)) else Spacer(Modifier.width(16.dp))
        ModeSwitch()
    }
}

/** Chat and Code, two square buttons in a shared tray; the chosen one is filled. */
@Composable
fun ModeSwitch() {
    val c = Oc.c
    val prefs = Graph.prefs
    Row(Modifier.clip(Oc.round(10.dp)).background(c.surface.copy(alpha = 0.6f)).padding(2.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        listOf("chat" to Ic.Chat, "code" to Ic.Code).forEach { (m, ic) ->
            IconButton(ic, {
                if (prefs.mode != m) { prefs.mode = m; Nav.go(Screen.Home) }
            }, size = 36.dp, tint = if (prefs.mode == m) c.text else c.textDim, selected = prefs.mode == m)
        }
    }
}

@Composable
private fun TitleCenter(modifier: Modifier) {
    val s = Nav.screen
    Row(modifier.padding(start = 26.dp), verticalAlignment = Alignment.CenterVertically) {
        when (s) {
            is Screen.Chat -> Graph.hub.session(s.id)?.let { SessionTitle(it) }
            Screen.Projects -> Txt("Projects", size = M.navText)
            Screen.Memory -> Txt("Memory", size = M.navText)
            Screen.Home -> if (Graph.prefs.mode == "code" && Nav.project != null) Txt(File(Nav.project!!).name, size = M.navText)
        }
    }
}

/** The menu shared by the session title and a right click on the session in the sidebar. */
@Composable
fun SessionMenu(s: Session, close: () -> Unit, inTitle: Boolean = false) {
    val hub = Graph.hub
    val prefs = Graph.prefs
    val pinned = s.id in prefs.pinnedList
    if (!inTitle) PopItem("Open", Ic.Chat) { close(); Nav.go(Screen.Chat(s.id)) }
    PopItem(if (pinned) "Unpin" else "Pin", Ic.Pin) { close(); prefs.togglePin(s.id) }
    PopItem("Rename", Ic.Edit) { close(); Renaming.id = s.id }
    if (prefs.memoryOn) PopItem(if (prefs.memoryOn(s.id)) "Turn off memory here" else "Turn on memory here", Ic.Bulb) {
        close(); prefs.setChatMemory(s.id, !prefs.memoryOn(s.id))
        Graph.hub.toasts.tryEmit(if (prefs.memoryOn(s.id)) "Memory is on in this chat" else "Memory is off in this chat: nothing is read or saved")
    }
    PopItem("Timeline", Ic.Layers, sub = if (inTitle) "Who did what and when, with helpers and cost" else null) { close(); TimelineUi.id = s.id }
    PopItem("Fork", Ic.Fork, sub = if (inTitle) "A copy of this conversation to try another way" else null) { close(); hub.fork(s.id) }
    if (inTitle) {
        PopItem("Transcript view", Ic.Layers, value = prefs.transcriptView.replaceFirstChar { it.uppercase() }, submenu = { c2 ->
            listOf("normal" to "Normal", "thinking" to "Show thinking", "verbose" to "Verbose").forEach { (id, l) ->
                PopItem(l, checked = prefs.transcriptView == id) { c2(); close(); prefs.transcriptView = id }
            }
        })
        PopItem("Compact", Ic.Layers, sub = "Summarize to free up the context") { close(); hub.compact(s.id) }
        PopItem("Export as Markdown", Ic.Download) {
            close()
            Pickers.folder("Save the transcript into…") { d ->
                val f = File(d, s.displayTitle.replace(Regex("[^\\p{L}\\p{N} ._-]"), "").take(60).ifBlank { s.id } + ".md")
                runCatching { f.writeText(hub.exportMarkdown(s.id)) }.onSuccess { hub.toasts.tryEmit("Saved ${f.path}") }
            }
        }
    }
    if (s.directory != CHAT_DIR) PopItem("Open folder", Ic.Folder) { close(); Desktop.open(s.directory) }
    PopItem("Copy session ID", Ic.Copy) { close(); copy(s.id) }
    PopDivider()
    PopItem("Archive", Ic.Archive) { close(); hub.archive(s.id); if ((Nav.screen as? Screen.Chat)?.id == s.id) Nav.go(Screen.Home) }
    PopItem("Delete", Ic.Trash, danger = true) { close(); hub.delete(s.id); if ((Nav.screen as? Screen.Chat)?.id == s.id) Nav.go(Screen.Home) }
}

@Composable
private fun SessionTitle(s: Session) {
    var menu by remember { mutableStateOf(false) }
    Box {
        // no title over the chat, only "…": rename, pin, memory, fork, compact, archive and delete are in it
        IconButton(Ic.More, { menu = true }, selected = menu)
        Pop(menu, { menu = false }, minWidth = 250.dp) { SessionMenu(s, { menu = false }, inTitle = true) }
    }
}

/** Remote Control in the title bar: the phone icon, with an accent dot while the phone may connect. */
@Composable
private fun RemoteButton() {
    val c = Oc.c
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(Ic.Phone, { open = true }, selected = open, tint = if (Graph.prefs.remoteControl) c.text else c.textDim)
        if (Graph.prefs.remoteControl) Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(7.dp).clip(CircleShape).background(c.accent))
        Pop(open, { open = false }, width = 460.dp) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt("Remote Control", size = 16.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    RemoteSwitch()
                }
                Spacer(Modifier.height(10.dp))
                RemoteBody()
            }
        }
    }
}

/** The panels' buttons (Claude desktop has them at the right of the title bar), the look, and the window buttons. */
@Composable
private fun WindowScope.TitleRight(onClose: () -> Unit) {
    var looks by remember { mutableStateOf(false) }
    Row(Modifier.padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Panes.all.forEach { (id, ic, _) -> IconButton(ic, { Nav.toggle(id) }, selected = Nav.pane == id, tint = if (Nav.pane == id) Oc.c.text else Oc.c.textDim) }
        Box(Modifier.padding(horizontal = 8.dp).width(1.dp).height(18.dp).background(Oc.c.border))
        RemoteButton()
        Box {
            IconButton(Ic.Palette, { looks = true })
            LookMenu(looks) { looks = false }
        }
        Spacer(Modifier.width(10.dp))
        val frame = window as Frame
        IconButton(Ic.Minus, { frame.extendedState = frame.extendedState or Frame.ICONIFIED }, size = 28.dp, icon = 15.dp)
        IconButton(Ic.Square, {
            frame.extendedState = if ((frame.extendedState and Frame.MAXIMIZED_BOTH) != 0) Frame.NORMAL else Frame.MAXIMIZED_BOTH
        }, size = 28.dp, icon = 13.dp)
        IconButton(Ic.Close, onClose, size = 28.dp, icon = 15.dp)
    }
}

// ---------- sidebar ----------

@Composable
private fun Sidebar() {
    val prefs = Graph.prefs
    val hub = Graph.hub
    var more by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = M.listInset).padding(top = 8.dp)) {
            NavRow(Ic.Plus, if (prefs.mode == "code") "New session" else "New chat", hint = "Ctrl+N") { Nav.go(Screen.Home) }
            // in the order chosen in Settings → Appearance → Sidebar
            prefs.sidebarRows.filter { prefs.sidebarShows(it) }.forEach { k ->
                when (k) {
                    "search" -> NavRow(Ic.Search, "Search", hint = "Ctrl+K") { Nav.search = true }
                    "projects" -> NavRow(Ic.Folder, "Projects", selected = Nav.screen == Screen.Projects) { Nav.go(Screen.Projects) }
                    "memory" -> NavRow(MEMORY_ICON, "Memory", selected = Nav.screen == Screen.Memory) { Nav.go(Screen.Memory) }
                    "customize" -> NavRow(Ic.Sliders, "Customize") { Nav.settings = "capabilities" }
                }
            }
            Box {
                NavRow(Ic.More, "More") { more = true }
                Pop(more, { more = false }, Side.Right, minWidth = 240.dp) {
                    PopItem("Usage", Ic.Gauge) { more = false; Nav.settings = "usage" }
                    PopItem("Models", Ic.Box) { more = false; Nav.settings = "models" }
                    PopItem("Your instructions", Ic.Edit) { more = false; Nav.settings = "instructions" }
                    PopItem("Archived", Ic.Archive, sub = "${hub.archived.size} sessions") { more = false; Nav.search = true }
                    PopDivider()
                    PopItem("Terminal", Ic.Terminal, hint = "Ctrl+`") { more = false; Nav.pane = "terminal" }
                    PopItem("Browser", Ic.Globe, hint = "Ctrl+Shift+B") { more = false; Nav.pane = "browser" }
                    PopItem("Keyboard shortcuts", Ic.Key) { more = false; Nav.settings = "shortcuts" }
                }
            }
        }
        val list = if (prefs.mode == "code") hub.sessions.filter { !hub.isChat(it) } else hub.sessions.filter { hub.isChat(it) }
        val pinned = prefs.pinnedList.mapNotNull { hub.session(it) }.filter { it.time.archived == null }
        Column(Modifier.weight(1f)) {
            if (pinned.isNotEmpty() && prefs.sidebarShows("pinned")) PinnedList(pinned)
            if (prefs.mode == "code") ProjectList(list.filter { it.id !in prefs.pinnedList }, Modifier.weight(1f))
            else ChatList(list.filter { it.id !in prefs.pinnedList }, Modifier.weight(1f))
        }
        Column(Modifier.padding(horizontal = M.listInset).padding(bottom = 8.dp)) {
            if (prefs.sidebarShows("server")) ServerPill()
            AccountRow()
        }
    }
}

@Composable
private fun NavRow(ic: Ic, label: String, hint: String? = null, selected: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(M.navRow).hoverTap(Oc.round(M.listRadius), selected, onClick = onClick).padding(start = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ic, 20.dp, Oc.c.text)
        Spacer(Modifier.width(14.dp))
        Txt(label, size = M.navText, modifier = Modifier.weight(1f))
        if (hint != null) Txt(hint, size = 12.sp, color = Oc.c.textDim.copy(alpha = 0.7f), family = Oc.mono)
    }
}

@Composable
private fun SectionHead(text: String, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.padding(start = 12.dp, top = 18.dp, bottom = 6.dp).hoverTap(Oc.round(6.dp), onClick = onToggle).padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(text, size = 14.sp, color = Oc.c.textDim, family = Oc.label)
        Spacer(Modifier.width(4.dp))
        Icon(if (open) Ic.Chevron else Ic.ChevronRight, 13.dp, Oc.c.textDim)
    }
}

@Composable
private fun PinnedList(list: List<Session>) {
    var open by remember { mutableStateOf(true) }
    SectionHead("Pinned", open) { open = !open }
    if (open) Column(Modifier.padding(horizontal = M.listInset)) { list.forEach { SessionRow(it) } }
}

@Composable
private fun ChatList(list: List<Session>, modifier: Modifier) {
    var open by remember { mutableStateOf(true) }
    Column(modifier) {
        SectionHead("Recents", open) { open = !open }
        AnimatedVisibility(open, enter = expandVertically(tween(140)) + fadeIn(tween(140)), exit = shrinkVertically(tween(110)) + fadeOut(tween(90))) {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = M.listInset)) {
                items(list, key = { it.id }) { s -> SessionRow(s, Modifier.animateItem()) }
                if (list.isEmpty()) item { Txt("Your chats will show up here", size = 14.sp, color = Oc.c.textDim, modifier = Modifier.padding(12.dp)) }
            }
        }
    }
}

@Composable
private fun ProjectList(list: List<Session>, modifier: Modifier) {
    val prefs = Graph.prefs
    var open by remember { mutableStateOf(true) }
    val byDir = list.groupBy { it.directory }
    val dirs = (prefs.projectList + byDir.keys).distinct()
    Column(modifier) {
        SectionHead("Projects", open) { open = !open }
        if (open) LazyColumn(Modifier.fillMaxSize().padding(horizontal = M.listInset)) {
            dirs.forEach { d ->
                item(key = "dir:$d") {
                    ContextArea(menu = { close -> ProjectMenu(d, close) }) {
                        Row(
                            Modifier.fillMaxWidth().height(36.dp).hoverTap(Oc.round(M.listRadius), Nav.project == d && Nav.screen == Screen.Home) {
                                Nav.project = d; prefs.addProject(d); Nav.go(Screen.Home)
                            }.padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Ic.Folder, 17.dp, Oc.c.textDim)
                            Spacer(Modifier.width(10.dp))
                            Txt(File(d).name.ifBlank { d }, size = 15.sp, color = Oc.c.textDim, weight = FontWeight.Medium, maxLines = 1)
                        }
                    }
                }
                items(byDir[d].orEmpty().take(8), key = { it.id }) { s -> SessionRow(s, Modifier.animateItem().padding(start = 14.dp)) }
            }
            if (dirs.isEmpty()) item { Txt("Open a folder to start", size = 14.sp, color = Oc.c.textDim, modifier = Modifier.padding(12.dp)) }
        }
    }
}

@Composable
fun ProjectMenu(d: String, close: () -> Unit) {
    val prefs = Graph.prefs
    PopItem("New session here", Ic.Plus) { close(); prefs.mode = "code"; Nav.project = d; prefs.addProject(d); Nav.go(Screen.Home) }
    PopItem("Open in file manager", Ic.Folder) { close(); Desktop.open(d) }
    PopItem("Terminal here", Ic.Terminal) { close(); Nav.project = d; Nav.pane = "terminal" }
    PopItem("Copy path", Ic.Copy) { close(); copy(d) }
    PopDivider()
    PopItem("Remove from the list", Ic.Close) { close(); prefs.removeProject(d); if (Nav.project == d) Nav.project = null }
}

@Composable
private fun SessionRow(s: Session, modifier: Modifier = Modifier) {
    val c = Oc.c
    val busy = Graph.hub.statuses[s.id] != null
    val waiting = Graph.hub.permissions.any { it.sessionID == s.id } || Graph.hub.questions.any { it.sessionID == s.id }
    val selected = (Nav.screen as? Screen.Chat)?.id == s.id
    ContextArea(modifier, menu = { close -> SessionMenu(s, close) }) {
        Row(
            Modifier.fillMaxWidth().height(M.listRow).hoverTap(Oc.round(M.listRadius), selected) { Nav.go(Screen.Chat(s.id)) }.padding(start = 12.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // a new chat gets its name a moment after the first reply: it fades in instead of jumping
            androidx.compose.animation.AnimatedContent(s.displayTitle, Modifier.weight(1f), label = "title", transitionSpec = {
                (fadeIn(tween(220)) + androidx.compose.animation.slideInVertically(tween(220)) { it / 3 }) togetherWith fadeOut(tween(120))
            }) { t -> Txt(t, size = M.navText, maxLines = 1, color = if (selected) c.text else c.text.copy(alpha = 0.92f)) }
            val done = s.id in Graph.hub.doneUnseen
            if (selected && done) androidx.compose.runtime.LaunchedEffect(Unit) { Graph.hub.doneUnseen.remove(s.id) }
            if (waiting) { Spacer(Modifier.width(8.dp)); Box(Modifier.size(7.dp).clip(CircleShape).background(c.warn)) }
            else if (busy || done) {
                Spacer(Modifier.width(8.dp))
                // breathing slowly while the agent works, bright blue and still once the task is done
                WorkDot(working = busy && !done, size = 8.dp)
            }
        }
    }
}

// ---------- projects ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectsScreen() {
    val c = Oc.c
    val hub = Graph.hub
    val prefs = Graph.prefs
    val byDir = hub.sessions.filter { !hub.isChat(it) }.groupBy { it.directory }
    val dirs = (prefs.projectList + byDir.keys).distinct()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 36.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt("Projects", size = 30.sp, family = Oc.heading, weight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Button("Open folder", primary = true) { pickProject() }
        }
        Spacer(Modifier.height(6.dp))
        Txt("A project is a folder: its sessions, its AGENTS.md and its terminal live together.", size = 15.sp, color = c.textDim)
        Spacer(Modifier.height(28.dp))
        if (dirs.isEmpty()) Txt("No projects yet — open a folder to start.", size = 15.sp, color = c.textDim)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            dirs.forEach { d ->
                val ss = byDir[d].orEmpty()
                ContextArea(menu = { close -> ProjectMenu(d, close) }) {
                    Column(
                        Modifier.width(300.dp).clip(Oc.round(14.dp)).border(1.dp, c.border, Oc.round(14.dp))
                            .hoverTap(Oc.round(14.dp)) { prefs.mode = "code"; Nav.project = d; prefs.addProject(d); Nav.go(Screen.Home) }.padding(18.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Ic.Folder, 20.dp, c.text)
                            Spacer(Modifier.width(10.dp))
                            Txt(File(d).name.ifBlank { d }, size = 17.sp, weight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
                            if (!File(d).exists()) Txt("missing", size = 12.sp, color = c.warn)
                        }
                        Spacer(Modifier.height(4.dp))
                        Txt(d.replaceFirst(System.getProperty("user.home"), "~"), size = 13.sp, color = c.textDim, family = Oc.mono, maxLines = 1)
                        Spacer(Modifier.height(14.dp))
                        Txt(
                            if (ss.isEmpty()) "No sessions yet" else "${ss.size} sessions · updated ${ago(ss.maxOf { it.time.updated })}",
                            size = 13.sp, color = c.textDim,
                        )
                        ss.take(3).forEach { s -> Txt("· " + s.displayTitle, size = 14.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp)) }
                    }
                }
            }
        }
    }
}

// ---------- the bottom of the sidebar ----------

/** Where Claude shows "Update available": here, the server's state, so it's clear whether OpenCode is up. */
@Composable
private fun ServerPill() {
    val c = Oc.c
    val st by Graph.runtime.state.collectAsStateCompat()
    val remote = Graph.prefs.remoteUrl.isNotBlank()
    val connected by Graph.events.connected.collectAsStateCompat()
    val (text, dot) = when {
        remote -> (if (connected) "Connected to ${Graph.prefs.remoteUrl.removePrefix("http://").removePrefix("https://")}" else "Connecting to the other computer…") to (if (connected) c.ok else c.warn)
        st is RuntimeState.Running -> "OpenCode ${(st as RuntimeState.Running).version}" to (if (connected) c.ok else c.warn)
        st is RuntimeState.Installing -> (st as RuntimeState.Installing).note to c.warn
        st is RuntimeState.Failed -> (st as RuntimeState.Failed).reason to c.danger
        else -> "Starting OpenCode…" to c.warn
    }
    Row(
        Modifier.fillMaxWidth().height(42.dp).clip(Oc.round(M.listRadius)).background(c.surface)
            .hoverTap(Oc.round(M.listRadius)) { Nav.settings = "server" }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(10.dp))
        Txt(text, size = 15.sp, weight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
        Icon(Ic.ChevronRight, 15.dp, c.textDim)
    }
}

/** Claude's account button: name and model, a menu with settings, usage, the look and help. */
@Composable
private fun AccountRow() {
    val c = Oc.c
    val prefs = Graph.prefs
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(top = 6.dp).hoverTap(Oc.round(M.listRadius), menu) { menu = true }.padding(start = 10.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(c.surfaceHigh), contentAlignment = Alignment.Center) {
                if (prefs.avatarPath.isNotBlank()) Avatar(28.dp)
                else if (Oc.look.clouds) GhostCloud(18.dp, active = true, eyesLook = true)
                else Txt(prefs.name.take(1).uppercase(), size = 13.sp, weight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Txt(prefs.name, size = 15.sp, maxLines = 1)
                Txt(Graph.hub.pick?.label ?: "No model yet", size = 12.sp, color = c.textDim, maxLines = 1)
            }
            Icon(Ic.Chevron, 15.dp, c.textDim)
        }
        Pop(menu, { menu = false }, Side.Above, width = (M.sidebar - M.listInset * 2)) {
            PopHeader(System.getProperty("user.name") + "@" + runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrDefault("localhost"))
            PopItem("Settings", Ic.Settings, hint = "Ctrl+,") { menu = false; Nav.settings = "general" }
            PopItem("Account", Ic.User, value = if (prefs.signedIn.isBlank()) "Sign in" else null) { menu = false; Nav.settings = "account" }
            PopItem("Memory", Ic.Bulb) { menu = false; Nav.settings = "memory" }
            PopItem("Usage", Ic.Gauge) { menu = false; Nav.settings = "usage" }
            PopItem("Look", Ic.Palette, value = Looks.of(prefs.edition).title, submenu = { close ->
                Looks.all.forEach { l -> PopItem(l.title, checked = prefs.edition == l.id) { close(); menu = false; prefs.edition = l.id } }
                PopDivider()
                listOf("system" to "Match system", "dark" to "Dark", "light" to "Light").forEach { (id, t) ->
                    PopItem(t, checked = prefs.colorMode == id) { close(); menu = false; prefs.colorMode = id }
                }
            })
            PopItem("Get help", Ic.Info, submenu = { close ->
                PopItem("OpenCode docs", Ic.Globe) { close(); menu = false; Nav.openUrl("https://opencode.ai/docs") }
                PopItem("Keyboard shortcuts", Ic.Key) { close(); menu = false; Nav.settings = "shortcuts" }
                PopItem("Server log", Ic.File) { close(); menu = false; Desktop.open(Graph.runtime.logFile.absolutePath) }
            })
            PopItem("About cl0udly", Ic.Cloud) { menu = false; Nav.settings = "about" }
            PopDivider()
            PopItem("Quit", hint = "Ctrl+Q") { menu = false; Nav.quit() }
        }
    }
}

/**
 * A card on top of the work area while the server isn't there for you: starting, failed (click to restart),
 * the event stream dropped and not back within 2.5 s, the other computer unreachable, or no network at all.
 */
@Composable
private fun ServerBanner(modifier: Modifier) {
    val st by Graph.runtime.state.collectAsStateCompat()
    val connected by Graph.events.connected.collectAsStateCompat()
    val remote = Graph.prefs.remoteUrl.isNotBlank()
    val c = Oc.c
    val online = rememberOnline()
    var lost by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(connected, st, remote) {
        lost = false
        if (!connected && (remote || st is RuntimeState.Running)) { kotlinx.coroutines.delay(2500); lost = true }
    }
    val inst = st as? RuntimeState.Installing
    val failed = !remote && st is RuntimeState.Failed
    val show = !online || lost || (!remote && st !is RuntimeState.Running)
    // keeps the last words while it fades out
    var text by remember { mutableStateOf("") }
    if (show) text = when {
        !online -> "No internet. Replies go on when it's back."
        remote && lost -> "Reconnecting to the other computer…"
        lost -> "Reconnecting to OpenCode…"
        else -> when (val s = st) {
            is RuntimeState.Installing -> s.note
            is RuntimeState.Failed -> if (s.reason.contains("restart", true)) s.reason else s.reason + "  Click to restart."
            else -> "Starting OpenCode…"
        }
    }
    AnimatedVisibility(show, modifier, enter = fadeIn(tween(140)), exit = fadeOut(tween(140))) {
        Column(
            Modifier.width(380.dp).clip(Oc.round(12.dp)).background(c.surface).border(1.dp, c.border, Oc.round(12.dp))
                .then(if (failed) Modifier.hoverTap(Oc.round(12.dp)) { Graph.runtime.restart() } else Modifier).padding(14.dp),
        ) {
            Txt(text, size = 14.sp, color = if (failed) c.danger else if (!online) c.textDim else c.text)
            if (inst != null && inst.share >= 0) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(c.border)) {
                    Box(Modifier.fillMaxWidth(inst.share.coerceIn(0f, 1f)).fillMaxHeight().background(c.accent))
                }
            }
        }
    }
}

/** Whether this computer has any network besides itself; looked at every few seconds (there's no event for it here). */
@Composable
private fun rememberOnline(): Boolean {
    var online by remember { mutableStateOf(true) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            online = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    java.net.NetworkInterface.getNetworkInterfaces().toList().any { n ->
                        n.isUp && !n.isLoopback && !n.isVirtual && n.inetAddresses.toList().any { !it.isLinkLocalAddress && !it.isLoopbackAddress }
                    }
                }.getOrDefault(true)
            }
            kotlinx.coroutines.delay(3000)
        }
    }
    return online
}

fun openFolder(dir: String) {
    app.cl0udly.data.Os.open(dir)
}

@Composable
fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateCompat() = collectAsState()

/** The bright blue of a finished task: the same in every look, so it reads at a glance (as on the phone). */
val DoneBlue = androidx.compose.ui.graphics.Color(0xFF3D8BFF)

/** A chat's state as a dot: breathes slowly (2.4 s in, 2.4 s out) while working, bright blue and still when done. */
@Composable
fun WorkDot(working: Boolean, size: androidx.compose.ui.unit.Dp = 8.dp) {
    if (!working) return Box(Modifier.size(size).clip(CircleShape).background(DoneBlue))
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "work-dot")
    val a by t.animateFloat(
        0.18f, 0.85f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(2400, easing = androidx.compose.animation.core.LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "breath",
    )
    Box(Modifier.size(size).alpha(a).clip(CircleShape).background(Oc.c.text))
}
