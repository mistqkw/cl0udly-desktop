package app.cl0udly.ui

import app.cl0udly.ui.kit.Pop
import app.cl0udly.ui.kit.PopItem
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
import app.cl0udly.ui.kit.IconButton
import app.cl0udly.ui.kit.Txt
import app.cl0udly.ui.kit.hoverTap
import app.cl0udly.ui.theme.Look
import app.cl0udly.ui.theme.Looks
import app.cl0udly.ui.theme.Oc
import kotlinx.coroutines.launch

private class Sec(val id: String, val ic: Ic, val title: String, val words: String)

private val SECTIONS = listOf(
    Sec("general", Ic.Settings, "General", "name profile about you language"),
    Sec("account", Ic.User, "Account", "sign in login photo avatar picture opencode key"),
    Sec("memory", Ic.Bulb, "Memory", "remember notes forget facts"),
    Sec("helpers", Ic.Fork, "Helpers", "subagents other models parallel tokens brief check verify style"),
    Sec("usage", Ic.Gauge, "Usage", "limits spending cost tokens 5 hours week month go"),
    Sec("appearance", Ic.Palette, "Appearance", "look theme dark light color text size width motion animation font"),
    Sec("instructions", Ic.Edit, "Your cl0udly", "instructions agents.md memory rules prompt"),
    Sec("capabilities", Ic.Layers, "Capabilities", "skills commands mcp connectors tools agents"),
    Sec("notifications", Ic.Bell, "Notifications", "notify desktop done permission errors"),
    Sec("models", Ic.Box, "Models", "providers api key connect models hide show"),
    Sec("code", Ic.Code, "Code", "transcript permissions auto browser links terminal shell remote control phone"),
    Sec("server", Ic.Server, "Server", "opencode serve address remote another computer log restart"),
    Sec("shortcuts", Ic.Key, "Shortcuts", "keyboard hotkeys keys"),
    Sec("desktop", Ic.Expand, "Desktop app", "startup autostart keep awake sleep storage data quick entry"),
    Sec("mascot", Ic.Cloud, "cl0udly", "mascot cloud pet eyes sleepy seasons"),
    Sec("about", Ic.Info, "About", "version license fonts"),
)

/** Claude desktop's settings window: search and sections on the left, the section on the right. */
@Composable
fun SettingsDialog() {
    val section = Nav.settings
    Scrim(section != null, { Nav.settings = null }) {
        val s = section ?: return@Scrim
        val c = Oc.c
        var q by remember { mutableStateOf("") }
        Row(
            Modifier.widthIn(max = 1120.dp).fillMaxWidth(0.9f).fillMaxHeight(0.88f).clip(Oc.round(16.dp)).background(c.bg)
                .border(1.dp, c.border, Oc.round(16.dp)),
        ) {
            Column(Modifier.width(250.dp).fillMaxHeight().background(c.sidebar).padding(12.dp)) {
                Row(Modifier.fillMaxWidth().height(38.dp).clip(Oc.round(10.dp)).background(c.surface).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ic.Search, 15.dp, c.textDim)
                    Spacer(Modifier.width(8.dp))
                    Field(q, { q = it }, "Search settings", Modifier.weight(1f), size = 14)
                }
                Spacer(Modifier.height(10.dp))
                val shown = SECTIONS.filter { q.isBlank() || it.title.contains(q, true) || it.words.contains(q, true) }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    shown.forEach { sec ->
                        Row(
                            Modifier.fillMaxWidth().height(38.dp).hoverTap(Oc.round(M.listRadius), sec.id == s) { Nav.settings = sec.id }.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(sec.ic, 17.dp, if (sec.id == s) c.text else c.textDim)
                            Spacer(Modifier.width(12.dp))
                            Txt(sec.title, size = 15.sp, color = if (sec.id == s) c.text else c.textDim)
                        }
                    }
                    if (shown.isEmpty()) Txt("Nothing found", size = 14.sp, color = c.textDim, modifier = Modifier.padding(10.dp))
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                AnimatedContent(s, transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(80)) }, label = "section") { id ->
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 36.dp, end = 40.dp, top = 30.dp, bottom = 50.dp)) {
                        Column(Modifier.widthIn(max = 760.dp)) {
                            when (id) {
                                "general" -> General()
                                "account" -> AccountPage()
                                "memory" -> MemoryPage()
                                "helpers" -> HelpersPage()
                                "usage" -> UsagePage()
                                "appearance" -> Appearance()
                                "instructions" -> Instructions()
                                "capabilities" -> Capabilities()
                                "notifications" -> Notifications()
                                "models" -> Models()
                                "code" -> CodeSettings()
                                "server" -> Server()
                                "shortcuts" -> Shortcuts()
                                "desktop" -> DesktopApp()
                                "mascot" -> Mascot()
                                else -> About()
                            }
                        }
                    }
                }
                IconButton(Ic.Close, { Nav.settings = null }, Modifier.align(Alignment.TopEnd).padding(12.dp), size = 30.dp, icon = 16.dp)
            }
        }
    }
}

