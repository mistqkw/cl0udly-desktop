package app.cl0udly.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cl0udly.data.Desktop
import app.cl0udly.ui.kit.Ic
import app.cl0udly.ui.kit.Icon
import app.cl0udly.ui.kit.IconButton
import app.cl0udly.ui.kit.Txt
import app.cl0udly.ui.kit.hoverTap
import app.cl0udly.ui.theme.Oc
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import javafx.scene.web.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.SwingUtilities

/**
 * Everything you and the agent send opens right in the app, like in Claude: pictures zoom, PDFs scroll page
 * by page, video and audio play, Markdown and web pages show rendered (their source a click away), tables as
 * tables, any text or code with line numbers. Only what the app can't show goes to the system's app.
 */
object FileView {
    var path by mutableStateOf<String?>(null)
    fun open(p: String) { path = p }
}

private enum class Kind { Image, Pdf, Video, Audio, Markdown, Html, Csv, Text, Other }

private const val TEXT_LIMIT = 2_000_000

private val TEXT_EXT = setOf(
    "txt", "md", "json", "kt", "kts", "java", "py", "js", "mjs", "ts", "tsx", "jsx", "c", "h", "cpp", "hpp", "rs", "go", "sh", "bash", "zsh",
    "yml", "yaml", "toml", "xml", "css", "scss", "log", "ini", "conf", "cfg", "gradle", "sql", "rb", "php", "swift", "lua", "dart", "env", "diff", "patch",
)

private fun kindOf(f: File): Kind = when (val ext = f.extension.lowercase()) {
    "png", "jpg", "jpeg", "gif", "webp", "bmp" -> Kind.Image
    "pdf" -> Kind.Pdf
    "mp4", "m4v", "mov", "webm", "mkv" -> Kind.Video
    "mp3", "wav", "m4a", "aac", "ogg", "flac", "aif", "aiff" -> Kind.Audio
    "md", "markdown" -> Kind.Markdown
    "html", "htm", "svg" -> Kind.Html
    "csv", "tsv" -> Kind.Csv
    else -> if (ext in TEXT_EXT || looksLikeText(f)) Kind.Text else Kind.Other
}

/** No zero bytes in the first 4 KB and almost all of it valid UTF-8: show it as text. */
private fun looksLikeText(f: File): Boolean = runCatching {
    val b = f.inputStream().use { it.readNBytes(4096) }
    if (b.isEmpty()) return true
    if (b.any { it == 0.toByte() }) return false
    val s = String(b, Charsets.UTF_8)
    s.count { it == '�' } < s.length / 50 + 1
}.getOrDefault(false)

private fun readText(f: File): String {
    val s = String(f.inputStream().use { it.readNBytes(TEXT_LIMIT) }, Charsets.UTF_8)
    return if (f.length() > TEXT_LIMIT) s + "\n\n… (${human(f.length())}: only the first ${human(TEXT_LIMIT.toLong())} are shown)" else s
}

internal fun human(b: Long) = when { b >= 1_000_000 -> String.format(java.util.Locale.US, "%.1f MB", b / 1e6); b >= 1000 -> "${b / 1000} KB"; else -> "$b B" }

private fun label(k: Kind, f: File) = when (k) {
    Kind.Image -> "Image"; Kind.Pdf -> "PDF"; Kind.Video -> "Video"; Kind.Audio -> "Audio"; Kind.Markdown -> "Markdown"
    Kind.Html -> "Web page"; Kind.Csv -> "Table"; Kind.Text, Kind.Other -> f.extension.uppercase().ifBlank { "File" }
}

