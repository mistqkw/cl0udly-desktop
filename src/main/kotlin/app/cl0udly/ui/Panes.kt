package app.cl0udly.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cl0udly.Graph
import app.cl0udly.api.arr
import app.cl0udly.api.dbl
import app.cl0udly.api.long
import app.cl0udly.api.str
import app.cl0udly.data.CHAT_DIR
import app.cl0udly.data.Desktop
import app.cl0udly.data.UsageWindow
import app.cl0udly.ui.kit.Ic
import app.cl0udly.ui.kit.Icon
import app.cl0udly.ui.kit.IconButton
import app.cl0udly.ui.kit.Pop
import app.cl0udly.ui.kit.PopDivider
import app.cl0udly.ui.kit.PopHeader
import app.cl0udly.ui.kit.PopItem
import app.cl0udly.ui.kit.Txt
import app.cl0udly.ui.kit.hoverTap
import app.cl0udly.ui.theme.Oc
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.ProcessTtyConnector
import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.TextStyle as TermStyle
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.ui.JediTermWidget
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider
import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import com.pty4j.WinSize
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.embed.swing.SwingFXUtils
import javafx.scene.Scene
import javafx.scene.web.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.awt.Font
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

/** The right-hand panels of Claude desktop: context, terminal, browser, changes and files. */
object Panes {
    /** A file open in the Files panel's preview. */
    var preview by mutableStateOf<String?>(null)
    var width by mutableStateOf(520f)

    val all = listOf(
        Triple("context", Ic.Info, "Context"),
        Triple("terminal", Ic.Terminal, "Terminal"),
        Triple("diff", Ic.Diff, "Changes"),
        Triple("browser", Ic.Globe, "Browser"),
        Triple("files", Ic.Folder, "Files"),
    )

    /** The session on screen, if any. */
    val sessionId: String? get() = (Nav.screen as? Screen.Chat)?.id

    /** The folder the panels work in: the open session's, the open project, or home. */
    val directory: String
        get() = sessionId?.let { Graph.hub.session(it)?.directory }?.takeIf { it != CHAT_DIR }
            ?: Nav.project ?: System.getProperty("user.home")
}

/** The panel with its own header (tabs as icons) and a handle on the left edge to drag its width. */
@Composable
fun PaneHost(modifier: Modifier = Modifier) {
    val c = Oc.c
    val pane = Nav.pane ?: return
    val density = LocalDensity.current
    val drag = rememberDraggableState { d -> Panes.width = (Panes.width - d / density.density).coerceIn(340f, 1100f) }
    Row(modifier.width(Panes.width.dp).fillMaxHeight()) {
        Box(
            Modifier.width(5.dp).fillMaxHeight().pointerHoverIcon(PointerIcon(java.awt.Cursor(java.awt.Cursor.E_RESIZE_CURSOR)))
                .draggable(drag, Orientation.Horizontal),
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.width(1.dp).fillMaxHeight().background(c.border.copy(alpha = 0.6f))) }
        Column(Modifier.weight(1f).fillMaxHeight().background(c.bg)) {
            Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Panes.all.forEach { (id, ic, label) ->
                    Row(
                        Modifier.padding(end = 2.dp).hoverTap(Oc.round(8.dp), pane == id) { Nav.pane = id }.padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(ic, 16.dp, if (pane == id) c.text else c.textDim)
                        if (pane == id) { Spacer(Modifier.width(7.dp)); Txt(label, size = 14.sp) }
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(Ic.Close, { Nav.pane = null }, size = 28.dp, icon = 15.dp)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border.copy(alpha = 0.55f)))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (pane) {
                    "context" -> ContextPane()
                    "terminal" -> TerminalPane()
                    "browser" -> BrowserPane()
                    "diff" -> DiffPane()
                    else -> FilesPane()
                }
            }
        }
    }
}

// ---------- small pieces ----------

@Composable
private fun Section(title: String, trailing: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(title, size = 13.sp, color = Oc.c.textDim, weight = FontWeight.Medium, family = Oc.label, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(1.dp).background(Oc.c.border.copy(alpha = 0.5f)))
}

@Composable
fun Bar(share: Float, modifier: Modifier = Modifier, color: Color = Oc.c.accent) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Oc.c.surfaceHigh)) {
        Box(Modifier.fillMaxWidth(share.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(color))
    }
}

@Composable
private fun KV(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Txt(k, size = 14.sp, color = Oc.c.textDim, modifier = Modifier.weight(1f))
        Txt(v, size = 14.sp, family = Oc.mono)
    }
}

@Composable
fun SmallButton(label: String, ic: Ic? = null, onClick: () -> Unit) {
    val c = Oc.c
    Row(
        Modifier.clip(Oc.round(8.dp)).border(1.dp, c.border, Oc.round(8.dp)).hoverTap(Oc.round(8.dp), onClick = onClick).padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ic != null) { Icon(ic, 15.dp, c.text); Spacer(Modifier.width(7.dp)) }
        Txt(label, size = 14.sp)
    }
}