@Composable
internal fun Head(title: String, sub: String? = null) {
    Txt(title, size = 22.sp, weight = FontWeight.SemiBold, family = Oc.heading)
    if (sub != null) { Spacer(Modifier.height(6.dp)); Txt(sub, size = 14.sp, color = Oc.c.textDim, lineHeight = 21.sp) }
    Spacer(Modifier.height(22.dp))
}

@Composable
internal fun Group(title: String? = null, content: @Composable () -> Unit) {
    val c = Oc.c
    if (title != null) Txt(title, size = 13.sp, color = c.textDim, weight = FontWeight.Medium, family = Oc.label, modifier = Modifier.padding(bottom = 8.dp, start = 2.dp))
    Column(Modifier.fillMaxWidth().clip(Oc.round(14.dp)).border(1.dp, c.border, Oc.round(14.dp))) { content() }
    Spacer(Modifier.height(26.dp))
}

@Composable
internal fun SettingRow(title: String, sub: String? = null, last: Boolean = false, trailing: @Composable () -> Unit) {
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
internal fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val c = Oc.c
    val x by animateFloatAsState(if (on) 1f else 0f, tween(120), label = "toggle")
    Box(Modifier.size(40.dp, 24.dp).clip(CircleShape).background(if (on) c.accent else c.surfaceHigh).hoverTap(CircleShape) { onChange(!on) }.padding(3.dp)) {
        Box(Modifier.offset(x = (16 * x).dp).size(18.dp).clip(CircleShape).background(if (on) c.bg else c.textDim))
    }
}