/** Over everything (put in Main): the file in a card that grows from 96 %, Esc or a click outside closes it. */
@Composable
fun FileViewerHost() {
    val path = FileView.path ?: return
    val f = remember(path) { File(path) }
    val c = Oc.c
    val a = remember(path) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(path) { a.animateTo(1f, tween(150, easing = FastOutSlowInEasing)) }
    fun close() = scope.launch { a.animateTo(0f, tween(110)); FileView.path = null }
    DisposableEffect(path) { app.cl0udly.ui.kit.Overlay.count++; onDispose { app.cl0udly.ui.kit.Overlay.count-- } }
    val focus = remember { FocusRequester() }
    LaunchedEffect(path) { runCatching { focus.requestFocus() } }
    val kind = remember(path) { if (f.isFile) kindOf(f) else null }
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = a.value }.background(c.scrim.copy(alpha = 0.72f))
            .clickable(remember { MutableInteractionSource() }, null) { close() }
            .focusRequester(focus).focusable()
            .onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { close(); true } else false },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(40.dp).widthIn(max = 1100.dp).fillMaxSize()
                .graphicsLayer { val s = 0.96f + 0.04f * a.value; scaleX = s; scaleY = s }
                .clip(Oc.round(16.dp)).background(c.bg).border(1.dp, c.border, Oc.round(16.dp))
                .clickable(remember { MutableInteractionSource() }, null) {},
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (kind == Kind.Image) Ic.Image else Ic.File, 18.dp, c.textDim)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Txt(f.name, size = 15.sp, weight = FontWeight.SemiBold, maxLines = 1)
                    Txt(if (kind == null) "Not found" else label(kind, f) + "  " + human(f.length()), size = 12.sp, color = c.textDim, maxLines = 1)
                }
                if (kind != null) {
                    IconButton(Ic.Download, { saveToDownloads(f) }, size = 32.dp, icon = 16.dp)
                    IconButton(Ic.Copy, { copyText(f.absolutePath) }, size = 32.dp, icon = 16.dp)
                    IconButton(Ic.Folder, { Desktop.open(f.parentFile?.absolutePath ?: f.absolutePath) }, size = 32.dp, icon = 16.dp)
                    IconButton(Ic.Expand, { Desktop.open(f.absolutePath) }, size = 32.dp, icon = 16.dp)
                }
                IconButton(Ic.Close, { close() }, size = 32.dp, icon = 16.dp)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (kind) {
                    null -> Txt("This file is gone.", Modifier.align(Alignment.Center), color = c.textDim)
                    Kind.Image -> ZoomImage(f)
                    Kind.Pdf -> PdfPages(f)
                    Kind.Video, Kind.Audio -> MediaPlayerView(f, video = kind == Kind.Video)
                    Kind.Markdown -> Rendered(f) { text -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 20.dp)) { SelectionContainer { Markdown(text) } } }
                    Kind.Html -> Rendered(f) { HtmlPage(f) }
                    Kind.Csv -> CsvTable(f)
                    Kind.Text -> TextLines(readText(f))
                    Kind.Other -> OtherFile(f)
                }
            }
        }
    }
}

/** Markdown and web pages: rendered, with a switch to the source text. */
@Composable
private fun Rendered(f: File, view: @Composable (String) -> Unit) {
    val c = Oc.c
    val text = remember(f) { readText(f) }
    var source by remember(f) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(false to "View", true to "Source").forEach { (v, l) ->
                Txt(l, Modifier.hoverTap(Oc.round(8.dp), selected = source == v) { source = v }.padding(horizontal = 12.dp, vertical = 5.dp), size = 13.sp, color = if (source == v) c.text else c.textDim)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { if (source) TextLines(text) else view(text) }
    }
}

/** Numbered lines in a mono font, selectable; long lines wrap. */
@Composable
private fun TextLines(text: String) {
    val c = Oc.c
    val lines = remember(text) { text.split('\n') }
    val gutter = remember(lines.size) { lines.size.toString().length }
    val list = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        SelectionContainer {
            LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(vertical = 10.dp)) {
                itemsIndexed(lines) { i, line ->
                    Row(Modifier.fillMaxWidth().padding(end = 20.dp)) {
                        Txt((i + 1).toString().padStart(gutter), Modifier.padding(start = 16.dp, end = 16.dp), size = 12.sp, color = c.textDim.copy(alpha = 0.6f), family = Oc.mono, lineHeight = 20.sp)
                        Txt(line.ifEmpty { " " }, Modifier.weight(1f), size = 13.sp, family = Oc.mono, lineHeight = 20.sp)
                    }
                }
            }
        }
        androidx.compose.foundation.VerticalScrollbar(
            androidx.compose.foundation.rememberScrollbarAdapter(list),
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 3.dp, top = 4.dp, bottom = 4.dp),
            style = androidx.compose.foundation.defaultScrollbarStyle().copy(unhoverColor = c.border, hoverColor = c.textDim, thickness = 6.dp),
        )
    }
}

