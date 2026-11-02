package app.cl0udly.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cl0udly.Graph
import app.cl0udly.api.AllProvider
import app.cl0udly.runtime.RuntimeController
import app.cl0udly.runtime.RuntimeState
import app.cl0udly.ui.cloud.CloudBackdrop
import app.cl0udly.ui.cloud.PixelCloud
import app.cl0udly.ui.kit.Ic
import app.cl0udly.ui.kit.Icon
import app.cl0udly.ui.kit.Txt
import app.cl0udly.ui.kit.hoverTap
import app.cl0udly.ui.theme.Look
import app.cl0udly.ui.theme.Looks
import app.cl0udly.ui.theme.Oc
import kotlinx.coroutines.launch

private val SECTIONS = listOf(
    Triple("general", Ic.User, "General"),
    Triple("appearance", Ic.Palette, "Appearance"),
    Triple("mascot", Ic.Cloud, "cl0udly"),
    Triple("models", Ic.Box, "Models"),
    Triple("server", Ic.Server, "Server"),
    Triple("about", Ic.Info, "About"),
)

/** Claude desktop's settings: a list of sections on the left, the section on the right, one column. */
@Composable
fun Settings(section: String) {
    val c = Oc.c
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(240.dp).fillMaxHeight().padding(start = 24.dp, top = 30.dp, end = 12.dp)) {
            Txt("Settings", size = 26.sp, family = Oc.heading, weight = FontWeight.Medium, modifier = Modifier.padding(start = 10.dp, bottom = 18.dp))
            SECTIONS.forEach { (id, ic, label) ->
                Row(
                    Modifier.fillMaxWidth().height(40.dp).hoverTap(Oc.round(M.listRadius), id == section) { Nav.go(Screen.Settings(id)) }.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(ic, 18.dp, if (id == section) c.text else c.textDim)
                    Spacer(Modifier.width(12.dp))
                    Txt(label, size = 15.sp, color = if (id == section) c.text else c.textDim)
                }
            }
        }
        AnimatedContent(section, transitionSpec = { fadeIn(tween(130)) togetherWith fadeOut(tween(90)) }, label = "section", modifier = Modifier.weight(1f)) { s ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 32.dp, end = 40.dp, top = 34.dp, bottom = 60.dp)) {
                Column(Modifier.widthIn(max = 760.dp)) {
                    when (s) {
                        "general" -> General()
                        "appearance" -> Appearance()
                        "mascot" -> Mascot()
                        "models" -> Models()
                        "server" -> Server()
                        else -> About()
                    }
                }
            }
        }
    }
}

@Composable
private fun Head(title: String, sub: String? = null) {
    Txt(title, size = 22.sp, weight = FontWeight.SemiBold, family = Oc.heading)
    if (sub != null) { Spacer(Modifier.height(6.dp)); Txt(sub, size = 14.sp, color = Oc.c.textDim, lineHeight = 21.sp) }
    Spacer(Modifier.height(22.dp))
}

@Composable
private fun Group(title: String? = null, content: @Composable () -> Unit) {
    val c = Oc.c
    if (title != null) Txt(title, size = 13.sp, color = c.textDim, weight = FontWeight.Medium, family = Oc.label, modifier = Modifier.padding(bottom = 8.dp, start = 2.dp))
    Column(Modifier.fillMaxWidth().clip(Oc.round(14.dp)).border(1.dp, c.border, Oc.round(14.dp))) { content() }
    Spacer(Modifier.height(26.dp))
}

@Composable
private fun SettingRow(title: String, sub: String? = null, last: Boolean = false, trailing: @Composable () -> Unit) {
    val c = Oc.c
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Txt(title, size = 15.sp)
            if (sub != null) Txt(sub, size = 13.sp, color = c.textDim, lineHeight = 19.sp)
        }
        Spacer(Modifier.width(16.dp))
        trailing()
    }
    if (!last) Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(1.dp).background(c.border.copy(alpha = 0.6f)))
}

@Composable
private fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val c = Oc.c
    val x by animateFloatAsState(if (on) 1f else 0f, tween(120), label = "toggle")
    Box(Modifier.size(40.dp, 24.dp).clip(CircleShape).background(if (on) c.accent else c.surfaceHigh).hoverTap(CircleShape) { onChange(!on) }.padding(3.dp)) {
        Box(Modifier.offset(x = (16 * x).dp).size(18.dp).clip(CircleShape).background(if (on) c.bg else c.textDim))
    }
}

