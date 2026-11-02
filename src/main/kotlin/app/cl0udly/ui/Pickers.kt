package app.cl0udly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cl0udly.Graph
import app.cl0udly.ui.kit.Ic
import app.cl0udly.ui.kit.Icon
import app.cl0udly.ui.kit.Txt
import app.cl0udly.ui.kit.hoverTap
import app.cl0udly.ui.theme.Oc
import kotlinx.coroutines.delay
import java.io.File

/** Opening folders and files goes through our own picker, drawn in the look (Claude Code has its own too). */
object Pickers {
    fun folder(title: String = "Open a project folder", onPick: (String) -> Unit) {
        Nav.picker = PickRequest(folders = true, multiple = false, title = title, start = Nav.project) { it.firstOrNull()?.let(onPick) }
    }

    fun files(title: String = "Add files", onPick: (List<String>) -> Unit) {
        Nav.picker = PickRequest(folders = false, multiple = true, title = title, start = Nav.project) { onPick(it) }
    }

    /** File → Open file…: text and code open in the Files panel, pages and pictures in the browser. */
    fun openFile() = files("Open a file") { list ->
        list.firstOrNull()?.let { f ->
            if (f.substringAfterLast('.').lowercase() in setOf("html", "htm", "svg", "png", "jpg", "jpeg", "gif", "webp", "pdf")) Nav.openUrl(File(f).toURI().toString())
            else { Panes.preview = f; Nav.pane = "files" }
        }
    }
}

/** Code: pick a folder, it becomes the open project. */
fun pickProject() = Pickers.folder { d ->
    Graph.prefs.addProject(d); Graph.prefs.mode = "code"; Nav.project = d; Nav.go(Screen.Home)
}

private val home = System.getProperty("user.home")

@Composable
fun PickerDialog() {
    val req = Nav.picker ?: return
    val c = Oc.c
    var dir by remember(req) { mutableStateOf(File(req.start?.takeIf { File(it).isDirectory } ?: home)) }
    var filter by remember(req) { mutableStateOf("") }
    var hidden by remember(req) { mutableStateOf(false) }
    val chosen = remember(req) { mutableStateListOf<String>() }
    val focus = remember { FocusRequester() }
    val entries = remember(dir, hidden, filter) {
        (dir.listFiles()?.toList().orEmpty())
            .filter { hidden || !it.name.startsWith(".") }
            .filter { !req.folders || it.isDirectory }
            .filter { filter.isBlank() || it.name.contains(filter, true) }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }
    val list = rememberLazyListState()
    LaunchedEffect(dir) { list.scrollToItem(0); filter = "" }
    fun close() { Nav.picker = null }
    fun done(paths: List<String>) { close(); req.onPick(paths) }
    Scrim(true, ::close) {
        Column(Modifier.size(860.dp, 600.dp).clip(Oc.round(16.dp)).background(c.surface).border(1.dp, c.border, Oc.round(16.dp))) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt(req.title, size = 17.sp, weight = FontWeight.SemiBold, family = Oc.heading, modifier = Modifier.weight(1f))
                app.cl0udly.ui.kit.IconButton(Ic.Close, ::close, size = 30.dp, icon = 15.dp)
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                // places, like a file manager's sidebar
                Column(Modifier.width(200.dp).fillMaxHeight().padding(horizontal = 10.dp)) {
                    val places = listOfNotNull(
                        Triple(Ic.Home, "Home", home),
                        Triple(Ic.Folder, "Projects", "$home/Проекты").takeIf { File(it.third).isDirectory },
                        Triple(Ic.Folder, "Projects", "$home/Projects").takeIf { File(it.third).isDirectory },
                        Triple(Ic.Download, "Downloads", "$home/Загрузки").takeIf { File(it.third).isDirectory },
                        Triple(Ic.Download, "Downloads", "$home/Downloads").takeIf { File(it.third).isDirectory },
                        Triple(Ic.File, "Documents", "$home/Документы").takeIf { File(it.third).isDirectory },
                        Triple(Ic.Box, "Root", "/"),
                    )
                    places.forEach { (ic, label, path) -> PlaceRow(ic, label, dir.absolutePath == path) { dir = File(path) } }
                    if (Graph.prefs.projectList.isNotEmpty()) {
                        Txt("Recent", size = 12.sp, color = c.textDim, modifier = Modifier.padding(start = 10.dp, top = 14.dp, bottom = 4.dp))
                        Graph.prefs.projectList.take(6).forEach { p -> PlaceRow(Ic.Code, File(p).name, dir.absolutePath == p) { dir = File(p) } }
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight().padding(end = 16.dp)) {
                    // path as breadcrumbs, each part clickable
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                        val parts = generateSequence(dir) { it.parentFile }.toList().reversed()
                        parts.forEachIndexed { i, f ->
                            if (i > 0) Icon(Ic.ChevronRight, 13.dp, c.textDim)
                            Box(Modifier.hoverTap(Oc.round(6.dp)) { dir = f }.padding(horizontal = 6.dp, vertical = 4.dp)) {
                                Txt(if (f.path == "/") "/" else f.name.ifBlank { f.path }, size = 13.sp, color = if (i == parts.lastIndex) c.text else c.textDim, maxLines = 1)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(Oc.round(10.dp)).border(1.dp, c.border, Oc.round(10.dp)).padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Ic.Search, 15.dp, c.textDim)
                        Spacer(Modifier.width(10.dp))
                        Field(filter, { filter = it }, "Filter, or type a path and press Enter", Modifier.weight(1f).focusRequester(focus).onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (e.key) {
                                Key.Enter -> {
                                    val typed = File(filter.replaceFirst("~", home))
                                    if (filter.contains('/') && typed.isDirectory) dir = typed
                                    else entries.firstOrNull()?.let { if (it.isDirectory) dir = it }
                                    true
                                }
                                Key.Backspace -> if (filter.isEmpty()) { dir.parentFile?.let { dir = it }; true } else false
                                Key.Escape -> { close(); true }
                                else -> false
                            }
                        }, size = 14)
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.hoverTap(Oc.round(6.dp), hidden) { hidden = !hidden }.padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Txt("Hidden", size = 12.sp, color = if (hidden) c.text else c.textDim)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list) {
                        dir.parentFile?.let { up ->
                            item(key = "..") { EntryRow(Ic.Back, "..", null, false) { dir = up } }
                        }
                        items(entries, key = { it.absolutePath }) { f ->
                            val sel = f.absolutePath in chosen
                            EntryRow(
                                if (f.isDirectory) Ic.Folder else iconFor(f.name), f.name,
                                if (f.isDirectory) null else size(f.length()), sel,
                                onDouble = { if (f.isDirectory) dir = f else done(listOf(f.absolutePath)) },
                            ) {
                                when {
                                    f.isDirectory && !req.folders -> dir = f
                                    f.isDirectory -> { chosen.clear(); chosen.add(f.absolutePath) }
                                    req.multiple -> if (sel) chosen.remove(f.absolutePath) else chosen.add(f.absolutePath)
                                    else -> { chosen.clear(); chosen.add(f.absolutePath) }
                                }
                            }
                        }
                        if (entries.isEmpty()) item { Txt(if (req.folders) "No folders here" else "Empty folder", size = 13.sp, color = c.textDim, modifier = Modifier.padding(12.dp)) }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Txt(
                    when {
                        req.folders -> chosen.firstOrNull()?.replace(home, "~") ?: dir.absolutePath.replace(home, "~")
                        chosen.isEmpty() -> "Click files to pick them, double-click opens one"
                        else -> "${chosen.size} picked"
                    },
                    size = 13.sp, color = c.textDim, family = Oc.mono, maxLines = 1, modifier = Modifier.weight(1f),
                )
                Button("Cancel", primary = false, onClick = ::close)
                Button(if (req.folders) "Open this folder" else "Add") {
                    if (req.folders) done(listOf(chosen.firstOrNull() ?: dir.absolutePath)) else if (chosen.isNotEmpty()) done(chosen.toList())
                }
            }
        }
        LaunchedEffect(Unit) { delay(40); runCatching { focus.requestFocus() } }
    }
}