@Composable
internal fun Segmented(options: List<Pair<String, String>>, value: String, onPick: (String) -> Unit) {
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
    val c = Oc.c
    Head("General")
    Group("Profile") {
        SettingRow("What should cl0udly call you?", "Used in the greeting.") {
            Field(prefs.name, { prefs.name = it }, "Your name", Modifier.width(220.dp).clip(Oc.round(8.dp)).border(1.dp, c.border, Oc.round(8.dp)).padding(10.dp), size = 14)
        }
        Column(Modifier.padding(18.dp)) {
            Txt("What should cl0udly know about you?", size = 15.sp)
            Txt("Sent with every new session, like Claude's personal preferences.", size = 13.sp, color = c.textDim)
            Spacer(Modifier.height(10.dp))
            Field(
                prefs.userAbout, { prefs.userAbout = it }, "I write Kotlin and Flutter, answer in Russian, keep it short…",
                Modifier.fillMaxWidth().height(110.dp).clip(Oc.round(10.dp)).border(1.dp, c.border, Oc.round(10.dp)).padding(12.dp), size = 14, singleLine = false,
            )
        }
    }
    Group("Look") {
        SettingRow("Color mode") { Segmented(listOf("system" to "System", "light" to "Light", "dark" to "Dark"), prefs.colorMode) { prefs.colorMode = it } }
        SettingRow("Text size", last = true) { Segmented(listOf("small" to "Small", "default" to "Default", "large" to "Large"), prefs.textSize) { prefs.textSize = it } }
    }
    Group("Window") {
        SettingRow(
            "Keep running in the tray",
            if (app.cl0udly.data.Tray.active) "Closing the window hides it; the agent keeps working. Quit from the cloud in the panel." else "Your panel has no tray, so closing the window quits.",
        ) { Switch(prefs.closeToTray) { prefs.closeToTray = it } }
        SettingRow("Sounds", "A soft note when a message goes, when a reply is done, when you pet the mascot.", last = true) {
            Switch(prefs.sounds) { prefs.sounds = it; if (it) app.cl0udly.data.Sounds.play(app.cl0udly.data.Sounds.S.Done) }
        }
    }
    Group("Language") {
        SettingRow("Interface language", "Language of the app's own words. The agent answers in the language you write to it.", last = true) {
            // a small list that drops down, the current one ticked
            var open by remember { mutableStateOf(false) }
            val names = mapOf("system" to "System", "ru" to "Русский", "en" to "English")
            Box {
                Row(Modifier.clip(Oc.round(8.dp)).border(1.dp, c.border, Oc.round(8.dp)).hoverTap(Oc.round(8.dp)) { open = true }.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Txt(names[prefs.language] ?: "System", size = 14.sp)
                    Spacer(Modifier.width(6.dp)); Icon(Ic.Chevron, 14.dp, c.textDim)
                }
                Pop(open, { open = false }, width = 220.dp) {
                    names.forEach { (id, name) -> PopItem(name, sub = if (id == "system") "Follows the computer's language" else null, checked = prefs.language == id) { prefs.language = id; app.cl0udly.data.I18n.lang = id; open = false } }
                }
            }
        }
    }
    Group("Dictation") {
        SettingRow(
            "Language",
            when (prefs.voiceLang) {
                "auto" -> "Whisper guesses it each time: any language, a few seconds slower."
                "en" -> "English only."
                else -> "Russian, with English words kept as they are. Fastest."
            },
            last = true,
        ) { Segmented(listOf("ru" to "Russian + English", "en" to "English", "auto" to "Auto"), prefs.voiceLang) { prefs.voiceLang = it } }
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
    Spacer(Modifier.height(28.dp))
    Txt("Sidebar", size = 16.sp, weight = FontWeight.Medium)
    Spacer(Modifier.height(4.dp))
    Txt("What the sidebar shows and in what order. New chat and More always stay.", size = 13.sp, color = Oc.c.textDim)
    Spacer(Modifier.height(10.dp))
    val names = mapOf("search" to "Search", "projects" to "Projects", "memory" to "Memory", "customize" to "Customize", "pinned" to "Pinned chats", "server" to "Server status")
    // the top rows can move; pinned chats and the server status keep their places lower down
    val order = prefs.sidebarRows
    val rows = order + listOf("pinned", "server")
    rows.forEachIndexed { i, k ->
        SettingRow(names[k] ?: k, last = i == rows.lastIndex) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (k in order) {
                    val at = order.indexOf(k)
                    IconButton(Ic.ArrowUp, { prefs.moveSidebar(k, -1) }, size = 28.dp, icon = 15.dp, tint = if (at > 0) Oc.c.textDim else Oc.c.border)
                    IconButton(Ic.ArrowDown, { prefs.moveSidebar(k, 1) }, size = 28.dp, icon = 15.dp, tint = if (at < order.lastIndex) Oc.c.textDim else Oc.c.border)
                    Spacer(Modifier.width(10.dp))
                }
                Toggle(prefs.sidebarShows(k)) { prefs.toggleSidebar(k) }
            }
        }
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
    Spacer(Modifier.height(14.dp))
    // games with the cloud: the same as on the phone, played with the mouse
    var game by remember { mutableStateOf<String?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button("Talk with cl0udly") { app.cl0udly.ui.cloud.TalkUi.open = true }
        listOf("rain" to "Catch the rain", "cups" to "Cups", "echo" to "Echo").forEach { (id, name) ->
            Button(name, primary = game == id) { game = if (game == id) null else id }
        }
    }
    if (game != null) {
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(460.dp).clip(Oc.round(16.dp)).background(c.surface).border(1.dp, c.border, Oc.round(16.dp))) {
            when (game) {
                "rain" -> app.cl0udly.ui.cloud.RainGame()
                "cups" -> app.cl0udly.ui.cloud.CupsGame()
                "echo" -> app.cl0udly.ui.cloud.EchoGame()
            }
        }
    }
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
    Group("Shown in the model picker") {
        val byProvider = hub.picks.groupBy { it.provider.name }
        if (byProvider.isEmpty()) SettingRow("No models yet", "The server is still starting, or no provider is connected.", last = true) { }
        byProvider.entries.forEach { (name, picks) ->
            Txt(name, size = 13.sp, color = c.textDim, weight = FontWeight.Medium, modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 2.dp))
            picks.forEach { p ->
                SettingRow(p.label, p.model.id) { Switch(p.key !in Graph.prefs.hiddenModelSet) { Graph.prefs.toggleHidden(p.key) } }
            }
        }
    }
    Group("Add a provider") {
        Field(q, { q = it }, "Search providers (Anthropic, OpenAI, Google, OpenRouter, Groq…)", Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), size = 14)
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border.copy(alpha = 0.6f)))
        // the ones people actually have keys for go first, OpenCode Go on top
        val popular = listOf("opencode-go", "opencode", "anthropic", "openai", "google", "openrouter", "deepseek", "moonshotai", "xai", "groq", "mistral", "zai")
        val list = all.filter { q.isBlank() || it.name.contains(q, true) || it.id.contains(q, true) }
            .sortedBy { popular.indexOf(it.id).let { i -> if (i < 0) 99 else i } }.take(12)
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
    Head("Server", "cl0udly runs its own OpenCode ${Graph.runtime.version} on this computer, like the phone app runs one inside itself. Or it can join another computer's `opencode serve`.")
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
    Group("OpenCode updates") {
        val up = Graph.ocUpdater
        val scope = rememberCoroutineScope()
        SettingRow("Version ${Graph.runtime.version}", up.busy ?: prefs.ocUpdateError.ifBlank { "Checked once a day. A new build is tested first and takes over only when it works." }) {
            Button("Check now", primary = false) { scope.launch { up.check(manual = true) } }
        }
        val prev = Graph.runtime.previous
        SettingRow("Go back to the previous version", prev?.let { "OpenCode $it is kept" } ?: "No previous version yet", last = true) {
            if (prev != null) Button("Go back", primary = false) { scope.launch { up.rollback() } }
        }
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
            Txt("$APP_VERSION · was OpenCode mobile, now on the computer too", size = 14.sp, color = c.textDim)
        }
    }
    Spacer(Modifier.height(24.dp))
    Group {
        SettingRow("OpenCode", "The agent itself: anomalyco/opencode, MIT.") { Txt(Graph.runtime.version, size = 13.sp, family = Oc.mono, color = c.textDim) }
        SettingRow("Fonts", "Inter, Source Serif 4, Unbounded, JetBrains Mono — SIL Open Font License.") { }
        SettingRow("Data", app.cl0udly.data.Paths.data.absolutePath, last = true) { Button("Open", primary = false) { openFolder(app.cl0udly.data.Paths.data.absolutePath) } }
    }
}

