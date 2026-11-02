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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import app.cl0udly.runtime.RuntimeState
import app.cl0udly.ui.cloud.GhostCloud
import app.cl0udly.ui.cloud.MiniCloudIcon
import app.cl0udly.ui.kit.Ic
import app.cl0udly.ui.kit.Icon
import app.cl0udly.ui.kit.IconButton
import app.cl0udly.ui.kit.Txt
import app.cl0udly.ui.kit.hoverTap
import app.cl0udly.ui.theme.Oc
import java.io.File

/** Title bar + sidebar + the work area: the frame of Claude desktop. */
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
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    Nav.screen, label = "screen",
                    transitionSpec = { fadeIn(tween(130)) togetherWith fadeOut(tween(90)) },
                    contentKey = { it::class },
                ) { s ->
                    when (s) {
                        Screen.Home -> Home()
                        is Screen.Chat -> ChatScreen(s.id)
                        is Screen.Settings -> Settings(s.section)
                    }
                }
                ServerBanner(Modifier.align(Alignment.TopCenter).padding(top = 10.dp))
                Toasts(Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
            }
        }
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
    Row(modifier.padding(start = 34.dp), verticalAlignment = Alignment.CenterVertically) {
        when (s) {
            is Screen.Chat -> Graph.hub.session(s.id)?.let { SessionTitle(it) }
            is Screen.Settings -> Txt("Settings", size = M.navText)
            Screen.Home -> if (Graph.prefs.mode == "code" && Nav.project != null) Txt(File(Nav.project!!).name, size = M.navText)
        }
    }
}

@Composable
private fun SessionTitle(s: Session) {
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.hoverTap(Oc.round(8.dp)) { menu = true }.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt(s.displayTitle, size = M.navText, maxLines = 1, modifier = Modifier.padding(end = 6.dp))
            Icon(Ic.Chevron, 16.dp, Oc.c.textDim)
        }
        Menu(menu, { menu = false }) {
            MenuItem(Ic.Edit, "Rename") { menu = false; rename = true }
            if (s.directory != CHAT_DIR) MenuItem(Ic.Folder, "Open folder") { menu = false; openFolder(s.directory) }
            MenuItem(Ic.Trash, "Delete", danger = true) { menu = false; Graph.hub.delete(s.id); Nav.go(Screen.Home) }
        }
    }
    if (rename) RenameDialog(s.displayTitle, { rename = false }) { Graph.hub.rename(s.id, it); rename = false }
}

@Composable
private fun TitleRight(onClose: () -> Unit) {
    var looks by remember { mutableStateOf(false) }
    Row(Modifier.padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(Ic.Search, { Nav.search = true })
        Box {
            IconButton(Ic.Palette, { looks = true })
            LookMenu(looks) { looks = false }
        }
        IconButton(Ic.Settings, { Nav.go(Screen.Settings()) })
        Spacer(Modifier.width(14.dp))
        IconButton(Ic.Close, onClose, size = 28.dp, icon = 15.dp)
    }
}

// ---------- sidebar ----------

@Composable
private fun Sidebar() {
    val c = Oc.c
    val prefs = Graph.prefs
    val hub = Graph.hub
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = M.listInset).padding(top = 8.dp)) {
            NavRow(Ic.Plus, if (prefs.mode == "code") "New session" else "New chat") { Nav.go(Screen.Home) }
            NavRow(Ic.Search, "Search") { Nav.search = true }
            NavRow(Ic.Box, "Models") { Nav.go(Screen.Settings("models")) }
            NavRow(Ic.Sliders, "Customize") { Nav.go(Screen.Settings("appearance")) }
            NavRow(Ic.More, "More") { Nav.go(Screen.Settings("general")) }
        }
        val list = hub.sessions
        if (prefs.mode == "code") ProjectList(list, Modifier.weight(1f)) else ChatList(list.filter { hub.isChat(it) }, Modifier.weight(1f))
        Column(Modifier.padding(horizontal = M.listInset).padding(bottom = 8.dp)) {
            ServerPill()
            AccountRow()
        }
    }
}

@Composable
private fun NavRow(ic: Ic, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(M.navRow).hoverTap(Oc.round(M.listRadius), onClick = onClick).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ic, 20.dp, Oc.c.text)
        Spacer(Modifier.width(14.dp))
        Txt(label, size = M.navText)
    }
}