@Composable
fun Switch(on: Boolean, onChange: (Boolean) -> Unit) {
    val c = Oc.c
    val x by androidx.compose.animation.core.animateFloatAsState(if (on) 1f else 0f, tween(120), label = "switch")
    Box(Modifier.size(36.dp, 21.dp).clip(CircleShape).background(if (on) c.accent else c.surfaceHigh).hoverTap(CircleShape) { onChange(!on) }.padding(3.dp)) {
        Box(Modifier.padding(start = (15 * x).dp).size(15.dp).clip(CircleShape).background(if (on) c.bg else c.textDim))
    }
}

fun tokens(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1e6)
    n >= 1_000 -> "%.1fk".format(n / 1e3)
    else -> n.toString()
}

fun money(d: Double) = if (d < 0.01 && d > 0) "<$0.01" else "$%.2f".format(d)

fun copy(text: String) = runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }

// ---------- context ----------

/** Claude's environment panel: where the session runs, Remote Control, how full the context is, usage. */
@Composable
private fun ContextPane() {
    val c = Oc.c
    val prefs = Graph.prefs
    val id = Panes.sessionId
    val dir = Panes.directory
    val scope = rememberCoroutineScope()
    var todos by remember(id) { mutableStateOf<List<JsonObject>>(emptyList()) }
    LaunchedEffect(id, id?.let { Graph.hub.statuses[it] }) {
        if (id != null) runCatching { todos = Graph.api.todos(id, Graph.hub.session(id)?.directory).filterIsInstance<JsonObject>() }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Section("Environment") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.Folder, 17.dp, c.textDim)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Txt(File(dir).name.ifBlank { dir }, size = 15.sp, maxLines = 1)
                    Txt(dir, size = 12.sp, color = c.textDim, family = Oc.mono, maxLines = 1)
                }
                IconButton(Ic.Expand, { Desktop.open(dir) }, size = 28.dp, icon = 15.dp)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.Server, 17.dp, c.textDim)
                Spacer(Modifier.width(10.dp))
                Txt(if (prefs.remoteUrl.isBlank()) "Local · OpenCode on this computer" else "Remote · ${prefs.remoteUrl}", size = 14.sp, maxLines = 1)
            }
        }
        RemoteControl()
        if (id != null) {
            val use = contextOf(id)
            val msgs = Graph.hub.chat(id).messages
            val last = msgs.lastOrNull { !it.info.isUser && it.info.tokens != null }?.info?.tokens
            Section("Context", trailing = { Txt(if (use.limit > 0) "${(use.share * 100).toInt()}% used" else tokens(use.used), size = 13.sp, color = c.textDim) }) {
                Bar(use.share, color = if (use.share > 0.85f) c.warn else c.accent)
                Spacer(Modifier.height(10.dp))
                if (last != null) {
                    KV("Input", tokens(last.input))
                    KV("Cache read", tokens(last.cache.read))
                    KV("Cache write", tokens(last.cache.write))
                    KV("Output", tokens(last.output))
                    if (last.reasoning > 0) KV("Reasoning", tokens(last.reasoning))
                }
                if (use.limit > 0) KV("Window", tokens(use.limit))
                Spacer(Modifier.height(10.dp))
                SmallButton("Compact session", Ic.Layers) { Graph.hub.compact(id) }
            }
            Section("Session") {
                val cost = msgs.sumOf { it.info.cost ?: 0.0 }
                val sum = msgs.sumOf { it.info.tokens?.sum ?: 0 }
                KV("Messages", msgs.size.toString())
                KV("Tokens", tokens(sum))
                KV("Cost", money(cost))
                Graph.hub.session(id)?.let { s -> KV("Started", ago(s.time.created)) }
                KV("Model", Graph.hub.pick?.label ?: "—")
            }
            if (todos.isNotEmpty()) Section("To-dos") {
                todos.forEach { t ->
                    val st = t.str("status")
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (st == "completed") Ic.Check else if (st == "in_progress") Ic.Play else Ic.ChevronRight, 14.dp, if (st == "completed") c.ok else c.textDim)
                        Spacer(Modifier.width(8.dp))
                        Txt(t.str("content").orEmpty(), size = 14.sp, color = if (st == "completed") c.textDim else c.text)
                    }
                }
            }
        }
        UsageSection()
    }
}

/** The phone joins this computer's server over the local network: a QR with the address and password. */
@Composable
private fun RemoteControl() {
    Section("Remote Control", trailing = { RemoteSwitch() }) { RemoteBody() }
}

@Composable
internal fun RemoteSwitch() {
    val prefs = Graph.prefs
    Switch(prefs.remoteControl) { on ->
        prefs.remoteControl = on
        if (prefs.remoteUrl.isBlank()) Graph.runtime.restart()
    }
}

/**
 * Remote Control: the phone drives this computer's server, so every session here (and the one open
 * now, which the code opens straight away) works from the phone: replies, OKs, helpers. The QR holds
 * the address and the password; a new password shuts out every phone that had the old one.
 * Over the home network it works as is; from anywhere through Tailscale on both devices.
 */