// ---------- new sections ----------

@Composable
private fun UsagePage() {
    val c = Oc.c
    val prefs = Graph.prefs
    val snap by Graph.usage.snapshot.collectAsStateCompat()
    Head("Usage", "OpenCode has no API for the remaining limits, so cl0udly adds up the cost of every reply that went through this computer.")
    UsageHero(snap)
    Spacer(Modifier.height(14.dp))
    DailyBars(snap.daily)
    Spacer(Modifier.height(14.dp))
    Group("By model · last 30 days") {
        if (snap.byModel.isEmpty()) SettingRow("Nothing counted yet", "Replies show up here as soon as they finish.", last = true) { }
        snap.byModel.take(12).forEachIndexed { i, m ->
            SettingRow(m.model, "${m.provider} · ${m.messages} replies · ${tokens(m.tokens)} tokens", last = i == minOf(11, snap.byModel.lastIndex)) {
                Column(horizontalAlignment = Alignment.End) {
                    Txt(money(m.cost), size = 14.sp, family = Oc.mono)
                    Bar(if (snap.totalMonth > 0) (m.cost / snap.totalMonth).toFloat() else 0f, Modifier.padding(top = 5.dp).width(64.dp))
                }
            }
        }
    }
    Group("Your plan") {
        SettingRow("5-hour limit, $") { NumField(prefs.limit5h) { prefs.limit5h = it; Graph.usage.recompute() } }
        SettingRow("Weekly limit, $") { NumField(prefs.limitWeek) { prefs.limitWeek = it; Graph.usage.recompute() } }
        SettingRow("Monthly limit, $") { NumField(prefs.limitMonth) { prefs.limitMonth = it; Graph.usage.recompute() } }
        SettingRow("Providers counted", "Comma-separated ids; empty counts everything.", last = true) {
            Field(prefs.limitProviders, { prefs.limitProviders = it; Graph.usage.recompute() }, "opencode-go", Modifier.width(220.dp).clip(Oc.round(8.dp)).border(1.dp, c.border, Oc.round(8.dp)).padding(10.dp), size = 14)
        }
    }
}

