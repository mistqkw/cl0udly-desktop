package app.cl0udly.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cl0udly.Graph
import app.cl0udly.data.Desktop
import app.cl0udly.data.Helpers
import app.cl0udly.data.Memory
import app.cl0udly.data.Paths
import app.cl0udly.ui.kit.Ic
import app.cl0udly.ui.kit.Icon
import app.cl0udly.ui.kit.Txt
import app.cl0udly.ui.kit.hoverTap
import app.cl0udly.ui.theme.Oc
import kotlinx.coroutines.launch
import java.io.File

/** The account picture: the chosen photo, or the first letter of the name. */
@Composable
fun Avatar(size: Dp, modifier: Modifier = Modifier) {
    val c = Oc.c
    val path = Graph.prefs.avatarPath
    val img = if (path.isNotBlank() && File(path).isFile) rememberImage(path) else null
    Box(modifier.size(size).clip(CircleShape).background(c.surfaceHigh), contentAlignment = Alignment.Center) {
        if (img != null) Image(img, null, Modifier.size(size), contentScale = ContentScale.Crop)
        else Txt(Graph.prefs.name.take(1).uppercase().ifBlank { "?" }, size = (size.value * 0.42f).sp, weight = FontWeight.SemiBold)
    }
}

/** A photo becomes a 512 px square (the middle of it) in our data folder. */
private fun saveAvatar(src: String): String? = runCatching {
    val img = javax.imageio.ImageIO.read(File(src)) ?: return null
    val side = minOf(img.width, img.height)
    val sq = img.getSubimage((img.width - side) / 2, (img.height - side) / 2, side, side)
    val n = minOf(512, side)
    val out = java.awt.image.BufferedImage(n, n, java.awt.image.BufferedImage.TYPE_INT_RGB)
    out.createGraphics().apply {
        setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        drawImage(sq, 0, 0, n, n, null); dispose()
    }
    val f = File(Paths.data, "avatar-${System.currentTimeMillis()}.jpg")
    f.parentFile.mkdirs()
    javax.imageio.ImageIO.write(out, "jpg", f)
    Graph.prefs.avatarPath.takeIf { it.isNotBlank() }?.let { File(it).delete() }
    f.path
}.getOrNull()

@Composable
internal fun AccountPage() {
    val c = Oc.c
    val prefs = Graph.prefs
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    Head("Account", "Your name and photo, and the OpenCode account the models come from: the same sign-in as on the phone.")
    Group("Profile") {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(72.dp, Modifier.hoverTap(CircleShape) { Pickers.files("Choose a photo") { l -> l.firstOrNull { app.cl0udly.data.Attachments.isImage(it) }?.let(::saveAvatar)?.let { prefs.avatarPath = it } } })
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Field(prefs.name, { prefs.name = it }, "Your name", Modifier.fillMaxWidth().clip(Oc.round(8.dp)).border(1.dp, c.border, Oc.round(8.dp)).padding(10.dp), size = 15)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button("Change photo", primary = false) { Pickers.files("Choose a photo") { l -> l.firstOrNull { app.cl0udly.data.Attachments.isImage(it) }?.let(::saveAvatar)?.let { prefs.avatarPath = it } } }
                    if (prefs.avatarPath.isNotBlank()) Button("Remove", primary = false) { File(prefs.avatarPath).delete(); prefs.avatarPath = "" }
                }
            }
        }
    }
    Group("OpenCode account") {
        var saving by remember { mutableStateOf(false) }
        var note by remember { mutableStateOf<String?>(null) }
        val go = Graph.hub.picks.count { it.provider.id == "opencode-go" }
        if (prefs.signedIn.isNotBlank()) {
            SettingRow("Signed in", if (go > 0) "OpenCode Go is on: $go models. Zen uses the same key." else "The key is saved, but OpenCode Go gave no models: check the key or the subscription.") {
                Button("Sign out", primary = false) {
                    scope.launch {
                        runCatching { Graph.api.removeAuth("opencode"); Graph.api.removeAuth("opencode-go"); Graph.api.disposeAll() }
                        prefs.signedIn = ""; Graph.hub.loadModels()
                    }
                }
            }
        } else Column(Modifier.padding(18.dp)) {
            // the key goes straight in: one field, any key the console gives (Go and Zen share it)
            Txt("OpenCode key", size = 15.sp, weight = FontWeight.Medium)
            Txt("Paste the API key from your OpenCode console. It turns on OpenCode Go and Zen; free models work without it.", size = 13.sp, color = c.textDim, lineHeight = 19.sp, modifier = Modifier.padding(top = 3.dp, bottom = 12.dp))
            fun save() {
                val k = key.trim().removeSurrounding("\"")
                if (k.isEmpty() || saving) return
                saving = true; note = null
                scope.launch {
                    val r = runCatching {
                        Graph.api.setApiKey("opencode-go", k)
                        runCatching { Graph.api.setApiKey("opencode", k) }
                        Graph.api.disposeAll()
                    }
                    Graph.hub.loadModels()
                    saving = false
                    r.onSuccess {
                        prefs.signedIn = "opencode"; key = ""
                        val n = Graph.hub.picks.count { it.provider.id == "opencode-go" }
                        Graph.hub.toasts.tryEmit(if (n > 0) "Signed in: $n OpenCode Go models" else "Key saved")
                    }.onFailure { note = it.message ?: "Could not save the key" }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field(key, { key = it; note = null }, "Paste the key", Modifier.weight(1f).clip(Oc.round(10.dp)).border(1.dp, c.border, Oc.round(10.dp)).padding(12.dp)
                    .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.Enter) { save(); true } else false }, size = 14, mask = true)
                Spacer(Modifier.width(8.dp))
                Button(if (saving) "Saving…" else "Sign in", primary = key.isNotBlank() && !saving) { save() }
                Spacer(Modifier.width(8.dp))
                Button("Get a key", primary = false) { Desktop.open("https://opencode.ai/auth") }
            }
            note?.let { Txt(it, size = 13.sp, color = c.warn, modifier = Modifier.padding(top = 8.dp)) }
        }
        SettingRow("Other providers", "Anthropic, OpenAI, Google, OpenRouter and more, with your own keys.", last = true) {
            Button("Open Models", primary = false) { Nav.settings = "models" }
        }
    }
}