@Composable
private fun PlaceRow(ic: Ic, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(34.dp).hoverTap(Oc.round(8.dp), selected, onClick = onClick).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(ic, 16.dp, Oc.c.textDim)
        Spacer(Modifier.width(10.dp))
        Txt(label, size = 14.sp, maxLines = 1)
    }
}

@Composable
private fun EntryRow(ic: Ic, name: String, meta: String?, selected: Boolean, onDouble: (() -> Unit)? = null, onClick: () -> Unit) {
    val c = Oc.c
    var last by remember { mutableStateOf(0L) }
    Row(
        Modifier.fillMaxWidth().height(36.dp).hoverTap(Oc.round(8.dp), selected) {
            val now = System.currentTimeMillis()
            if (onDouble != null && now - last < 350) onDouble() else onClick()
            last = now
        }.padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ic, 16.dp, if (ic == Ic.Folder) c.accent else c.textDim)
        Spacer(Modifier.width(12.dp))
        Txt(name, size = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
        if (meta != null) Txt(meta, size = 12.sp, color = c.textDim, family = Oc.mono)
    }
}

fun iconFor(name: String): Ic = when (name.substringAfterLast('.', "").lowercase()) {
    "png", "jpg", "jpeg", "gif", "webp", "svg", "bmp" -> Ic.Image
    "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx", "rs", "go", "c", "cpp", "h", "sh", "dart", "css", "html", "json", "toml", "yaml", "yml", "xml" -> Ic.Code
    else -> Ic.File
}

fun size(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "%.1f KB".format(b / 1024.0)
    b < 1024L * 1024 * 1024 -> "%.1f MB".format(b / 1024.0 / 1024)
    else -> "%.1f GB".format(b / 1024.0 / 1024 / 1024)
}