@Composable
internal fun RemoteBody() {
    val c = Oc.c
    val prefs = Graph.prefs
    if (prefs.remoteUrl.isNotBlank()) { Txt("This window drives another computer's server; Remote Control is for this computer's own.", size = 14.sp, color = c.textDim, lineHeight = 20.sp); return }
    if (!prefs.remoteControl) {
        Txt("Drive this computer's sessions from cl0udly on your phone: answer, approve, watch the helpers. At home over Wi-Fi, anywhere through Tailscale.", size = 14.sp, color = c.textDim, lineHeight = 20.sp)
        return
    }
    val addrs = remember { Desktop.addresses() }
    if (addrs.isEmpty()) { Txt("No network found", size = 14.sp, color = c.warn); return }
    var chosen by remember { mutableStateOf(addrs.first().first) }
    val session = (Nav.screen as? Screen.Chat)?.id
    val link = "cl0udly://connect?n=${enc(prefs.name)}&u=${enc("http://$chosen:${prefs.serverPort}")}&p=${enc(prefs.serverPassword)}" + (session?.let { "&s=${enc(it)}" } ?: "")
    val qr = remember(link) { qr(link, 260) }
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.clip(Oc.round(10.dp)).background(Color.White).padding(8.dp)) { Image(qr, null, Modifier.size(150.dp)) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Txt(if (session != null) "Scan with cl0udly on the phone: it opens this chat" else "Scan with cl0udly on the phone", size = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(8.dp))
            addrs.forEach { (ip, what) ->
                Row(Modifier.fillMaxWidth().hoverTap(Oc.round(8.dp), ip == chosen) { chosen = ip }.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (ip == chosen) c.accent else c.border))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Txt(what, size = 13.sp)
                        Txt("$ip:${prefs.serverPort}", size = 12.sp, color = c.textDim, family = Oc.mono)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallButton("Copy link", Ic.Copy) { copy(link) }
                SmallButton("New code", Ic.Key) {
                    prefs.serverPassword = java.util.UUID.randomUUID().toString().replace("-", "").take(24)
                    Graph.runtime.restart()
                    Graph.hub.toasts.tryEmit("New code: phones with the old one are out. Scan again.")
                }
            }
        }
    }
    if (addrs.none { it.second.startsWith("Tailscale") }) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().clip(Oc.round(10.dp)).background(c.surface).hoverTap(Oc.round(10.dp)) { Desktop.open("https://tailscale.com/download") }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.Globe, 16.dp, c.textDim)
            Spacer(Modifier.width(10.dp))
            Txt("Away from home? Install Tailscale (free) on this computer and on the phone and sign in to both: its address appears here and works from anywhere.", size = 13.sp, color = c.textDim, lineHeight = 19.sp, modifier = Modifier.weight(1f))
        }
    }
}

private fun enc(s: String) = java.net.URLEncoder.encode(s, Charsets.UTF_8)

fun qr(text: String, px: Int): ImageBitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, px, px, mapOf(EncodeHintType.MARGIN to 0))
    val img = java.awt.image.BufferedImage(m.width, m.height, java.awt.image.BufferedImage.TYPE_INT_RGB)
    for (y in 0 until m.height) for (x in 0 until m.width) img.setRGB(x, y, if (m[x, y]) 0x000000 else 0xFFFFFF)
    return img.toComposeImageBitmap()
}

@Composable
fun UsageSection() {
    val snap by Graph.usage.snapshot.collectAsState()
    Section("Usage", trailing = { Txt("counted on this computer", size = 12.sp, color = Oc.c.textDim) }) {
        listOf(snap.fiveHour, snap.week, snap.month).forEach { UsageRow(it) }
    }
}

@Composable
fun UsageRow(w: UsageWindow) {
    val c = Oc.c
    Column(Modifier.padding(vertical = 6.dp)) {
        Row {
            Txt(w.label, size = 14.sp, modifier = Modifier.weight(1f))
            Txt("${money(w.used)} of ${money(w.limit)}", size = 13.sp, color = c.textDim, family = Oc.mono)
        }
        Spacer(Modifier.height(6.dp))
        Bar(w.fraction.toFloat(), color = if (w.fraction > 0.85) c.warn else c.accent)
        w.resetAt?.let { Txt("Resets ${resetIn(it)}", size = 12.sp, color = c.textDim, modifier = Modifier.padding(top = 4.dp)) }
    }
}

fun resetIn(at: Long): String {
    val m = ((at - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)
    return when {
        m < 60 -> "in $m min"
        m < 48 * 60 -> "in ${m / 60} h ${m % 60} min"
        else -> "in ${m / 1440} days"
    }
}

/** Something is drawn over the panel (a menu, settings, search): Swing would paint through it. */
private val covered: Boolean
    get() = app.cl0udly.ui.kit.Overlay.count > 0 || Nav.settings != null || Nav.search || Nav.quick || Nav.picker != null

/**
 * A Swing view in the panel. While something covers it, it is swapped for a picture of itself,
 * so menus and dialogs stay on top and the panel doesn't go blank.
 */
@Composable
private fun Swing(view: java.awt.Component, bg: Color, modifier: Modifier) {
    var shot by remember(view) { mutableStateOf<ImageBitmap?>(null) }
    val cover = covered
    if (cover) {
        LaunchedEffect(view) {
            if (shot == null && view.width > 0 && view.height > 0) shot = runCatching {
                val img = java.awt.image.BufferedImage(view.width, view.height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                val g = img.createGraphics(); view.paint(g); g.dispose(); img.toComposeImageBitmap()
            }.getOrNull()
        }
        Box(modifier.background(bg)) {
            shot?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.FillBounds) }
        }
    } else {
        LaunchedEffect(view) { shot = null }
        SwingPanel(bg, factory = { view }, modifier = modifier)
    }
}

