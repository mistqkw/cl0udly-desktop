package app.cl0udly.data

import app.cl0udly.Graph
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File

/**
 * Helpers: other models the agent may hand work to (OpenCode's task tool and subagents).
 * The user picks which models may help in Settings → Helpers; each one becomes a subagent
 * `helper-<model>` in the server's config. Calling one asks the user first (permission "task"),
 * unless they chose "Always allow" for that model.
 *
 * The working rules (brief replies, check every step, long output cut to a file, the loop guard)
 * live in an OpenCode plugin, [PLUGIN], with its settings in cl0udly-brain.json next to it.
 */
object Helpers {
    const val PLUGIN = "cl0udly-brain.js"

    private val prefs get() = Graph.prefs

    /** A helper just added: its cloud flies into the row above the field once. */
    var justAdded by androidx.compose.runtime.mutableStateOf<String?>(null)

    /** The subagent name: helper-<its own name, or the model id>, only letters, digits and dashes. */
    fun nameOf(p: Pick) = "helper-" + (meta(p.key).first.ifBlank { p.model.id }).lowercase().replace(Regex("[^a-z0-9а-яё]+"), "-").trim('-').take(40).ifBlank { "helper" }

    // ---------- names and roles: a helper can be "Reviewer: checks the change and runs the tests" ----------

