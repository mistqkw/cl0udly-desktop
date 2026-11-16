package app.cl0udly.data

import androidx.compose.runtime.mutableStateMapOf
import app.cl0udly.api.OcJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Where the app keeps its files: XDG folders, like every Linux program. */
val SIDEBAR_ROWS = listOf("search", "projects", "memory", "customize")

object Paths {
    private val home = System.getProperty("user.home")
    // XDG folders on Linux, AppData on Windows, Application Support on macOS
    val data = File(Os.dataHome, "cl0udly")
    val config = File(Os.configHome, "cl0udly")
    /** Plain chats live in their own folder, everything else is a project (Code). */
    val chats = File(data, "chats")
    /** Our OpenCode's config folder (its XDG_CONFIG_HOME is data/opencode/config). */
    val opencodeConfig = File(data, "opencode/config/opencode")
    /** Memory: notes the agent keeps about the user, the same layout as on the phone. */
    val memory = File(data, "memory")
}

/** Folder of plain chats, as the server sees it. */
val CHAT_DIR: String get() = Paths.chats.absolutePath

/**
 * Settings in ~/.config/cl0udly/prefs.json. Every value is a string in a state map, so a change
 * recomposes the screens that read it, and the file is written right away.
 */
class Prefs(private val file: File = File(Paths.config, "prefs.json")) {
    private val values = mutableStateMapOf<String, String>()

    init {
        runCatching {
            (OcJson.parseToJsonElement(file.readText()) as? JsonObject)?.forEach { (k, v) ->
                (v as? JsonPrimitive)?.contentOrNull?.let { values[k] = it }
            }
        }
    }

    private fun save() {
        runCatching {
            file.parentFile.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(OcJson.encodeToString(JsonObject.serializer(), JsonObject(values.toMap().mapValues { JsonPrimitive(it.value) })))
            tmp.renameTo(file)
        }
    }