@Composable
internal fun MemoryPage() {
    val c = Oc.c
    val prefs = Graph.prefs
    var tick by remember { mutableIntStateOf(0) }
    var shelf by remember { mutableStateOf("about-me") }
    var fact by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    Head("Memory", "Things cl0udly remembers about you between chats, kept as plain notes in ${Paths.memory.path.replace(System.getProperty("user.home"), "~")}. Turn it off for one chat from the chat's menu.")
    Group {
        SettingRow("Use memory", "Remembered notes go with every message; off means nothing is read or saved.") { Toggle(prefs.memoryOn) { prefs.memoryOn = it } }
        SettingRow("Saving", "Ask: it offers what it heard about you as a card, you tap Remember. Only when asked: it saves when you say \"remember\".", last = true) {
            Segmented(listOf("ask" to "Ask", "auto" to "On its own", "off" to "Only when asked"), prefs.memoryMode) { prefs.memorySave = it }
        }
    }
    @Suppress("UNUSED_EXPRESSION") tick
    val shelves = Memory.shelves()
    Group("What it remembers") {
        if (shelves.all { Memory.facts(it).isEmpty() }) SettingRow("Nothing yet", "Tell cl0udly about yourself, or add a note below.", last = true) { }
        shelves.forEach { f ->
            val facts = Memory.facts(f)
            if (facts.isEmpty()) return@forEach
            Txt(f.nameWithoutExtension.replace('-', ' ').replaceFirstChar { it.uppercase() }, size = 13.sp, color = c.textDim, weight = FontWeight.Medium, modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 2.dp))
            facts.forEach { line ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Txt(line, size = 14.sp, modifier = Modifier.weight(1f), lineHeight = 20.sp)
                    Box(Modifier.size(28.dp).hoverTap(Oc.round(6.dp)) { Memory.removeFact(f, line); tick++ }, contentAlignment = Alignment.Center) { Icon(Ic.Trash, 14.dp, c.textDim) }
                }
            }
        }
    }
    Group("Add a note") {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Field(shelf, { shelf = it }, "shelf", Modifier.width(130.dp).clip(Oc.round(8.dp)).border(1.dp, c.border, Oc.round(8.dp)).padding(10.dp), size = 14)
            Spacer(Modifier.width(8.dp))
            Field(fact, { fact = it }, "I prefer short answers in Russian", Modifier.weight(1f).clip(Oc.round(8.dp)).border(1.dp, c.border, Oc.round(8.dp)).padding(10.dp), size = 14)
            Spacer(Modifier.width(8.dp))
            Button("Add", primary = fact.isNotBlank()) { if (fact.isNotBlank()) { Memory.addFact(shelf, fact); fact = ""; tick++ } }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // a memory export from Claude (zip) or a folder of notes comes in as shelves; export is a zip in Downloads
        Button("Import…", primary = false) {
            Pickers.files("Import memory: a .zip or .md files") { list -> importMemory(list); tick++ }
        }
        Button("Export", primary = false) { exportMemory() }
        Button("Open the folder", primary = false) { Memory.ensure(); Desktop.open(Paths.memory.absolutePath) }
        if (!confirm) Button("Forget everything", primary = false) { confirm = true }
        else Button("Yes, delete all notes", danger = true) { Memory.forgetAll(); confirm = false; tick++ }
    }
}