@Composable
private fun CsvTable(f: File) {
    val c = Oc.c
    val rows = remember(f) { parseCsv(readText(f), if (f.extension.lowercase() == "tsv") '\t' else ',').take(5000) }
    if (rows.isEmpty()) return TextLines("")
    val cols = rows.maxOf { it.size }
    val widths = remember(rows) { (0 until cols).map { i -> (rows.take(300).maxOf { it.getOrNull(i)?.length ?: 0 }.coerceIn(3, 40) * 8 + 28).dp } }
    Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
        LazyColumn(Modifier.width(widths.fold(0.dp) { a, w -> a + w }).fillMaxHeight(), contentPadding = PaddingValues(bottom = 20.dp)) {
            itemsIndexed(rows) { r, row ->
                Row(Modifier.background(if (r == 0) c.surfaceHigh else if (r % 2 == 0) c.surface else Color.Transparent)) {
                    (0 until cols).forEach { i ->
                        Txt(row.getOrNull(i).orEmpty(), Modifier.width(widths[i]).padding(horizontal = 14.dp, vertical = 7.dp), size = 13.sp,
                            weight = if (r == 0) FontWeight.SemiBold else null, maxLines = 2)
                    }
                }
            }
        }
    }
}

/** A plain CSV reader: quoted fields, doubled quotes, line breaks inside quotes. */
internal fun parseCsv(s: String, sep: Char): List<List<String>> {
    val rows = ArrayList<List<String>>()
    var row = ArrayList<String>()
    val cell = StringBuilder()
    var quoted = false
    var i = 0
    while (i < s.length) {
        val ch = s[i]
        when {
            quoted && ch == '"' && s.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
            ch == '"' -> quoted = !quoted
            !quoted && ch == sep -> { row.add(cell.toString()); cell.clear() }
            !quoted && (ch == '\n' || ch == '\r') -> {
                if (ch == '\r' && s.getOrNull(i + 1) == '\n') i++
                row.add(cell.toString()); cell.clear(); rows.add(row); row = ArrayList()
            }
            else -> cell.append(ch)
        }
        i++
    }
    if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString()); rows.add(row) }
    return rows.filterNot { it.size == 1 && it[0].isEmpty() }
}