// ---------- terminal ----------

/** Colors the terminal reads on every paint, so it follows the look. */
private object TermColors {
    @Volatile var fg = com.jediterm.core.Color(0xE4, 0xE7, 0xEB)
    @Volatile var bg = com.jediterm.core.Color(0x0B, 0x0C, 0x0E)
}

private class PtyConnector(private val p: PtyProcess) : ProcessTtyConnector(p, Charsets.UTF_8) {
    override fun getName() = "shell"
    override fun isConnected() = p.isAlive
    override fun resize(size: TermSize) { if (p.isAlive) p.winSize = WinSize(size.columns, size.rows) }
}

private class Term(val dir: String) {
    var title by mutableStateOf(File(dir).name.ifBlank { "~" })
    val widget = JediTermWidget(100, 30, Settings())

    init {
        val shell = Graph.prefs.terminalShell.ifBlank { System.getenv("SHELL") ?: "/bin/bash" }
        val env = HashMap(System.getenv()).apply { put("TERM", "xterm-256color"); put("COLORTERM", "truecolor"); put("CL0UDLY", "1") }
        val p = PtyProcessBuilder(arrayOf(shell, "-l")).setDirectory(dir).setEnvironment(env).setInitialColumns(100).setInitialRows(30).start()
        widget.ttyConnector = PtyConnector(p)
        // a thin scrollbar in the look's colors instead of Swing's blue one
        scrollBars(widget).forEach { bar ->
            bar.preferredSize = java.awt.Dimension(8, 0)
            bar.setUI(object : javax.swing.plaf.basic.BasicScrollBarUI() {
                override fun configureScrollBarColors() { thumbColor = java.awt.Color(0x80808080.toInt(), true); trackColor = java.awt.Color(0, 0, 0, 0) }
                override fun createDecreaseButton(o: Int) = javax.swing.JButton().apply { preferredSize = java.awt.Dimension(0, 0) }
                override fun createIncreaseButton(o: Int) = javax.swing.JButton().apply { preferredSize = java.awt.Dimension(0, 0) }
                override fun paintTrack(g: java.awt.Graphics, c: javax.swing.JComponent, r: java.awt.Rectangle) {
                    g.color = java.awt.Color(TermColors.bg.red, TermColors.bg.green, TermColors.bg.blue); g.fillRect(r.x, r.y, r.width, r.height)
                }
                override fun paintThumb(g: java.awt.Graphics, c: javax.swing.JComponent, r: java.awt.Rectangle) {
                    if (r.isEmpty) return
                    val g2 = g as java.awt.Graphics2D
                    g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.color = thumbColor; g2.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 4, 4)
                }
            })
        }
        widget.start()
    }

    fun close() = runCatching { widget.close() }

    class Settings : DefaultSettingsProvider() {
        override fun getTerminalFont(): Font =
            Font(if ("JetBrains Mono" in java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames) "JetBrains Mono" else Font.MONOSPACED, Font.PLAIN, 14)
        override fun getTerminalFontSize() = 14f
        override fun useAntialiasing() = true
        override fun audibleBell() = false
        override fun getBufferMaxLinesCount() = 10_000
        // read on every paint, so the terminal follows the look
        override fun getDefaultForeground() = TerminalColor { TermColors.fg }
        override fun getDefaultBackground() = TerminalColor { TermColors.bg }
        override fun getDefaultStyle() = TermStyle(getDefaultForeground(), getDefaultBackground())
    }
}

private fun scrollBars(c: java.awt.Container): List<javax.swing.JScrollBar> =
    c.components.flatMap { if (it is javax.swing.JScrollBar) listOf(it) else if (it is java.awt.Container) scrollBars(it) else emptyList() }

private object Terms {
    val list = mutableStateListOf<Term>()
    var current by mutableStateOf(0)
    fun add(dir: String) {
        runCatching { Term(dir) }.onSuccess { list.add(it); current = list.lastIndex }
            .onFailure { Graph.hub.toasts.tryEmit("Could not start a terminal: ${it.message}") }
    }
    fun close(i: Int) { list.getOrNull(i)?.close(); if (i in list.indices) list.removeAt(i); current = current.coerceAtMost(list.lastIndex).coerceAtLeast(0) }
}

