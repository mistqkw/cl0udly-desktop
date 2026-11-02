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
object Paths {
    private val home = System.getProperty("user.home")
    val data = File(System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.local/share", "cl0udly")
    val config = File(System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.config", "cl0udly")
    /** Plain chats live in their own folder, everything else is a project (Code). */
    val chats = File(data, "chats")
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

    val projectList: List<String> get() = projects.split('\n').filter { it.isNotBlank() }
    fun addProject(dir: String) { projects = (listOf(dir) + projectList.filter { it != dir }).take(20).joinToString("\n") }
    fun removeProject(dir: String) { projects = projectList.filter { it != dir }.joinToString("\n") }

    companion object {
        fun parse(o: JsonObject, k: String) = o[k]?.jsonPrimitive?.contentOrNull
    }
}