    private fun str(default: String) = object : ReadWriteProperty<Any?, String> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = values[property.name] ?: default
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            if (values[property.name] == value) return
            values[property.name] = value; save()
        }
    }

    private fun flag(default: Boolean) = object : ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = values[property.name]?.toBooleanStrictOrNull() ?: default
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            if (values[property.name] == value.toString()) return
            values[property.name] = value.toString(); save()
        }
    }

    // server
    var serverPort by str("4196")
    var serverPassword by str("")
    /** Another computer's `opencode serve` (address and password); empty means our own server. */
    /** Dictation language for Whisper: "ru" (with English words), "en" or "auto". */
    var voiceLang by str("ru")
    /** Closing the window hides it to the tray (when the panel has one) instead of quitting. */
    var closeToTray by flag(true)
    var sounds by flag(true)
    /** The limit window already warned about, "5-hour@<reset>". */
    var limitWarned by str("")
    /** system | en | ru: the app's own words. */
    var language by str("system")
    var trayHintShown by flag(false)
    var seenVersion by str("")
    var remoteUrl by str("")
    var remotePassword by str("")

    // you
    var name by str(System.getProperty("user.name").replaceFirstChar { it.uppercase() })

    // look
    var edition by str("cloud")
    var colorMode by str("system")
    var textSize by str("default")
    var sidebarOpen by flag(true)

    // chat
    /** chat | code: the switch in the title bar, like Claude desktop. */
    var mode by str("chat")
    var providerId by str("")
    var modelId by str("")
    var variant by str("")
    var agent by str("build")
    /** Recent project folders for Code, newest first, separated by a line break. */
    var projects by str("")

    // the mascot
    var mascotSleepy by flag(true)
    var mascotMorning by flag(true)
    var mascotSeasonal by flag(true)
    var mascotBattery by flag(true)
    var mascotFollow by flag(true)
    var mascotOutfit by str("auto")
    var lastMorning by str("")
    var petCount by str("0")
    var introSeen by flag(false)

    /** Pinned session ids, newest first. */
    var pinned by str("")
    /** Sidebar rows turned off in Appearance → Sidebar, one key per line. */
    var sidebarHidden by str("")
    val sidebarHiddenSet get() = sidebarHidden.split('\n').filter { it.isNotBlank() }.toSet()
    fun sidebarShows(key: String) = key !in sidebarHiddenSet
    fun toggleSidebar(key: String) { sidebarHidden = (if (key in sidebarHiddenSet) sidebarHiddenSet - key else sidebarHiddenSet + key).joinToString("\n") }
    /** The order of the sidebar's top rows (as the phone's side menu); keys it doesn't know yet go at the end. */
    var sidebarOrder by str("search,projects,memory,customize")
    val sidebarRows get() = (sidebarOrder.split(',').filter { it in SIDEBAR_ROWS } + SIDEBAR_ROWS).distinct()
    fun moveSidebar(key: String, by: Int) {
        val o = sidebarRows.toMutableList()
        val i = o.indexOf(key); val j = i + by
        if (i < 0 || j !in o.indices) return
        o.removeAt(i); o.add(j, key)
        sidebarOrder = o.joinToString(",")
    }
    /** Recently picked models ("provider/model"), the top four get number keys in the picker. */
    var recentModels by str("")
    /** Models hidden from the picker (OpenCode desktop: Settings → Models toggles). */
    var hiddenModels by str("")
    /** manual: ask before tools; auto: allow everything, like Claude's Auto. */
    var permMode by str("manual")
    /** normal | thinking | verbose: how much of the agent's work the transcript shows. */
    var transcriptView by str("normal")
    var transcriptWidth by str("narrow")
    var motion by str("system")
    var userAbout by str("")

    // notifications and the desktop
    var notifyDone by flag(true)
    var notifyPermission by flag(true)
    var notifyErrors by flag(false)
    var keepAwake by flag(true)
    var runOnStartup by flag(false)
    var autoOpenFiles by flag(true)
    var linksInBrowser by flag(true)
    var terminalShell by str("")

    // Remote control: the phone joins this computer's server over the local network
    var remoteControl by flag(false)
    /** OpenCode updates: when GitHub was last asked, a version that failed its test, why, and a note for the user. */
    var ocChecked by str("0")
    var ocSkip by str("")
    var ocUpdateError by str("")
    var ocNotice by str("")

    // usage limits (OpenCode Go: $12 per 5 hours, $30 a week, $60 a month), counted locally
    var limit5h by str("12")
    var limitWeek by str("30")
    var limitMonth by str("60")
    var limitProviders by str("opencode-go")

    // helpers and working rules (Settings → Helpers)
    /** Models allowed to help, "provider/model" per line. */
    var helperModels by str("")
    /** Of those, the ones that help without asking first. */
    var helperAlways by str("")
    /** Subagent names last written to the server's config, to turn off the ones dropped since. */
    var helperApplied by str("")
    var helperParallel by str("3")
    /** Helpers' own names and roles: {"provider/model": {"name", "role"}}. */
    var helperMeta by str("{}")
    /** The team file as cl0udly last wrote it: a different text means the agent changed it. */
    var teamFileLast by str("")
    /** Brief replies and a check of every step, from the cl0udly-brain plugin. */
    var brainStyle by flag(true)
    /** Tool output longer than this many characters is cut to its head and tail; 0 keeps it whole. */
    var brainPrune by str("8000")

    // memory (Settings → Memory)
    var memoryOn by flag(true)
    var memoryAutoSave by flag(true)
    /** ask | auto | off: offer facts as "Remember this?" cards, save them on its own, or only when asked. */
    var memorySave by str("")
    val memoryMode get() = memorySave.ifBlank { if (memoryAutoSave) "ask" else "off" }
    /** Answers to "Remember this?" cards: callID:y|n, newest last. */
    var memoryAnswers by str("")
    fun memoryAnswer(call: String) = memoryAnswers.split(',').firstOrNull { it.startsWith("$call:") }?.substringAfter(':')
    fun answerMemory(call: String, yes: Boolean) {
        memoryAnswers = (memoryAnswers.split(',').filter { it.isNotBlank() && !it.startsWith("$call:") } + "$call:${if (yes) "y" else "n"}").takeLast(300).joinToString(",")
    }
    /** Chats where memory is off, one session id per line. */
    var memoryOff by str("")

    // account
    var signedIn by str("")
    var avatarPath by str("")

    val projectList: List<String> get() = projects.split('\n').filter { it.isNotBlank() }
    val pinnedList: List<String> get() = pinned.split('\n').filter { it.isNotBlank() }
    val helperModelList get() = helperModels.lines().filter { it.isNotBlank() }
    val helperAlwaysSet get() = helperAlways.lines().filter { it.isNotBlank() }.toSet()
    val helperAppliedList get() = helperApplied.lines().filter { it.isNotBlank() }
    val memoryOffSet get() = memoryOff.lines().filter { it.isNotBlank() }.toSet()
    fun memoryOn(sessionId: String?) = memoryOn && (sessionId == null || sessionId !in memoryOffSet)
    fun setChatMemory(sessionId: String, on: Boolean) { memoryOff = (if (on) memoryOffSet - sessionId else memoryOffSet + sessionId).joinToString("\n") }
    fun toggleHelper(key: String) { helperModels = (if (key in helperModelList) helperModelList - key else helperModelList + key).joinToString("\n") }
    fun setHelperAlways(key: String, on: Boolean) { helperAlways = (if (on) helperAlwaysSet + key else helperAlwaysSet - key).joinToString("\n") }

    /** What you were typing in a chat, kept until you send it or erase it. */
    fun draft(sessionId: String) = values["draft_$sessionId"].orEmpty()
    fun setDraft(sessionId: String, text: String) {
        if (values["draft_$sessionId"].orEmpty() == text) return
        if (text.isBlank()) values.remove("draft_$sessionId") else values["draft_$sessionId"] = text
        save()
    }
    fun togglePin(id: String) { pinned = (if (id in pinnedList) pinnedList - id else listOf(id) + pinnedList).joinToString("\n") }
    val recentModelList: List<String> get() = recentModels.split('\n').filter { it.isNotBlank() }
    /** A model already in the picker's short list keeps its place: picking the second one doesn't make it first. */
    fun useModel(key: String) { if (key in recentModelList.take(4)) return; recentModels = (listOf(key) + recentModelList.filter { it != key }).take(8).joinToString("\n") }
    val hiddenModelSet: Set<String> get() = hiddenModels.split('\n').filter { it.isNotBlank() }.toSet()
    fun toggleHidden(key: String) { hiddenModels = (if (key in hiddenModelSet) hiddenModelSet - key else hiddenModelSet + key).joinToString("\n") }
    fun addProject(dir: String) { projects = (listOf(dir) + projectList.filter { it != dir }).take(20).joinToString("\n") }
    fun removeProject(dir: String) { projects = projectList.filter { it != dir }.joinToString("\n") }

    companion object {
        fun parse(o: JsonObject, k: String) = o[k]?.jsonPrimitive?.contentOrNull
    }
}