@Composable
private fun TerminalPane() {
    val c = Oc.c
    SideEffect {
        TermColors.fg = com.jediterm.core.Color(c.text.toArgb() and 0xFFFFFF)
        TermColors.bg = com.jediterm.core.Color(c.bg.toArgb() and 0xFFFFFF)
    }
    LaunchedEffect(Unit) { if (Terms.list.isEmpty()) Terms.add(Panes.directory) }
    Column(Modifier.fillMaxSize()) {
        Tabs(Terms.list.map { it.title }, Terms.current, { Terms.current = it }, { Terms.close(it) }) { Terms.add(Panes.directory) }
        val t = Terms.list.getOrNull(Terms.current)
        if (t == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { SmallButton("New terminal", Ic.Plus) { Terms.add(Panes.directory) } }
        else androidx.compose.runtime.key(t) {
            Swing(t.widget, c.bg, Modifier.fillMaxSize().padding(start = 8.dp, top = 6.dp))
            LaunchedEffect(t) { delay(60); t.widget.requestFocusInWindow() }
        }
    }
}

/** Browser-like tabs over a panel: title, close, and a "+" at the end. */
@Composable
private fun Tabs(titles: List<String>, current: Int, onPick: (Int) -> Unit, onClose: (Int) -> Unit, onAdd: () -> Unit) {
    val c = Oc.c
    Row(Modifier.fillMaxWidth().height(38.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        titles.forEachIndexed { i, t ->
            Row(
                Modifier.padding(end = 4.dp).widthIn(max = 200.dp).hoverTap(Oc.round(8.dp), i == current) { onPick(i) }.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Txt(t, size = 13.sp, color = if (i == current) c.text else c.textDim, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                IconButton(Ic.Close, { onClose(i) }, size = 20.dp, icon = 12.dp)
            }
        }
        IconButton(Ic.Plus, onAdd, size = 26.dp, icon = 15.dp)
    }
}

// ---------- browser ----------

private class WebTab(start: String) {
    val panel = JFXPanel()
    var url by mutableStateOf(start)
    var title by mutableStateOf("New tab")
    var loading by mutableStateOf(false)
    var canBack by mutableStateOf(false)
    var canForward by mutableStateOf(false)
    @Volatile var view: WebView? = null

    init {
        Platform.setImplicitExit(false)
        Platform.runLater {
            val v = WebView()
            val e = v.engine
            e.locationProperty().addListener { _, _, n -> ui { url = n ?: "" } }
            e.titleProperty().addListener { _, _, n -> ui { title = n?.takeIf { it.isNotBlank() } ?: url.removePrefix("http://").removePrefix("https://") } }
            e.loadWorker.stateProperty().addListener { _, _, s ->
                val h = e.history
                ui { loading = s == Worker.State.RUNNING || s == Worker.State.SCHEDULED; canBack = h.currentIndex > 0; canForward = h.currentIndex < h.entries.size - 1 }
            }
            view = v
            panel.scene = Scene(v)
            if (start.isNotBlank()) e.load(start)
        }
    }

    private fun ui(f: () -> Unit) = SwingUtilities.invokeLater(f)
    private fun fx(f: (WebView) -> Unit) = Platform.runLater { view?.let(f) }

    fun load(u: String) { val n = normalize(u); url = n; fx { it.engine.load(n) } }
    fun back() = fx { if (it.engine.history.currentIndex > 0) it.engine.history.go(-1) }
    fun forward() = fx { val h = it.engine.history; if (h.currentIndex < h.entries.size - 1) h.go(1) }
    fun reload() = fx { it.engine.reload() }
    fun stop() = fx { it.engine.loadWorker.cancel() }
    fun zoom(z: Double) = fx { it.zoom = z }

    fun screenshot(to: File, done: (Boolean) -> Unit) = fx {
        val ok = runCatching { ImageIO.write(SwingFXUtils.fromFXImage(it.snapshot(null, null), null), "png", to) }.isSuccess
        ui { done(ok) }
    }

    companion object {
        fun normalize(u: String): String {
            val t = u.trim()
            return when {
                t.isBlank() -> "about:blank"
                Regex("^[a-z][a-z0-9+.-]*:").containsMatchIn(t) -> t
                t.startsWith("localhost") || t.startsWith("127.") || Regex("^[\\w.-]+:\\d+").containsMatchIn(t) -> "http://$t"
                t.startsWith("/") -> File(t).toURI().toString()
                ' ' !in t && '.' in t -> "https://$t"
                else -> "https://duckduckgo.com/html/?q=" + enc(t)
            }
        }
    }
}

private object Webs {
    val list = mutableStateListOf<WebTab>()
    var current by mutableStateOf(0)
    var viewport by mutableStateOf("desktop")
    fun add(url: String = "") { list.add(WebTab(url)); current = list.lastIndex }
    fun close(i: Int) {
        val t = list.getOrNull(i) ?: return
        Platform.runLater { t.view?.engine?.load(null) }
        list.removeAt(i); current = current.coerceAtMost(list.lastIndex).coerceAtLeast(0)
    }
}

/** Servers listening on this computer (a dev server, a preview): ss lists them with the program's name. */
private data class LocalServer(val port: Int, val program: String)

private fun localServers(): List<LocalServer> = runCatching {
    val out = ProcessBuilder("ss", "-ltnpH").redirectErrorStream(true).start().inputStream.bufferedReader().readText()
    val own = Graph.prefs.serverPort.toIntOrNull()
    out.lines().mapNotNull { l ->
        val cols = l.trim().split(Regex("\\s+"))
        val local = cols.getOrNull(3) ?: return@mapNotNull null
        val port = local.substringAfterLast(':').toIntOrNull() ?: return@mapNotNull null
        if (port < 1024 || port == own) return@mapNotNull null
        val host = local.substringBeforeLast(':')
        if (host !in setOf("127.0.0.1", "0.0.0.0", "*", "[::]", "[::1]", "::1")) return@mapNotNull null
        LocalServer(port, Regex("\"([^\"]+)\"").find(l)?.groupValues?.get(1) ?: "")
    }.distinctBy { it.port }.sortedBy { it.port }
}.getOrDefault(emptyList())

@Composable
private fun BrowserPane() {
    val c = Oc.c
    val scope = rememberCoroutineScope()
    LaunchedEffect(Nav.browse) {
        Nav.browse?.let { u -> val t = Webs.list.getOrNull(Webs.current); if (t == null || t.url.isNotBlank() && t.url != "about:blank") Webs.add(u) else t.load(u); Nav.browse = null }
    }
    LaunchedEffect(Unit) { if (Webs.list.isEmpty()) Webs.add() }
    val tab = Webs.list.getOrNull(Webs.current)
    Column(Modifier.fillMaxSize()) {
        Tabs(Webs.list.map { it.title }, Webs.current, { Webs.current = it }, { Webs.close(it) }) { Webs.add() }
        if (tab == null) return@Column
        androidx.compose.runtime.key(tab) {
            var field by remember { mutableStateOf(tab.url) }
            LaunchedEffect(tab.url) { field = tab.url }
            var menu by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(Ic.Back, { tab.back() }, size = 28.dp, icon = 16.dp, tint = if (tab.canBack) c.textDim else c.textDim.copy(alpha = 0.35f))
                IconButton(Ic.Forward, { tab.forward() }, size = 28.dp, icon = 16.dp, tint = if (tab.canForward) c.textDim else c.textDim.copy(alpha = 0.35f))
                IconButton(if (tab.loading) Ic.Close else Ic.Retry, { if (tab.loading) tab.stop() else tab.reload() }, size = 28.dp, icon = 15.dp)
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f).height(32.dp).clip(Oc.round(8.dp)).background(c.surface).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
                    BasicTextField(
                        field, { field = it }, singleLine = true,
                        textStyle = TextStyle(color = c.text, fontSize = 14.sp, fontFamily = Oc.mono),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.fillMaxWidth().onPreviewKeyEvent {
                            if (it.type == KeyEventType.KeyDown && it.key == Key.Enter) { tab.load(field); true } else false
                        },
                    )
                    if (field.isBlank()) Txt("Address, localhost:3000 or a search", size = 14.sp, color = c.textDim)
                }
                Spacer(Modifier.width(4.dp))
                Box {
                    IconButton(Ic.More, { menu = true }, size = 28.dp)
                    Pop(menu, { menu = false }, minWidth = 240.dp) {
                        PopItem("Open in default browser", Ic.Expand) { menu = false; Desktop.open(tab.url) }
                        PopItem("Copy URL", Ic.Copy) { menu = false; copy(tab.url) }
                        PopItem("Save screenshot", Ic.Image) {
                            menu = false
                            val f = File(System.getProperty("user.home"), "Изображения/cl0udly-${System.currentTimeMillis()}.png").also { it.parentFile.mkdirs() }
                            tab.screenshot(f) { ok -> Graph.hub.toasts.tryEmit(if (ok) "Saved ${f.path}" else "Could not save the screenshot") }
                        }
                        PopItem("Open HTML file…", Ic.File) { menu = false; Pickers.files("Open an HTML file") { it.firstOrNull()?.let { p -> tab.load(p) } } }
                        PopItem("Viewport", Ic.Phone, submenu = { close ->
                            listOf("desktop" to "Desktop", "tablet" to "Tablet · 768", "mobile" to "Phone · 390").forEach { (id, l) ->
                                PopItem(l, checked = Webs.viewport == id) { close(); menu = false; Webs.viewport = id }
                            }
                        })
                        PopItem("Zoom", Ic.Search, submenu = { close ->
                            listOf(0.75, 0.9, 1.0, 1.25).forEach { z -> PopItem("${(z * 100).toInt()}%") { close(); menu = false; tab.zoom(z) } }
                        })
                        PopDivider()
                        PopItem("Clear browsing data", Ic.Trash, danger = true) {
                            menu = false
                            Platform.runLater { java.net.CookieHandler.setDefault(java.net.CookieManager()) }
                            Graph.hub.toasts.tryEmit("Cookies cleared")
                        }
                    }
                }
            }
            if (tab.url.isBlank() || tab.url == "about:blank") LocalServers { tab.load("http://localhost:$it") }
            else Box(Modifier.fillMaxSize().background(c.surface), contentAlignment = Alignment.TopCenter) {
                val w = when (Webs.viewport) { "tablet" -> 768.dp; "mobile" -> 390.dp; else -> null }
                Swing(tab.panel, Color.White, if (w == null) Modifier.fillMaxSize() else Modifier.width(w).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun LocalServers(onOpen: (Int) -> Unit) {
    val c = Oc.c
    var list by remember { mutableStateOf<List<LocalServer>>(emptyList()) }
    LaunchedEffect(Unit) { while (true) { list = withContext(Dispatchers.IO) { localServers() }; delay(3000) } }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Txt("Local servers", size = 13.sp, color = c.textDim, weight = FontWeight.Medium, family = Oc.label)
        Spacer(Modifier.height(10.dp))
        if (list.isEmpty()) Txt("Nothing is listening yet. Start a dev server in the terminal and it shows up here.", size = 14.sp, color = c.textDim, lineHeight = 20.sp)
        list.forEach { s ->
            Row(
                Modifier.fillMaxWidth().height(40.dp).hoverTap(Oc.round(8.dp)) { onOpen(s.port) }.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(c.ok))
                Spacer(Modifier.width(10.dp))
                Txt("localhost:${s.port}", size = 14.sp, family = Oc.mono, modifier = Modifier.weight(1f))
                Txt(s.program, size = 13.sp, color = c.textDim)
                Spacer(Modifier.width(8.dp))
                Icon(Ic.Play, 14.dp, c.textDim)
            }
        }
    }
}

// ---------- changes ----------

private data class FileChange(val file: String, val before: String, val after: String, val add: Long, val del: Long)

@Composable
private fun DiffPane() {
    val c = Oc.c
    val id = Panes.sessionId
    var list by remember(id) { mutableStateOf<List<FileChange>?>(null) }
    var open by remember(id) { mutableStateOf<String?>(null) }
    LaunchedEffect(id, id?.let { Graph.hub.statuses[it] }) {
        if (id == null) return@LaunchedEffect
        list = runCatching {
            Graph.api.diff(id, Graph.hub.session(id)?.directory).filterIsInstance<JsonObject>().map {
                FileChange(it.str("file").orEmpty(), it.str("before").orEmpty(), it.str("after").orEmpty(), it.long("additions") ?: 0, it.long("deletions") ?: 0)
            }
        }.getOrDefault(emptyList())
    }
    if (id == null) { Empty("Open a session to see what it changed"); return }
    val l = list ?: return
    if (l.isEmpty()) { Empty("No changes in this session yet"); return }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Txt("${l.size} files", size = 14.sp, modifier = Modifier.weight(1f))
                Txt("+${l.sumOf { it.add }}", size = 13.sp, color = c.ok, family = Oc.mono)
                Spacer(Modifier.width(8.dp))
                Txt("−${l.sumOf { it.del }}", size = 13.sp, color = c.danger, family = Oc.mono)
            }
        }
        l.forEach { f ->
            item(key = "h:" + f.file) {
                Row(
                    Modifier.fillMaxWidth().height(36.dp).hoverTap(Oc.round(0.dp), open == f.file) { open = if (open == f.file) null else f.file }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (open == f.file) Ic.Chevron else Ic.ChevronRight, 14.dp, c.textDim)
                    Spacer(Modifier.width(8.dp))
                    Txt(f.file, size = 14.sp, family = Oc.mono, maxLines = 1, modifier = Modifier.weight(1f))
                    Txt("+${f.add}", size = 12.sp, color = c.ok, family = Oc.mono)
                    Spacer(Modifier.width(6.dp))
                    Txt("−${f.del}", size = 12.sp, color = c.danger, family = Oc.mono)
                }
            }
            if (open == f.file) {
                val lines = diffLines(f.before, f.after)
                items(lines.size, key = { "l:${f.file}:$it" }) { i ->
                    val (k, t) = lines[i]
                    val bg = when (k) { '+' -> c.ok.copy(alpha = 0.12f); '-' -> c.danger.copy(alpha = 0.12f); else -> Color.Transparent }
                    Txt(
                        (if (k == '~') "  ⋯" else "$k $t"), size = 12.5.sp, family = Oc.mono, maxLines = 1,
                        color = when (k) { '+' -> c.ok; '-' -> c.danger; '~' -> c.textDim; else -> c.text.copy(alpha = 0.85f) },
                        modifier = Modifier.fillMaxWidth().background(bg).padding(horizontal = 16.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Empty(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Txt(text, size = 14.sp, color = Oc.c.textDim) }
}

/** A line diff (LCS) with three lines of context around each change; '~' marks skipped lines. */
fun diffLines(a: String, b: String, context: Int = 3): List<Pair<Char, String>> {
    val x = a.lines(); val y = b.lines()
    if (x.size.toLong() * y.size > 4_000_000) return y.map { '+' to it }
    val n = x.size; val m = y.size
    val dp = Array(n + 1) { IntArray(m + 1) }
    for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) dp[i][j] = if (x[i] == y[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
    val all = ArrayList<Pair<Char, String>>()
    var i = 0; var j = 0
    while (i < n && j < m) when {
        x[i] == y[j] -> { all.add(' ' to x[i]); i++; j++ }
        dp[i + 1][j] >= dp[i][j + 1] -> { all.add('-' to x[i]); i++ }
        else -> { all.add('+' to y[j]); j++ }
    }
    while (i < n) all.add('-' to x[i++])
    while (j < m) all.add('+' to y[j++])
    val keep = BooleanArray(all.size)
    all.forEachIndexed { k, (t, _) -> if (t != ' ') for (q in (k - context).coerceAtLeast(0)..(k + context).coerceAtMost(all.lastIndex)) keep[q] = true }
    val out = ArrayList<Pair<Char, String>>()
    var skipped = false
    all.forEachIndexed { k, p -> if (keep[k]) { out.add(p); skipped = false } else if (!skipped) { out.add('~' to ""); skipped = true } }
    return out
}

// ---------- files ----------

@Composable
private fun FilesPane() {
    val c = Oc.c
    val root = Panes.directory
    val expanded = remember(root) { mutableStateListOf<String>() }
    var showHidden by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }
    val preview = Panes.preview
    AnimatedContent(preview, transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(90)) }, label = "files") { p ->
        if (p != null) FilePreview(File(p)) { Panes.preview = null }
        else Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt(File(root).name.ifBlank { root }, size = 14.sp, weight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
                IconButton(Ic.Lock, { showHidden = !showHidden }, size = 26.dp, icon = 14.dp, selected = showHidden)
                IconButton(Ic.Retry, { tick++ }, size = 26.dp, icon = 14.dp)
                IconButton(Ic.Expand, { Desktop.open(root) }, size = 26.dp, icon = 14.dp)
            }
            val rows = remember(root, expanded.toList(), showHidden, tick) { tree(File(root), expanded.toSet(), showHidden) }
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
                items(rows, key = { it.first.path }) { (f, depth) ->
                    val dir = f.isDirectory
                    Row(
                        Modifier.fillMaxWidth().height(30.dp).hoverTap(Oc.round(6.dp)) {
                            if (dir) { if (f.path in expanded) expanded.remove(f.path) else expanded.add(f.path) } else Panes.preview = f.path
                        }.padding(start = (8 + depth * 14).dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (dir) Icon(if (f.path in expanded) Ic.Chevron else Ic.ChevronRight, 13.dp, c.textDim) else Spacer(Modifier.width(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Icon(if (dir) Ic.Folder else iconFor(f.name), 15.dp, c.textDim)
                        Spacer(Modifier.width(8.dp))
                        Txt(f.name, size = 14.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

private val SKIP = setOf(".git", "node_modules", ".gradle", "build", ".idea", "__pycache__", ".venv", "target", "dist")

private fun tree(root: File, open: Set<String>, hidden: Boolean): List<Pair<File, Int>> {
    val out = ArrayList<Pair<File, Int>>()
    fun walk(d: File, depth: Int) {
        if (out.size > 4000) return
        val kids = d.listFiles()?.filter { hidden || !it.name.startsWith(".") }?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: return
        kids.forEach { f ->
            out.add(f to depth)
            if (f.isDirectory && f.path in open && (f.name !in SKIP || f.path in open)) walk(f, depth + 1)
        }
    }
    walk(root, 0)
    return out
}

private val IMAGES = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")

@Composable
private fun FilePreview(f: File, onBack: () -> Unit) {
    val c = Oc.c
    val ext = f.extension.lowercase()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(Ic.Back, onBack, size = 28.dp, icon = 16.dp)
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Txt(f.name, size = 14.sp, weight = FontWeight.Medium, maxLines = 1)
                Txt(size(f.length()), size = 12.sp, color = c.textDim)
            }
            IconButton(Ic.At, { ComposerPrefill.text = "@" + f.relativeToOrSelf(File(Panes.directory)).path + " " }, size = 28.dp, icon = 15.dp)
            if (ext == "html" || ext == "htm") IconButton(Ic.Globe, { Nav.openUrl(f.toURI().toString()) }, size = 28.dp, icon = 15.dp)
            IconButton(Ic.Copy, { copy(f.path) }, size = 28.dp, icon = 15.dp)
            IconButton(Ic.Expand, { Desktop.open(f.path) }, size = 28.dp, icon = 15.dp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border.copy(alpha = 0.5f)))
        if (ext in IMAGES) {
            val img = remember(f.path) { runCatching { org.jetbrains.skia.Image.makeFromEncoded(f.readBytes()).toComposeImageBitmap() }.getOrNull() }
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                if (img != null) Image(img, null) else Txt("Could not open the image", size = 14.sp, color = c.textDim)
            }
            return@Column
        }
        val lines = remember(f.path) {
            runCatching {
                val bytes = f.inputStream().use { it.readNBytes(400_000) }
                if (bytes.take(4000).any { it == 0.toByte() }) null else String(bytes, Charsets.UTF_8).lines().take(5000)
            }.getOrNull()
        }
        if (lines == null) { Empty("A binary file — open it in its own app"); return@Column }
        val pad = lines.size.toString().length
        LazyColumn(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
            itemsIndexed(lines) { i, l ->
                Row(Modifier.padding(horizontal = 10.dp)) {
                    Txt((i + 1).toString().padStart(pad), size = 12.5.sp, family = Oc.mono, color = c.textDim.copy(alpha = 0.6f))
                    Spacer(Modifier.width(12.dp))
                    Txt(l.replace("\t", "    "), size = 12.5.sp, family = Oc.mono, maxLines = 1)
                }
            }
        }
    }
}