@Composable
private fun NumField(v: String, onChange: (String) -> Unit) {
    Field(v, { s -> onChange(s.filter { it.isDigit() || it == '.' }) }, "0", Modifier.width(90.dp).clip(Oc.round(8.dp)).border(1.dp, Oc.c.border, Oc.round(8.dp)).padding(10.dp), size = 14)
}

/** The global AGENTS.md of our OpenCode: rules every session follows. */
private val agentsFile get() = java.io.File(app.cl0udly.data.Paths.data, "opencode/config/opencode/AGENTS.md")

@Composable
private fun Instructions() {
    val c = Oc.c
    var text by remember { mutableStateOf(runCatching { agentsFile.readText() }.getOrDefault("")) }
    var saved by remember { mutableStateOf(text) }
    val project = Nav.project
    var ptext by remember(project) { mutableStateOf(project?.let { runCatching { java.io.File(it, "AGENTS.md").readText() }.getOrNull() }.orEmpty()) }
    var psaved by remember(project) { mutableStateOf(ptext) }
    Head("Your cl0udly", "Instructions every session follows. OpenCode reads them from AGENTS.md, the same file the terminal version uses.")
    Group("For every session") {
        Column(Modifier.padding(18.dp)) {
            Field(text, { text = it }, "Answer in Russian. Prefer small commits. Never touch the release keys…",
                Modifier.fillMaxWidth().height(220.dp).clip(Oc.round(10.dp)).border(1.dp, c.border, Oc.round(10.dp)).padding(12.dp), size = 14, singleLine = false)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(agentsFile.absolutePath, size = 12.sp, color = c.textDim, family = Oc.mono, modifier = Modifier.weight(1f), maxLines = 1)
                Button("Save", primary = text != saved) {
                    runCatching { agentsFile.parentFile.mkdirs(); agentsFile.writeText(text); saved = text; Graph.hub.toasts.tryEmit("Saved — new sessions follow it") }
                }
            }
        }
    }
    if (project != null) Group("For ${java.io.File(project).name}") {
        Column(Modifier.padding(18.dp)) {
            Field(ptext, { ptext = it }, "How to build, test and lint this project…",
                Modifier.fillMaxWidth().height(180.dp).clip(Oc.round(10.dp)).border(1.dp, c.border, Oc.round(10.dp)).padding(12.dp), size = 14, singleLine = false)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt("$project/AGENTS.md", size = 12.sp, color = c.textDim, family = Oc.mono, modifier = Modifier.weight(1f), maxLines = 1)
                Button("Save", primary = ptext != psaved) { runCatching { java.io.File(project, "AGENTS.md").writeText(ptext); psaved = ptext } }
            }
        }
    }
}