@Composable
internal fun HelpersPage() {
    val c = Oc.c
    val prefs = Graph.prefs
    val scope = rememberCoroutineScope()
    fun apply() = scope.launch { Helpers.sync() }
    Head("Helpers", "How cl0udly works, and which other models it may call for help. A helper works on its own piece and reports back; it shows as a small cloud above the field.")
    Group("Working style") {
        SettingRow("Brief, and checks every step", "Shorter replies, fewer tokens; it re-reads its edits and runs the checks before handing work back.") {
            Toggle(prefs.brainStyle) { prefs.brainStyle = it; Helpers.writeSettings() }
        }
        SettingRow("Long tool output", "Cut to its beginning and end; the full text goes to a file it can read in parts. Saves tokens.", last = true) {
            Segmented(listOf("0" to "Keep", "8000" to "8k", "12000" to "12k", "20000" to "20k"), prefs.brainPrune) { prefs.brainPrune = it; Helpers.writeSettings() }
        }
    }
    Group("Helpers at once") {
        SettingRow("At most", "How many helpers it may start in one go.", last = true) {
            Segmented((1..5).map { "$it" to "$it" }, prefs.helperParallel) { prefs.helperParallel = it; Helpers.writeSettings() }
        }
    }
    // names and roles are typed letter by letter: the server's config is updated once typing stops
    var metaRev by remember { mutableIntStateOf(0) }
    LaunchedEffect(metaRev) { if (metaRev > 0) { kotlinx.coroutines.delay(800); Helpers.sync() } }
    Group("Models that may help") {
        val picks = Graph.hub.picks.filter { it.key !in prefs.hiddenModelSet }
        if (picks.isEmpty()) SettingRow("No models yet", "Connect a provider in Models.", last = true) { }
        picks.forEachIndexed { i, p ->
            val on = p.key in prefs.helperModelList
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Txt(p.label, size = 15.sp, maxLines = 1)
                    Txt("${p.provider.name}  ·  ${Helpers.cost(p)}", size = 13.sp, color = c.textDim, maxLines = 1)
                }
                if (on) {
                    Segmented(listOf("ask" to "Ask first", "always" to "Always"), if (p.key in prefs.helperAlwaysSet) "always" else "ask") { prefs.setHelperAlways(p.key, it == "always"); apply() }
                    Spacer(Modifier.width(12.dp))
                }
                Toggle(on) { prefs.toggleHelper(p.key); if (!on) Helpers.justAdded = p.key; apply() }
            }
            if (on) Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                var n by remember(p.key) { mutableStateOf(Helpers.meta(p.key).first) }
                var r by remember(p.key) { mutableStateOf(Helpers.meta(p.key).second) }
                Field(n, { n = it; Helpers.setMeta(p.key, n, r); metaRev++ }, "Name, e.g. Reviewer", Modifier.width(180.dp).clip(Oc.round(8.dp)).border(1.dp, c.border, Oc.round(8.dp)).padding(10.dp), size = 14)
                Field(r, { r = it; Helpers.setMeta(p.key, n, r); metaRev++ }, "What it does, e.g. checks the change and runs the tests", Modifier.weight(1f).clip(Oc.round(8.dp)).border(1.dp, c.border, Oc.round(8.dp)).padding(10.dp), size = 14)
            }
            if (i != picks.lastIndex) Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(1.dp).background(c.border.copy(alpha = 0.6f)))
        }
    }
    Txt(
        "The main agent can change this team itself too: ask it to add, rename or switch off a helper. Click a helper's cloud above the field to see its task, its thinking and its steps.",
        size = 13.sp, color = c.textDim, modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
    )
}

/** Zips and .md files picked in Import: zips are unpacked, everything goes onto the shelves. */
internal fun importMemory(paths: List<String>) {
    runCatching {
        val tmp = kotlin.io.path.createTempDirectory("cl0udly-mem").toFile()
        paths.map(::File).forEach { f ->
            if (f.extension.lowercase() == "zip") f.inputStream().use { app.cl0udly.data.MemoryIO.unzip(it, tmp) }
            else if (f.isDirectory) f.copyRecursively(File(tmp, f.name), overwrite = true)
            else f.copyTo(File(tmp, f.name), overwrite = true)
        }
        val n = app.cl0udly.data.MemoryIO.import(tmp, Paths.memory)
        tmp.deleteRecursively()
        Graph.hub.toasts.tryEmit("Imported $n notes into memory")
    }.onFailure { Graph.hub.toasts.tryEmit("Couldn't import: ${it.message}") }
}

internal fun exportMemory() {
    runCatching {
        val home = System.getProperty("user.home")
        val dir = listOf("$home/Загрузки", "$home/Downloads").firstOrNull { File(it).isDirectory } ?: home
        val out = File(dir, "cl0udly-memory-${java.time.LocalDate.now()}.zip")
        out.outputStream().use { app.cl0udly.data.MemoryIO.zip(Paths.memory, it) }
        Graph.hub.toasts.tryEmit("Saved to ${out.path.replace(home, "~")}")
    }.onFailure { Graph.hub.toasts.tryEmit("Couldn't export: ${it.message}") }
}