/** The wheel zooms, dragging moves a zoomed picture, a double click fits it back. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun ZoomImage(f: File) {
    val img = rememberImage(f.absolutePath)
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.fillMaxSize().clip(androidx.compose.ui.graphics.RectangleShape)
            .onPointerEvent(PointerEventType.Scroll) { e ->
                val d = e.changes.first().scrollDelta.y
                val next = (scale * if (d < 0) 1.15f else 1f / 1.15f).coerceIn(1f, 10f)
                if (next == 1f) offset = Offset.Zero
                scale = next
            }
            .pointerInput(Unit) { detectDragGestures { ch, drag -> ch.consume(); if (scale > 1f) offset += drag } }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f }) }
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (img != null) Image(img, f.name, Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }, contentScale = ContentScale.Fit)
    }
}

/** PDF pages one under another, drawn by PDFBox off the UI thread; Ctrl + wheel or the buttons zoom. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun PdfPages(f: File) {
    val c = Oc.c
    val doc by produceState<Result<PdfDoc>?>(null, f) { value = withContext(Dispatchers.IO) { runCatching { PdfDoc(f) } } }
    DisposableEffect(f) { onDispose { doc?.getOrNull()?.close() } }
    val d = doc?.getOrNull()
    if (doc == null) return Txt("Opening…", Modifier.padding(24.dp), color = c.textDim)
    if (d == null) return Txt("This PDF can't be opened: ${doc?.exceptionOrNull()?.message}", Modifier.padding(24.dp), color = c.textDim)
    var zoom by remember { mutableFloatStateOf(1f) }
    val list = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxSize().background(c.surface)) {
        val full = maxWidth
        val pageW = minOf(full - 64.dp, 860.dp) * zoom
        val px = with(LocalDensity.current) { pageW.toPx() }
        Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
            LazyColumn(
                Modifier.width(maxOf(full, pageW + 64.dp)).fillMaxHeight().onPointerEvent(PointerEventType.Scroll) { e ->
                    if ((e.nativeEvent as? java.awt.event.InputEvent)?.isControlDown == true) { zoom = (zoom * if (e.changes.first().scrollDelta.y < 0) 1.1f else 1 / 1.1f).coerceIn(0.5f, 4f); e.changes.forEach { it.consume() } }
                },
                state = list, contentPadding = PaddingValues(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items(d.count) { i ->
                    val bmp by produceState<ImageBitmap?>(null, i, (px / 200).toInt()) { value = d.render(i, px.toInt().coerceIn(200, 3000)) }
                    Box(Modifier.width(pageW).aspectRatio(d.ratio(i)).background(Color.White)) {
                        bmp?.let { Image(it, "Page ${i + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                    }
                }
            }
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(14.dp).clip(Oc.round(10.dp)).background(c.bg).border(1.dp, c.border, Oc.round(10.dp)).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(Ic.Minus, { zoom = (zoom / 1.2f).coerceAtLeast(0.5f) }, size = 28.dp, icon = 14.dp)
            Txt("${(zoom * 100).toInt()}%", Modifier.width(48.dp), size = 12.sp, color = c.textDim, align = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton(Ic.Plus, { zoom = (zoom * 1.2f).coerceAtMost(4f) }, size = 28.dp, icon = 14.dp)
            Txt("${d.count} pages", Modifier.padding(start = 6.dp, end = 8.dp), size = 12.sp, color = c.textDim)
        }
    }
}

/** PDFBox renders one page at a time: every use goes through a lock. */
private class PdfDoc(f: File) {
    private val doc = org.apache.pdfbox.Loader.loadPDF(f)
    private val renderer = org.apache.pdfbox.rendering.PDFRenderer(doc)
    private val lock = Mutex()
    private val sizes = (0 until doc.numberOfPages).map { i -> doc.getPage(i).cropBox.let { it.width to it.height } }
    val count get() = sizes.size
    fun ratio(i: Int) = sizes[i].let { (w, h) -> w / h.coerceAtLeast(1f) }
    suspend fun render(i: Int, width: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        lock.withLock { runCatching { renderer.renderImage(i, width / sizes[i].first).toComposeImageBitmap() }.getOrNull() }
    }
    fun close() { runCatching { doc.close() } }
}

private val MPV: String? by lazy {
    System.getenv("PATH").orEmpty().split(File.pathSeparator).map { File(it, "mpv") }.firstOrNull { it.canExecute() }?.path
}

/**
 * Video or audio. With mpv on the computer (Linux) it plays inside the viewer, in a window mpv draws into,
 * with mpv's own controls (they show when the pointer moves). Without it, JavaFX's player; if that can't
 * play the file either, a button opens it in the system's player.
 */
@Composable
private fun MediaPlayerView(f: File, video: Boolean) {
    val mpv = MPV
    if (mpv != null) MpvView(f, mpv, video) else FxMediaView(f, video)
}