@Composable
private fun SectionHead(text: String, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.padding(start = 12.dp, top = 22.dp, bottom = 6.dp).hoverTap(Oc.round(6.dp), onClick = onToggle).padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(text, size = 14.sp, color = Oc.c.textDim, family = Oc.label)
        Spacer(Modifier.width(4.dp))
        Icon(if (open) Ic.Chevron else Ic.ChevronRight, 13.dp, Oc.c.textDim)
    }
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
    val byDir = list.filter { it.directory != CHAT_DIR }.groupBy { it.directory }
    val dirs = (prefs.projectList + byDir.keys).distinct()
    Column(modifier) {
        SectionHead("Projects", open) { open = !open }
        if (open) LazyColumn(Modifier.fillMaxSize().padding(horizontal = M.listInset)) {
            dirs.forEach { d ->
                item(key = "dir:$d") {
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
                items(byDir[d].orEmpty().take(8), key = { it.id }) { s -> SessionRow(s, Modifier.animateItem().padding(start = 14.dp)) }
            }
            if (dirs.isEmpty()) item { Txt("Open a folder to start", size = 14.sp, color = Oc.c.textDim, modifier = Modifier.padding(12.dp)) }
        }
    }
}

@Composable
private fun SessionRow(s: Session, modifier: Modifier = Modifier) {
    val c = Oc.c
    val busy = Graph.hub.statuses[s.id] != null
    val selected = (Nav.screen as? Screen.Chat)?.id == s.id
    Row(
        modifier.fillMaxWidth().height(M.listRow).hoverTap(Oc.round(M.listRadius), selected) { Nav.go(Screen.Chat(s.id)) }.padding(start = 12.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(s.displayTitle, size = M.navText, maxLines = 1, modifier = Modifier.weight(1f), color = if (selected) c.text else c.text.copy(alpha = 0.92f))
        if (busy) {
            Spacer(Modifier.width(8.dp))
            if (Oc.look.clouds) MiniCloudIcon(16.dp) else Box(Modifier.size(7.dp).clip(CircleShape).background(c.accent))
        }
    }
}

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
            .hoverTap(Oc.round(M.listRadius)) { Nav.go(Screen.Settings("server")) }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(10.dp))
        Txt(text, size = 15.sp, weight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
        Icon(Ic.ChevronRight, 15.dp, c.textDim)
    }
}

@Composable
private fun AccountRow() {
    val c = Oc.c
    val prefs = Graph.prefs
    Row(Modifier.fillMaxWidth().height(58.dp).padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(c.surfaceHigh), contentAlignment = Alignment.Center) {
            if (Oc.look.clouds) GhostCloud(18.dp, active = true, eyesLook = true)
            else Txt(prefs.name.take(1).uppercase(), size = 13.sp, weight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(10.dp))
        Txt(prefs.name, size = M.navText, maxLines = 1)
        Txt(" · " + Graph.hub.pick?.label.orEmpty(), size = 14.sp, color = c.textDim, maxLines = 1, modifier = Modifier.weight(1f))
        IconButton(Ic.Settings, { Nav.go(Screen.Settings()) }, tint = c.textDim)
    }
}

/** A thin strip on top of the work area while the server is not up yet. */
@Composable
private fun ServerBanner(modifier: Modifier) {
    val st by Graph.runtime.state.collectAsStateCompat()
    if (Graph.prefs.remoteUrl.isNotBlank()) return
    val c = Oc.c
    val inst = st as? RuntimeState.Installing
    AnimatedVisibility(st !is RuntimeState.Running, modifier, enter = fadeIn(tween(140)), exit = fadeOut(tween(140))) {
        Column(
            Modifier.width(360.dp).clip(Oc.round(12.dp)).background(c.surface).border(1.dp, c.border, Oc.round(12.dp)).padding(14.dp),
        ) {
            Txt(
                when (val s = st) {
                    is RuntimeState.Installing -> s.note
                    is RuntimeState.Failed -> s.reason
                    else -> "Starting OpenCode…"
                },
                size = 14.sp,
            )
            if (inst != null && inst.share >= 0) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(c.border)) {
                    Box(Modifier.fillMaxWidth(inst.share.coerceIn(0f, 1f)).fillMaxHeight().background(c.accent))
                }
            }
        }
    }
}

fun openFolder(dir: String) {
    runCatching { ProcessBuilder("xdg-open", dir).start() }
}

@Composable
fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateCompat() = collectAsState()