    private fun metaJson(): JsonObject = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(prefs.helperMeta) as JsonObject }.getOrDefault(JsonObject(emptyMap()))

    /** A helper's own name and role (both may be blank: then it's just the model). */
    fun meta(key: String): Pair<String, String> = (metaJson()[key] as? JsonObject).let { o ->
        (o?.get("name")?.jsonPrimitive?.contentOrNull ?: "") to (o?.get("role")?.jsonPrimitive?.contentOrNull ?: "")
    }

    fun setMeta(key: String, name: String, role: String) {
        prefs.helperMeta = JsonObject(metaJson() + (key to buildJsonObject { put("name", name); put("role", role) })).toString()
    }

    /** Models that are or were helpers: the on ones first, then the ones switched off but still named. */
    fun known(): List<Pick> = (prefs.helperModelList + metaJson().keys).distinct().mapNotNull { k -> Graph.hub.picks.firstOrNull { it.key == k } }

    // ---------- the team file: the agent changes its helpers itself ----------

    val teamFile get() = File(Paths.opencodeConfig, "cl0udly-team.json")

    private fun teamText(): String = kotlinx.serialization.json.Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), buildJsonObject {
        put("max", prefs.helperParallel.toIntOrNull() ?: 3)
        put("members", buildJsonArray {
            known().forEach { p ->
                val (n, r) = meta(p.key)
                add(buildJsonObject { put("name", n.ifBlank { p.label }); put("role", r); put("model", p.key); put("on", p.key in prefs.helperModelList) })
            }
        })
    })

    /** The agent edited the file since cl0udly wrote it: take its lineup. True when something changed. */
    fun readTeam(): Boolean {
        val text = runCatching { teamFile.readText() }.getOrNull() ?: return false
        if (text == prefs.teamFileLast) return false
        return runCatching {
            val o = kotlinx.serialization.json.Json.parseToJsonElement(text) as JsonObject
            o["max"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?.let { prefs.helperParallel = it.coerceIn(1, 5).toString() }
            val on = ArrayList<String>()
            (o["members"] as? kotlinx.serialization.json.JsonArray)?.forEach { e ->
                val m = e as? JsonObject ?: return@forEach
                val key = m["model"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                if (Graph.hub.picks.none { it.key == key }) return@forEach
                setMeta(key, m["name"]?.jsonPrimitive?.contentOrNull.orEmpty().trim(), m["role"]?.jsonPrimitive?.contentOrNull.orEmpty().trim())
                if (m["on"]?.jsonPrimitive?.contentOrNull != "false") on += key
            }
            prefs.helperModels = on.joinToString("\n")
            prefs.teamFileLast = text
            true
        }.getOrDefault(false)
    }

    private fun writeTeam() {
        val text = teamText()
        if (text == prefs.teamFileLast && teamFile.exists()) return
        runCatching { teamFile.parentFile.mkdirs(); teamFile.writeText(text); prefs.teamFileLast = text }
    }

    /** Before a message: if the agent changed its team, apply it. */
    suspend fun pull() { if (readTeam()) sync() }

    /** Picks the user allowed as helpers, in their order. */
    fun chosen(): List<Pick> = prefs.helperModelList.mapNotNull { k -> Graph.hub.picks.firstOrNull { it.key == k } }

    /** Helper name → its model, for the clouds and the permission card. */
    fun pickFor(agent: String?): Pick? = agent?.let { a -> Graph.hub.picks.firstOrNull { nameOf(it) == a } }

    fun label(agent: String?): String = when (agent) {
        null, "" -> "Helper"
        "general" -> "General helper"
        "explore" -> "Explorer"
        else -> pickFor(agent)?.let { p -> meta(p.key).first.ifBlank { p.label } } ?: agent.removePrefix("helper-")
    }

    /** What a model costs the user: free, part of a subscription, or the price per million tokens. */
    fun cost(p: Pick): String {
        val c = p.model.cost
        val inp = c?.get("input")?.jsonPrimitive?.doubleOrNull ?: 0.0
        val out = c?.get("output")?.jsonPrimitive?.doubleOrNull ?: 0.0
        val subs = prefs.limitProviders.split(',').map { it.trim() }.filter { it.isNotBlank() }
        return when {
            p.model.id.endsWith("-free") || p.provider.id == FreeModels.POLLINATIONS_ID -> "free"
            p.provider.id in subs -> "subscription: ${p.provider.name}"
            p.provider.id == "github-copilot" || p.provider.id == "copilot-chat" -> "subscription: GitHub Copilot"
            inp == 0.0 && out == 0.0 -> "free"
            else -> "paid: \$${fmt(inp)} in, \$${fmt(out)} out per 1M tokens"
        }
    }

    private fun fmt(d: Double) = if (d >= 10) String.format(java.util.Locale.US, "%.0f", d) else String.format(java.util.Locale.US, "%.2f", d).trimEnd('0').trimEnd('.')

    /** How much of the subscription is left, as the agent sees it. */
    private fun budget(): String? {
        val s = Graph.usage.snapshot.value
        val w = listOf(s.fiveHour, s.week, s.month).filter { it.limit > 0 }
        if (w.isEmpty()) return null
        return "Subscription spent so far (counted by cl0udly): " + w.joinToString("; ") { "${it.label} \$${String.format(java.util.Locale.US, "%.2f", it.used)} of \$${String.format(java.util.Locale.US, "%.0f", it.limit)}" } +
            ". When a window is nearly used up, prefer free helpers."
    }

    /**
     * Where the agent is: on the phone it knows from the phone tools; here it is told it runs on the
     * user's own Linux computer, with what that allows, so it doesn't act like it's in a sandbox.
     */
    private fun place(): String {
        val home = System.getProperty("user.home")
        val de = System.getenv("XDG_CURRENT_DESKTOP").orEmpty().ifBlank { System.getenv("DESKTOP_SESSION").orEmpty() }
        val os = runCatching { File("/etc/os-release").readLines().firstOrNull { it.startsWith("PRETTY_NAME=") }?.substringAfter('=')?.trim('"') }.getOrNull() ?: "Linux"
        return "## Where you are\nYou run in cl0udly for desktop, on the user's own computer: $os" + (if (de.isNotBlank()) ", desktop $de" else "") +
            ", user ${System.getProperty("user.name")}, home $home. It is not a sandbox and not a phone: commands run for real on this machine, " +
            "files you write stay on it, the user sees your work in the app's panels (files, terminal, browser). " +
            "Use normal Linux tools (xdg-open opens a file or link for the user, notify-send shows a notification). " +
            "Ask before anything that touches the system itself (sudo, packages, services, deleting outside the project).\n" +
            "You can manage your helper subagents yourself: edit ${teamFile.absolutePath} (members: name, role, model \"provider/model\", on true|false; max). " +
            "Add, rename, switch off or change a helper's model there; it applies from the next message. Tell the user what you changed."
    }

    /** cl0udly-brain.json for the plugin: rules on or off, the cut length, helpers with their prices. */
    fun writeSettings(configDir: File = Paths.opencodeConfig) {
        runCatching {
            configDir.mkdirs()
            val json = buildJsonObject {
                put("style", prefs.brainStyle)
                put("pruneChars", prefs.brainPrune.toIntOrNull() ?: 8000)
                put("parallel", prefs.helperParallel.toIntOrNull() ?: 3)
                budget()?.let { put("budget", it) }
                put("extra", place())
                put("helpers", buildJsonArray {
                    chosen().forEach { p ->
                        val (n, r) = meta(p.key)
                        add(buildJsonObject { put("name", nameOf(p)); put("label", (if (n.isNotBlank()) "$n${if (r.isNotBlank()) " ($r)" else ""}, on " else "") + "${p.label} by ${p.provider.name}"); put("cost", cost(p)) })
                    }
                })
            }
            File(configDir, "cl0udly-brain.json").writeText(json.toString())
        }
    }

    /** Puts the plugin into our server's config; it is read when the server starts. */
    fun installPlugin(configDir: File = Paths.opencodeConfig) {
        runCatching {
            val dir = File(configDir, "plugin").apply { mkdirs() }
            val text = Helpers::class.java.getResourceAsStream("/brain/$PLUGIN")!!.use { it.readBytes() }
            val f = File(dir, PLUGIN)
            if (!f.exists() || !f.readBytes().contentEquals(text)) f.writeBytes(text)
        }
        writeSettings(configDir)
    }

    /**
     * Brings the server's subagents in line with the chosen helpers: one `helper-…` per model,
     * the ones no longer chosen turned off, and "task" asking first except for always-allowed ones.
     * Applied with PATCH /global/config, which takes effect at once.
     */
    suspend fun sync() {
        val picks = chosen()
        val names = picks.map(::nameOf).toSet()
        val old = prefs.helperAppliedList.toSet()
        val patch = buildJsonObject {
            putJsonObject("agent") {
                picks.forEach { p ->
                    putJsonObject(nameOf(p)) {
                        put("mode", "subagent")
                        put("model", p.key)
                        put("disable", false)
                        val (n, r) = meta(p.key)
                        put("description", (if (n.isNotBlank()) "$n${if (r.isNotBlank()) ": $r" else ""}. " else "") + "${p.label} by ${p.provider.name} (${cost(p)}). Give it a complete, self-contained task.")
                        if (r.isNotBlank()) put("prompt", "You are ${n.ifBlank { "a helper" }}, a member of a small team led by the main agent. Your role: $r. Do only the part you were given and finish with a short report of what you did and found.")
                    }
                }
                (old - names).forEach { n -> putJsonObject(n) { put("disable", true) } }
            }
            putJsonObject("permission") {
                // memory and the plugin's full-output files live outside the projects: reading them needs no OK
                putJsonObject("external_directory") {
                    put(Paths.memory.absolutePath + "/**", "allow")
                    put("/tmp/cl0udly-spill/**", "allow")
                    put(File(System.getProperty("java.io.tmpdir"), "cl0udly-spill").absolutePath + "/**", "allow")
                }
                putJsonObject("task") {
                    put("*", "ask")
                    picks.forEach { p -> put(nameOf(p), if (p.key in prefs.helperAlwaysSet) "allow" else "ask") }
                }
            }
        }
        runCatching { Graph.api.patchGlobalConfig(patch) }.onSuccess { prefs.helperApplied = names.joinToString("\n") }
            .onFailure { Graph.hub.toasts.tryEmit("Could not update helpers: ${it.message}") }
        writeTeam()
        writeSettings()
    }

    /** "Always allow" from the permission card: this model helps without asking from now on. */
    suspend fun alwaysAllow(agent: String) {
        val p = pickFor(agent) ?: return
        prefs.helperAlways = (prefs.helperAlwaysSet + p.key).joinToString("\n")
        sync()
    }

    /** The model's own name in a part's metadata, for helpers started before the list loaded. */
    fun modelOf(meta: JsonObject?): String? = (meta?.get("model") as? JsonObject)?.get("modelID")?.jsonPrimitive?.contentOrNull
}