/** mpv inside an AWT canvas: --wid points it at the canvas's native window; it is stopped when the viewer closes. */
@Composable
private fun MpvView(f: File, mpv: String, video: Boolean) {
    val canvas = remember(f) {
        object : java.awt.Canvas() {
            var proc: Process? = null
            @Volatile var gone = false
            override fun addNotify() {
                super.addNotify()
                // start once the panel has its final size: mpv takes the window's size as it starts
                javax.swing.Timer(250) { start() }.apply { isRepeats = false }.start()
            }
            fun start() {
                if (proc != null || gone || !isDisplayable) return
                val wid = runCatching { com.sun.jna.Native.getComponentID(this) }.getOrNull() ?: return
                proc = runCatching {
                    ProcessBuilder(
                        mpv, "--wid=$wid", "--no-terminal", "--force-window=yes", "--keep-open=yes", "--idle=no",
                        "--osc=yes", if (video) "--script-opts=osc-visibility=always,osc-hidetimeout=1500" else "--script-opts=osc-visibility=always,osc-layout=bottombar,osc-vidscale=no,osc-scalewindowed=1.2", "--background=color", "--background-color=#000000",
                        "--input-default-bindings=yes", "--input-vo-keyboard=no", "--", f.absolutePath,
                    ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        // under a Wayland session mpv would open its own window and ignore --wid; the app's
                        // windows are X11 (XWayland), so mpv draws through X11 into ours
                        .apply { environment().remove("WAYLAND_DISPLAY"); environment()["XDG_SESSION_TYPE"] = "x11" }
                        .start()
                }.getOrNull()
            }
        }.apply { background = java.awt.Color.BLACK }
    }
    DisposableEffect(f) { onDispose { canvas.gone = true; canvas.proc?.let { p -> p.destroy(); if (!p.waitFor(400, java.util.concurrent.TimeUnit.MILLISECONDS)) p.destroyForcibly() } } }
    if (video) SwingPanel(Color.Black, factory = { canvas }, modifier = Modifier.fillMaxSize().padding(start = 1.dp, end = 1.dp, bottom = 14.dp))
    // audio: the name above, only mpv's control strip below
    else Column(Modifier.fillMaxSize()) {
        val c = Oc.c
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(84.dp).clip(Oc.round(20.dp)).background(c.surfaceHigh), contentAlignment = Alignment.Center) { Icon(Ic.Mic, 34.dp, c.textDim) }
            Spacer(Modifier.height(12.dp))
            Txt(f.nameWithoutExtension, size = 15.sp)
        }
        SwingPanel(Color.Black, factory = { canvas }, modifier = Modifier.fillMaxWidth().height(96.dp).padding(start = 14.dp, end = 14.dp, bottom = 14.dp))
    }
}

/** Video or audio through JavaFX: the picture above, play/pause, a bar to seek and the time under it. */
@Composable
private fun FxMediaView(f: File, video: Boolean) {
    val c = Oc.c
    val panel = remember(f) { JFXPanel() }
    var player by remember(f) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var pos by remember { mutableFloatStateOf(0f) }
    var dur by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(f) {
        Platform.setImplicitExit(false)
        Platform.runLater {
            runCatching {
                val p = MediaPlayer(Media(f.toURI().toString()))
                val view = MediaView(p).apply { isPreserveRatio = true }
                val root = javafx.scene.layout.StackPane(view).apply { style = "-fx-background-color: black;" }
                view.fitWidthProperty().bind(root.widthProperty()); view.fitHeightProperty().bind(root.heightProperty())
                panel.scene = Scene(root)
                p.setOnReady { val d = p.totalDuration.toSeconds().toFloat(); SwingUtilities.invokeLater { dur = d } }
                p.setOnEndOfMedia { p.pause(); p.seek(javafx.util.Duration.ZERO); SwingUtilities.invokeLater { playing = false; pos = 0f } }
                p.setOnError { SwingUtilities.invokeLater { error = p.error?.message ?: "Can't play this file" } }
                p.play()
                SwingUtilities.invokeLater { player = p; playing = true }
            }.onFailure { e -> SwingUtilities.invokeLater { error = e.message ?: "Can't play this file" } }
        }
        onDispose { Platform.runLater { player?.dispose() } }
    }
    LaunchedEffect(player, playing) {
        while (playing) { player?.let { p -> Platform.runLater { val t = p.currentTime.toSeconds().toFloat(); SwingUtilities.invokeLater { pos = t } } }; delay(200) }
    }
    fun toggle() = player?.let { p -> Platform.runLater { if (p.status == MediaPlayer.Status.PLAYING) p.pause() else p.play() }; playing = !playing }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().background(if (video) Color.Black else c.surface), contentAlignment = Alignment.Center) {
            if (video && error == null) SwingPanel(Color.Black, factory = { panel }, modifier = Modifier.fillMaxSize())
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(84.dp).clip(Oc.round(20.dp)).background(c.surfaceHigh), contentAlignment = Alignment.Center) { Icon(if (error != null) Ic.Info else Ic.Mic, 34.dp, c.textDim) }
                Spacer(Modifier.height(12.dp))
                Txt(if (error != null) "This file can't play here." else f.nameWithoutExtension, size = 15.sp, color = if (error != null) c.textDim else c.text)
                if (error != null) {
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.clip(Oc.round(10.dp)).border(1.dp, c.border, Oc.round(10.dp)).hoverTap(Oc.round(10.dp)) { Desktop.open(f.absolutePath) }.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ic.Play, 15.dp, c.text); Spacer(Modifier.width(8.dp)); Txt("Play in the system player", size = 14.sp)
                    }
                }
                // audio still needs the JavaFX panel somewhere in the window to play; it takes no space
                if (!video) SwingPanel(Color.Transparent, factory = { panel }, modifier = Modifier.size(1.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(Oc.pill).background(c.text).hoverTap(Oc.pill) { toggle() }, contentAlignment = Alignment.Center) { Icon(if (playing) Ic.Pause else Ic.Play, 16.dp, c.bg, stroke = 2.2f) }
            Spacer(Modifier.width(14.dp))
            val k = if (dur > 0) (pos / dur).coerceIn(0f, 1f) else 0f
            Canvas(Modifier.weight(1f).height(20.dp).pointerInput(player, dur) {
                detectTapGestures { o -> val t = (o.x / size.width).coerceIn(0f, 1f) * dur; pos = t; player?.let { p -> Platform.runLater { p.seek(javafx.util.Duration.seconds(t.toDouble())) } } }
            }) {
                val y = size.height / 2
                drawRect(c.border, Offset(0f, y - 2f), Size(size.width, 4f))
                drawRect(c.text, Offset(0f, y - 2f), Size(size.width * k, 4f))
            }
            Spacer(Modifier.width(14.dp))
            Txt(time(pos) + " / " + time(dur), size = 12.sp, color = c.textDim, family = Oc.mono)
        }
    }
}