@Composable
private fun Segmented(options: List<Pair<String, String>>, value: String, onPick: (String) -> Unit) {
    val c = Oc.c
    Row(Modifier.clip(Oc.round(10.dp)).background(c.surface).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        options.forEach { (id, label) ->
            Box(Modifier.hoverTap(Oc.round(8.dp), value == id) { onPick(id) }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Txt(label, size = 14.sp, color = if (value == id) c.text else c.textDim)
            }
        }
    }
}

// ---------- sections ----------

@Composable
private fun General() {
    val prefs = Graph.prefs
    Head("General")
    Group("Profile") {
        SettingRow("What should cl0udly call you?", "Used in the greeting.", last = true) {
            Field(prefs.name, { prefs.name = it }, "Your name", Modifier.width(220.dp).clip(Oc.round(8.dp)).border(1.dp, Oc.c.border, Oc.round(8.dp)).padding(10.dp), size = 14)
        }
    }
    Group("Look") {
        SettingRow("Color mode") { Segmented(listOf("system" to "System", "light" to "Light", "dark" to "Dark"), prefs.colorMode) { prefs.colorMode = it } }
        SettingRow("Text size", last = true) { Segmented(listOf("small" to "Small", "default" to "Default", "large" to "Large"), prefs.textSize) { prefs.textSize = it } }
    }
    Group("Keyboard") {
        listOf(
            "New chat" to "Ctrl+N", "Open a project" to "Ctrl+O", "Search" to "Ctrl+K", "Quick entry" to "Ctrl+Shift+Space",
            "Toggle sidebar" to "Ctrl+B", "Settings" to "Ctrl+,", "Stop the reply" to "Esc",
        ).let { keys ->
            keys.forEachIndexed { i, (what, key) ->
                SettingRow(what, last = i == keys.lastIndex) { Txt(key, size = 13.sp, family = Oc.mono, color = Oc.c.textDim) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Appearance() {
    val prefs = Graph.prefs
    Head("Appearance", "The window keeps Claude desktop's frame; a look repaints it and changes fonts and messages.")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Looks.all.forEach { l -> LookCard(l, prefs.edition == l.id) { prefs.edition = l.id } }
    }
}

/** A tiny window in the look's colors: sidebar, a bubble, a reply and the input. */
@Composable
private fun LookCard(l: Look, selected: Boolean, onClick: () -> Unit) {
    val c = Oc.c
    val p = if (c.isDark) l.dark else l.light
    Column(
        Modifier.width(232.dp).clip(Oc.round(14.dp)).border(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.border, Oc.round(14.dp))
            .hoverTap(Oc.round(14.dp), onClick = onClick).padding(10.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(128.dp).clip(Oc.round(9.dp)).background(p.bg)) {
            if (l.clouds) Box(Modifier.fillMaxSize().then(with(CloudBackdrop) { Modifier.backdrop(p.bg, p.isDark, "card-${l.id}", true) }))
            Canvas(Modifier.fillMaxSize()) {
                val k = size.width / 232f
                val r = CornerRadius(5f * k * l.radius.coerceAtLeast(0.2f))
                drawRect(p.sidebar, Offset.Zero, Size(58f * k, size.height))
                repeat(4) { i -> drawRoundRect(p.textDim.copy(alpha = 0.5f), Offset(8f * k, (16f + i * 13f) * k), Size((30f + (i % 2) * 10f) * k, 4f * k), CornerRadius(2f * k)) }
                if (l.user == "line") drawRoundRect(p.border, Offset(140f * k, 14f * k), Size(78f * k, 20f * k), r, style = androidx.compose.ui.graphics.drawscope.Stroke(1.2f))
                else drawRoundRect(p.userBubble, Offset(140f * k, 14f * k), Size(78f * k, 20f * k), r)
                repeat(3) { i -> drawRoundRect(p.text.copy(alpha = 0.7f), Offset(72f * k, (46f + i * 10f) * k), Size((128f - i * 30f) * k, 4f * k), CornerRadius(2f * k)) }
                drawRoundRect(p.surface, Offset(68f * k, 94f * k), Size(156f * k, 24f * k), CornerRadius(7f * k * l.radius.coerceAtLeast(0.1f)))
                drawRoundRect(p.border, Offset(68f * k, 94f * k), Size(156f * k, 24f * k), CornerRadius(7f * k * l.radius.coerceAtLeast(0.1f)), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
                drawCircle(p.accent, 5f * k, Offset(212f * k, 106f * k))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(l.title, size = 15.sp, weight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (selected) Icon(Ic.Check, 16.dp, c.accent)
        }
        Txt(l.sub, size = 12.sp, color = c.textDim, lineHeight = 17.sp, maxLines = 2)
    }
}

@Composable
private fun Mascot() {
    val c = Oc.c
    val prefs = Graph.prefs
    Head("cl0udly", "The little cloud lives on the home screen in the cl0udly look. It is the same one as on the phone.")
    Box(Modifier.fillMaxWidth().height(220.dp).clip(Oc.round(16.dp)).background(Looks.cloud.dark.bg), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().then(with(CloudBackdrop) { Modifier.backdrop(Looks.cloud.dark.bg, true, "mascot-room", false) }))
        PixelCloud(Modifier.size(width = 190.dp, height = 136.dp))
    }
    Spacer(Modifier.height(10.dp))
    Txt("Click it, double-click it, hold it, drag it, rub it back and forth, or shake the cursor over it.", size = 13.sp, color = c.textDim)
    Spacer(Modifier.height(4.dp))
    Txt("Petted ${prefs.petCount} times.", size = 13.sp, color = c.textDim)
    Spacer(Modifier.height(24.dp))
    Group("Life") {
        SettingRow("Eyes follow the cursor") { Toggle(prefs.mascotFollow) { prefs.mascotFollow = it } }
        SettingRow("Gets sleepy when left alone") { Toggle(prefs.mascotSleepy) { prefs.mascotSleepy = it } }
        SettingRow("Stretches on the first launch of the day") { Toggle(prefs.mascotMorning) { prefs.mascotMorning = it } }
        SettingRow("Holidays and seasons", "Snow in winter, leaves in autumn, a hat on its birthday.") { Toggle(prefs.mascotSeasonal) { prefs.mascotSeasonal = it } }
        SettingRow("Notices the charger", "On a laptop: cheers when plugged in, drowsy under 15 %.", last = true) { Toggle(prefs.mascotBattery) { prefs.mascotBattery = it } }
    }
}

@Composable
private fun Models() {
    val c = Oc.c
    val hub = Graph.hub
    val scope = rememberCoroutineScope()
    var all by remember { mutableStateOf<List<AllProvider>>(emptyList()) }
    var connected by remember { mutableStateOf<List<String>>(emptyList()) }
    var q by remember { mutableStateOf("") }
    var keyFor by remember { mutableStateOf<AllProvider?>(null) }
    suspend fun load() = runCatching { Graph.api.allProviders() }.getOrNull()?.let { all = it.all; connected = it.connected }
    LaunchedEffect(Unit) { load() }
    Head("Models", "OpenCode reaches the models. Free ones from OpenCode Zen work without a key; others need the provider's API key.")
    Group("In use") {
        val byProvider = hub.picks.groupBy { it.provider.name }
        if (byProvider.isEmpty()) SettingRow("No models yet", "The server is still starting, or no provider is connected.", last = true) { }
        byProvider.entries.forEachIndexed { i, (name, picks) ->
            SettingRow(name, picks.take(6).joinToString(" · ") { it.label } + if (picks.size > 6) " · +${picks.size - 6}" else "", last = i == byProvider.size - 1) {
                Txt("${picks.size}", size = 13.sp, color = c.textDim, family = Oc.mono)
            }
        }
    }
    Group("Add a provider") {
        Field(q, { q = it }, "Search providers (Anthropic, OpenAI, Google, OpenRouter, Groq…)", Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), size = 14)
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border.copy(alpha = 0.6f)))
        val list = all.filter { q.isBlank() || it.name.contains(q, true) || it.id.contains(q, true) }.take(12)
        list.forEachIndexed { i, p ->
            SettingRow(p.name.ifBlank { p.id }, p.env.firstOrNull(), last = i == list.lastIndex) {
                if (p.id in connected) Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt("Connected", size = 13.sp, color = c.ok)
                    Spacer(Modifier.width(10.dp))
                    Button("Remove", primary = false) { scope.launch { runCatching { Graph.api.removeAuth(p.id); Graph.api.disposeAll() }; load(); hub.loadModels() } }
                } else Button("Add key", primary = false) { keyFor = p }
            }
        }
    }
    keyFor?.let { p ->
        var key by remember(p.id) { mutableStateOf("") }
        androidx.compose.ui.window.Dialog(onDismissRequest = { keyFor = null }) {
            Column(Modifier.width(460.dp).clip(Oc.round(16.dp)).background(c.surface).border(1.dp, c.border, Oc.round(16.dp)).padding(20.dp)) {
                Txt("${p.name} API key", size = 18.sp, weight = FontWeight.SemiBold, family = Oc.heading)
                Spacer(Modifier.height(6.dp))
                Txt("Stored by OpenCode on this computer only.", size = 13.sp, color = c.textDim)
                Spacer(Modifier.height(14.dp))
                Field(key, { key = it }, "Paste the key", Modifier.fillMaxWidth().clip(Oc.round(10.dp)).border(1.dp, c.border, Oc.round(10.dp)).padding(12.dp), mask = true)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    Button("Cancel", primary = false) { keyFor = null }
                    Button("Save") {
                        val k = key.trim(); keyFor = null
                        scope.launch {
                            runCatching { Graph.api.setApiKey(p.id, k); Graph.api.disposeAll() }.onFailure { Graph.hub.toasts.tryEmit(it.message ?: "Could not save the key") }
                            load(); hub.loadModels()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Server() {
    val c = Oc.c
    val prefs = Graph.prefs
    val st by Graph.runtime.state.collectAsStateCompat()
    val connected by Graph.events.connected.collectAsStateCompat()
    Head("Server", "cl0udly runs its own OpenCode ${RuntimeController.OC_VERSION} on this computer, like the phone app runs one inside itself. Or it can join another computer's `opencode serve`.")
    Group("This computer") {
        SettingRow("State", if (prefs.remoteUrl.isNotBlank()) "Not used: connected to another computer" else null) {
            Txt(
                when (val s = st) {
                    is RuntimeState.Running -> "Running · ${s.version}"
                    is RuntimeState.Installing -> s.note
                    is RuntimeState.Failed -> s.reason
                    RuntimeState.Starting -> "Starting…"
                    RuntimeState.Idle -> "Stopped"
                },
                size = 14.sp, color = if (st is RuntimeState.Running) c.ok else c.textDim,
            )
        }
        SettingRow("Address", "Only this computer can reach it.") { Txt("127.0.0.1:${prefs.serverPort}", size = 13.sp, family = Oc.mono, color = c.textDim) }
        SettingRow("Live updates", null) { Txt(if (connected) "Connected" else "Not connected", size = 14.sp, color = if (connected) c.ok else c.warn) }
        SettingRow("Log", Graph.runtime.logFile.absolutePath) { Button("Open", primary = false) { openFolder(Graph.runtime.logFile.parent) } }
        SettingRow("Restart the server", last = true) { Button("Restart", primary = false) { Graph.runtime.restart() } }
    }
    Group("Another computer") {
        var url by remember { mutableStateOf(prefs.remoteUrl) }
        var pass by remember { mutableStateOf(prefs.remotePassword) }
        var note by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Txt("Address of `opencode serve` (OpenCode 1.x) and its password.", size = 13.sp, color = c.textDim)
            Field(url, { url = it }, "http://192.168.1.20:4096", Modifier.fillMaxWidth().clip(Oc.round(10.dp)).border(1.dp, c.border, Oc.round(10.dp)).padding(12.dp), size = 14)
            Field(pass, { pass = it }, "Password", Modifier.fillMaxWidth().clip(Oc.round(10.dp)).border(1.dp, c.border, Oc.round(10.dp)).padding(12.dp), size = 14, mask = true)
            note?.let { Txt(it, size = 13.sp, color = c.danger) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button("Connect") {
                    scope.launch {
                        val why = Graph.api.reachable(url.trim().trimEnd('/'), pass)
                        if (why != null) note = why else { note = null; Graph.useRemote(url, pass) }
                    }
                }
                if (prefs.remoteUrl.isNotBlank()) Button("Use this computer", primary = false) { Graph.useRemote("", ""); url = ""; pass = "" }
            }
        }
    }
}

@Composable
private fun About() {
    val c = Oc.c
    Head("About")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(84.dp).clip(Oc.round(20.dp)).background(Looks.cloud.dark.surface), contentAlignment = Alignment.Center) {
            PixelCloud(Modifier.size(64.dp, 48.dp), interactive = false)
        }
        Spacer(Modifier.width(18.dp))
        Column {
            Txt("cl0udly for desktop", size = 22.sp, family = Oc.heading, weight = FontWeight.SemiBold)
            Txt("0.0.1 · was OpenCode mobile, now on the computer too", size = 14.sp, color = c.textDim)
        }
    }
    Spacer(Modifier.height(24.dp))
    Group {
        SettingRow("OpenCode", "The agent itself: anomalyco/opencode, MIT.") { Txt(RuntimeController.OC_VERSION, size = 13.sp, family = Oc.mono, color = c.textDim) }
        SettingRow("Fonts", "Inter, Source Serif 4, Unbounded, JetBrains Mono — SIL Open Font License.") { }
        SettingRow("Data", app.cl0udly.data.Paths.data.absolutePath, last = true) { Button("Open", primary = false) { openFolder(app.cl0udly.data.Paths.data.absolutePath) } }
    }
}