@Composable
private fun Capabilities() {
    val c = Oc.c
    val hub = Graph.hub
    val scope = rememberCoroutineScope()
    var mcp by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    suspend fun loadMcp() { mcp = runCatching { Graph.api.mcpStatus().mapValues { (_, v) -> (v as? kotlinx.serialization.json.JsonObject)?.get("status")?.toString()?.trim('"') ?: "?" } }.getOrDefault(emptyMap()) }
    LaunchedEffect(Unit) { loadMcp() }
    var name by remember { mutableStateOf("") }
    var cmd by remember { mutableStateOf("") }
    Head("Capabilities", "What the agent can use besides its own tools: connectors (MCP servers), skills, commands and agents.")
    Group("Connectors · MCP") {
        if (mcp.isEmpty()) SettingRow("No connectors yet", "Add a local MCP server below.") { }
        mcp.forEach { (n, st) ->
            SettingRow(n, st) {
                Switch(st == "connected") { on ->
                    scope.launch { runCatching { if (on) Graph.api.mcpConnect(n) else Graph.api.mcpDisconnect(n) }.onFailure { hub.toasts.tryEmit(it.message ?: "Failed") }; loadMcp() }
                }
            }
        }
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Txt("Add a connector", size = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Field(name, { name = it }, "name", Modifier.width(150.dp).clip(Oc.round(8.dp)).border(1.dp, c.border, Oc.round(8.dp)).padding(10.dp), size = 14)
                Field(cmd, { cmd = it }, "npx -y @modelcontextprotocol/server-filesystem ~", Modifier.weight(1f).clip(Oc.round(8.dp)).border(1.dp, c.border, Oc.round(8.dp)).padding(10.dp), size = 14)
                Button("Add", primary = name.isNotBlank() && cmd.isNotBlank()) {
                    if (name.isBlank() || cmd.isBlank()) return@Button
                    val cfg = kotlinx.serialization.json.buildJsonObject {
                        put("type", kotlinx.serialization.json.JsonPrimitive("local"))
                        put("command", kotlinx.serialization.json.JsonArray(cmd.trim().split(Regex("\\s+")).map { kotlinx.serialization.json.JsonPrimitive(it) }))
                        put("enabled", kotlinx.serialization.json.JsonPrimitive(true))
                    }
                    scope.launch { runCatching { Graph.api.mcpAdd(name.trim(), cfg) }.onFailure { hub.toasts.tryEmit(it.message ?: "Could not add") }; name = ""; cmd = ""; loadMcp() }
                }
            }
        }
    }
    Group("Skills") {
        if (hub.skills.isEmpty()) SettingRow("No skills", "Put SKILL.md folders in ~/.config/opencode/skill or the project's .opencode/skill.", last = true) { }
        hub.skills.forEachIndexed { i, s -> SettingRow(s.name, s.description, last = i == hub.skills.lastIndex) { } }
    }
    Group("Slash commands") {
        if (hub.commands.isEmpty()) SettingRow("No commands", last = true) { }
        hub.commands.forEachIndexed { i, s -> SettingRow("/" + s.name, s.description, last = i == hub.commands.lastIndex) { Txt(s.source.orEmpty(), size = 12.sp, color = c.textDim) } }
    }
    Group("Agents") {
        hub.agents.forEachIndexed { i, a ->
            SettingRow(a, if (a == Graph.prefs.agent) "Used for new messages" else null, last = i == hub.agents.lastIndex) {
                if (a != Graph.prefs.agent) Button("Use", primary = false) { Graph.prefs.agent = a }
            }
        }
    }
}

@Composable
private fun Notifications() {
    val prefs = Graph.prefs
    Head("Notifications", "Desktop notifications while the window is in the background.")
    Group {
        SettingRow("A reply is ready") { Switch(prefs.notifyDone) { prefs.notifyDone = it } }
        SettingRow("cl0udly needs your OK", "A tool wants permission or the agent asks a question.") { Switch(prefs.notifyPermission) { prefs.notifyPermission = it } }
        SettingRow("Errors") { Switch(prefs.notifyErrors) { prefs.notifyErrors = it } }
        SettingRow("Try it", last = true) { Button("Send a test", primary = false) { app.cl0udly.data.Desktop.notify("cl0udly", "Notifications work") } }
    }
}