private fun time(s: Float): String { val t = s.toInt(); return "%d:%02d".format(t / 60, t % 60) }

/** A web page or SVG drawn by JavaFX's WebView, from the file itself (its images and styles load next to it). */
@Composable
private fun HtmlPage(f: File) {
    val panel = remember(f) { JFXPanel() }
    DisposableEffect(f) {
        Platform.setImplicitExit(false)
        Platform.runLater { val v = WebView(); panel.scene = Scene(v); v.engine.load(f.toURI().toString()) }
        onDispose { Platform.runLater { (panel.scene?.root as? WebView)?.engine?.load(null) } }
    }
    // a native panel ignores the card's rounded clip: keep it off the bottom corners
    SwingPanel(Color.White, factory = { panel }, modifier = Modifier.fillMaxSize().padding(start = 1.dp, end = 1.dp, bottom = 14.dp))
}

@Composable
private fun OtherFile(f: File) {
    val c = Oc.c
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp).clip(Oc.round(20.dp)).background(c.surfaceHigh), contentAlignment = Alignment.Center) { Icon(Ic.File, 34.dp, c.textDim) }
        Spacer(Modifier.height(14.dp))
        Txt(f.name, size = 16.sp, weight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Txt("cl0udly can't show this kind of file.", size = 13.sp, color = c.textDim)
        Spacer(Modifier.height(18.dp))
        Row(Modifier.clip(Oc.round(10.dp)).border(1.dp, c.border, Oc.round(10.dp)).hoverTap(Oc.round(10.dp)) { Desktop.open(f.absolutePath) }.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.Expand, 15.dp, c.text); Spacer(Modifier.width(8.dp)); Txt("Open with the system app", size = 14.sp)
        }
    }
}

private val VIDEO = setOf("mp4", "webm", "mov", "mkv", "avi")
private val AUDIO = setOf("mp3", "wav", "ogg", "flac", "m4a", "opus", "aac")
fun isMedia(path: String) = path.substringAfterLast('.').lowercase().let { it in VIDEO || it in AUDIO }

/** A video or a sound the agent sent, as a square tile like a picture: play sign, name; a click opens it. */
@Composable
internal fun MediaTile(path: String) {
    val c = Oc.c
    val f = remember(path) { File(path) }
    val video = f.extension.lowercase() in VIDEO
    val shape = Oc.round(18.dp)
    Box(
        Modifier.size(TILE).clip(shape).background(if (video) androidx.compose.ui.graphics.Color.Black else c.surface).border(1.dp, c.border, shape).hoverTap(shape) { FileView.open(path) },
        contentAlignment = Alignment.Center,
    ) {
        if (!video) androidx.compose.foundation.Canvas(Modifier.size(TILE).padding(horizontal = 14.dp, vertical = 30.dp)) {
            val bars = 14; val w = size.width / (bars * 2 - 1)
            for (i in 0 until bars) {
                val h = size.height * (0.25f + ((f.name.hashCode() shr (i % 16)) and 7) / 10f).coerceAtMost(1f)
                drawRoundRect(c.textDim.copy(alpha = 0.5f), androidx.compose.ui.geometry.Offset(i * 2 * w, (size.height - h) / 2), androidx.compose.ui.geometry.Size(w, h), androidx.compose.ui.geometry.CornerRadius(w / 2))
            }
        }
        Box(Modifier.size(34.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (video) androidx.compose.ui.graphics.Color(0xAA000000) else c.text), contentAlignment = Alignment.Center) {
            Icon(Ic.Play, 16.dp, if (video) androidx.compose.ui.graphics.Color.White else c.bg)
        }
        Txt(f.nameWithoutExtension, size = 11.sp, color = if (video) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f) else c.textDim, maxLines = 1,
            modifier = Modifier.align(Alignment.BottomCenter).padding(start = 8.dp, end = 8.dp, bottom = 7.dp))
    }
}

/** A file under a message: icon, name, type and size; a click opens it in the viewer. */
@Composable
internal fun FileCard(path: String) {
    val c = Oc.c
    val f = remember(path) { File(path) }
    Row(
        Modifier.widthIn(max = 420.dp).clip(Oc.round(12.dp)).border(1.dp, c.border, Oc.round(12.dp)).hoverTap(Oc.round(12.dp)) { if (f.isDirectory) Desktop.open(path) else FileView.open(path) }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(Oc.round(8.dp)).background(c.surfaceHigh), contentAlignment = Alignment.Center) { Icon(if (f.isDirectory) Ic.Folder else Ic.File, 16.dp, c.textDim) }
        Spacer(Modifier.width(10.dp))
        Column {
            Txt(f.name, size = 14.sp, weight = FontWeight.Medium, maxLines = 1)
            Txt((if (f.isDirectory) "Folder" else f.extension.uppercase().ifBlank { "File" } + "  " + human(f.length())), size = 12.sp, color = c.textDim, maxLines = 1)
        }
        if (f.isFile) {
            Spacer(Modifier.width(14.dp))
            // a copy in Downloads, where the browser's files go too
            Box(Modifier.size(30.dp).hoverTap(Oc.round(8.dp)) { saveToDownloads(f) }, contentAlignment = Alignment.Center) { Icon(Ic.Download, 16.dp, c.textDim) }
        }
    }
}

/** Copies [f] into the Downloads folder (a number is added if the name is taken) and says where. */
internal fun saveToDownloads(f: File) {
    val home = System.getProperty("user.home")
    val dir = runCatching { ProcessBuilder("xdg-user-dir", "DOWNLOAD").start().inputStream.bufferedReader().readText().trim() }.getOrNull()
        ?.takeIf { it.isNotBlank() && it != home } ?: listOf("$home/Загрузки", "$home/Downloads").firstOrNull { File(it).isDirectory } ?: "$home/Downloads"
    runCatching {
        val d = File(dir).apply { mkdirs() }
        var out = File(d, f.name)
        var i = 1
        while (out.exists()) out = File(d, "${f.nameWithoutExtension} (${i++})" + (if (f.extension.isNotEmpty()) ".${f.extension}" else ""))
        f.copyTo(out)
        app.cl0udly.Graph.hub.toasts.tryEmit("Saved to ${out.path.replace(home, "~")}")
    }.onFailure { app.cl0udly.Graph.hub.toasts.tryEmit("Couldn't save: ${it.message}") }
}