@Composable
private fun CodeSettings() {
    val c = Oc.c
    val prefs = Graph.prefs
    Head("Code", "How sessions in a project folder behave.")
    Group("Transcript") {
        SettingRow("Transcript view", "Normal hides the work in a summary; Verbose shows every step open.") {
            Segmented(listOf("normal" to "Normal", "thinking" to "Thinking", "verbose" to "Verbose"), prefs.transcriptView) { prefs.transcriptView = it }
        }
        SettingRow("Width", last = true) { Segmented(listOf("narrow" to "Narrow", "wide" to "Wide"), prefs.transcriptWidth) { prefs.transcriptWidth = it } }
    }
    Group("Permissions") {
        SettingRow("Default mode", "Auto lets the agent run tools without asking, like Claude's Auto.", last = true) {
            Segmented(listOf("manual" to "Ask", "auto" to "Auto"), prefs.permMode) { prefs.permMode = it }
        }
    }
    Group("Browser and files") {
        SettingRow("Open links in the built-in browser") { Switch(prefs.linksInBrowser) { prefs.linksInBrowser = it } }
        SettingRow("Show files the agent edits", "Opens Changes when a session edits files.", last = true) { Switch(prefs.autoOpenFiles) { prefs.autoOpenFiles = it } }
    }
    Group("Terminal") {
        SettingRow("Shell", "Empty uses the system shell.", last = true) {
            Field(prefs.terminalShell, { prefs.terminalShell = it }, app.cl0udly.data.Os.defaultShell, Modifier.width(220.dp).clip(Oc.round(8.dp)).border(1.dp, c.border, Oc.round(8.dp)).padding(10.dp), size = 14)
        }
    }
    Group("Remote Control") {
        SettingRow("Let the phone connect", "The server listens on the local network with its password. The QR is in the Context panel.", last = true) {
            Switch(prefs.remoteControl) { prefs.remoteControl = it; if (prefs.remoteUrl.isBlank()) Graph.runtime.restart() }
        }
    }
}

val SHORTCUTS = listOf(
    "New chat" to "Ctrl+N", "Open a project" to "Ctrl+O", "Open a file" to "Ctrl+Shift+O", "Search" to "Ctrl+K", "Quick entry" to "Ctrl+Shift+Space",
    "Toggle sidebar" to "Ctrl+B", "Settings" to "Ctrl+,", "Context" to "Ctrl+I", "Terminal" to "Ctrl+`", "Browser" to "Ctrl+Shift+B",
    "Changes" to "Ctrl+Shift+D", "Files" to "Ctrl+Shift+E", "Back" to "Ctrl+[", "Forward" to "Ctrl+]", "Bigger text" to "Ctrl+=",
    "Smaller text" to "Ctrl+-", "Attach files" to "Ctrl+U", "Stop the reply" to "Esc", "New line" to "Shift+Enter", "Quit" to "Ctrl+Q",
)

@Composable
private fun Shortcuts() {
    var q by remember { mutableStateOf("") }
    Head("Shortcuts")
    Field(q, { q = it }, "Search shortcuts", Modifier.fillMaxWidth().clip(Oc.round(10.dp)).border(1.dp, Oc.c.border, Oc.round(10.dp)).padding(12.dp), size = 14)
    Spacer(Modifier.height(16.dp))
    Group {
        val list = SHORTCUTS.filter { q.isBlank() || it.first.contains(q, true) || it.second.contains(q, true) }
        list.forEachIndexed { i, (what, key) ->
            SettingRow(what, last = i == list.lastIndex) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    key.split('+').forEach { k ->
                        Box(Modifier.clip(Oc.round(6.dp)).background(Oc.c.surface).border(1.dp, Oc.c.border, Oc.round(6.dp)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                            Txt(k, size = 12.sp, family = Oc.mono)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopApp() {
    val prefs = Graph.prefs
    val c = Oc.c
    var used by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) { used = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.cl0udly.data.Paths.data.walkTopDown().filter { it.isFile }.sumOf { it.length() } } }
    Head("Desktop app")
    Group("System") {
        SettingRow("Start with the session", "Opens cl0udly when you log in.") {
            Switch(prefs.runOnStartup) { prefs.runOnStartup = it; app.cl0udly.data.Desktop.runOnStartup(it) }
        }
        SettingRow("Keep the computer awake", "While the agent works.") { Switch(prefs.keepAwake) { prefs.keepAwake = it } }
        SettingRow("Quick entry", "Ask from anywhere in the window.", last = true) { Txt("Ctrl+Shift+Space", size = 13.sp, family = Oc.mono, color = c.textDim) }
    }
    Group("Storage") {
        SettingRow("App data", app.cl0udly.data.Paths.data.absolutePath) { Txt(used?.let { size(it) } ?: "…", size = 13.sp, family = Oc.mono, color = c.textDim) }
        SettingRow("Open the folder", last = true) { Button("Open", primary = false) { openFolder(app.cl0udly.data.Paths.data.absolutePath) } }
    }
}
