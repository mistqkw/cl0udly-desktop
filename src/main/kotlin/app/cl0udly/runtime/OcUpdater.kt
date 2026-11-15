package app.cl0udly.runtime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.cl0udly.Graph
import app.cl0udly.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.Credentials
import okhttp3.Request
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

/**
 * OpenCode keeps itself up to date: once a day GitHub is asked for a newer build on the same major
 * line; it is downloaded next to the running one, and when no chat is working it is tried on a
 * spare port with a copy of the settings (the server answers, models load, our plugin loads). Only
 * then does it take over; a build that fails is skipped and the reason shown in Settings → Server.
 */
class OcUpdater(private val prefs: Prefs, private val runtime: RuntimeController, private val scope: CoroutineScope) {
    /** What it's doing right now, for Settings; null when nothing. */
    var busy by mutableStateOf<String?>(null)
        private set

    private val major = RuntimeController.OC_VERSION.substringBefore('.')

    fun start() {
        scope.launch {
            delay(120_000)
            while (true) {
                if (System.currentTimeMillis() - (prefs.ocChecked.toLongOrNull() ?: 0) > 24 * 3600_000L) check()
                delay(3600_000)
            }
        }
    }

    /** One pass: look, fetch, wait for a quiet moment, test, switch. */
    suspend fun check(manual: Boolean = false) {
        if (busy != null || prefs.remoteUrl.isNotBlank()) return
        try {
            busy = "Checking for a new OpenCode…"
            val (v, url, sha) = withContext(Dispatchers.IO) { latest() } ?: run {
                prefs.ocChecked = System.currentTimeMillis().toString()
                if (manual) prefs.ocUpdateError = ""
                return
            }
            prefs.ocChecked = System.currentTimeMillis().toString()
            busy = "Downloading OpenCode $v…"
            val dir = File(runtime.folder, "next").apply { deleteRecursively() }
            withContext(Dispatchers.IO) { runtime.download(url, sha, dir, "") }
            busy = "OpenCode $v is waiting for the chats to finish"
            waitQuiet()
            busy = "Testing OpenCode $v…"
            val why = withContext(Dispatchers.IO) { smoke(File(dir, app.cl0udly.data.Os.ocExe), v) }
            if (why != null) {
                dir.deleteRecursively()
                prefs.ocSkip = v; prefs.ocUpdateError = "OpenCode $v stays off: ${app.cl0udly.data.I18n.t(why)}"
                return
            }
            busy = "Switching to OpenCode $v…"
            val fail = runtime.switchTo(dir, v)
            if (fail != null) { prefs.ocSkip = v; prefs.ocUpdateError = "OpenCode $v did not start, back on the previous one: ${app.cl0udly.data.I18n.t(fail)}"; return }
            prefs.ocUpdateError = ""; prefs.ocNotice = v
        } catch (e: Exception) {
            prefs.ocUpdateError = "Could not update OpenCode: ${e.message ?: e::class.simpleName}"
        } finally {
            busy = null
        }
    }

    /** Back to the version kept in prev/. */
    suspend fun rollback() {
        val v = runtime.previous ?: return
        if (busy != null) return
        busy = "Going back to OpenCode $v…"
        try {
            val cur = runtime.version
            val fail = runtime.switchTo(File(runtime.folder, "prev"), v)
            if (fail == null) { prefs.ocSkip = cur; prefs.ocUpdateError = ""; prefs.ocNotice = v } else prefs.ocUpdateError = "Could not go back: $fail"
        } finally { busy = null }
    }

    /** Two quiet looks a minute apart: no chat working, the server up. */
    private suspend fun waitQuiet() {
        var quiet = 0
        while (quiet < 2) {
            delay(60_000)
            quiet = if (Graph.hub.statuses.isEmpty() && runtime.state.value is RuntimeState.Running) quiet + 1 else 0
        }
    }

    /** The newest release on our major line that is newer than the running one: (version, archive url, sha256). */
    private fun latest(): Triple<String, String, String>? {
        val req = Request.Builder().url("https://api.github.com/repos/anomalyco/opencode/releases?per_page=40")
            .header("Accept", "application/vnd.github+json").header("User-Agent", "cl0udly").build()
        val body = Graph.api.http.newCall(req).execute().use { if (!it.isSuccessful) error("GitHub: HTTP ${it.code}"); it.body.string() }
        return Json.parseToJsonElement(body).jsonArray.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            if (o.str("draft") == "true" || o.str("prerelease") == "true") return@mapNotNull null
            val v = o.str("tag_name")?.removePrefix("v") ?: return@mapNotNull null
            if (!Regex("""\d+\.\d+\.\d+""").matches(v) || v.substringBefore('.') != major) return@mapNotNull null
            if (v == prefs.ocSkip || !newer(v, runtime.version)) return@mapNotNull null
            val asset = (o["assets"] as? JsonArray)?.map { it.jsonObject }?.firstOrNull { it.str("name") == app.cl0udly.data.Os.ocAsset } ?: return@mapNotNull null
            val sha = asset.str("digest")?.removePrefix("sha256:") ?: return@mapNotNull null
            Triple(v, asset.str("browser_download_url") ?: return@mapNotNull null, sha)
        }.maxWithOrNull { a, b -> if (newer(a.first, b.first)) 1 else if (a.first == b.first) 0 else -1 }
    }

    /** Runs [bin] on a spare port with a copy of our settings and keys; null when it works the way the app needs. */
    private fun smoke(bin: File, v: String): String? {
        if (!bin.canExecute()) return "no opencode binary in the archive"
        val root = File(runtime.folder, "smoke").apply { deleteRecursively(); mkdirs() }
        File(runtime.folder, "config/opencode").takeIf { it.isDirectory }?.copyRecursively(File(root, "config/opencode"))
        File(runtime.folder, "data/opencode/auth.json").takeIf { it.isFile }?.copyTo(File(root, "data/opencode/auth.json"))
        val port = ServerSocket(0).use { it.localPort }
        val pass = java.util.UUID.randomUUID().toString()
        val pb = ProcessBuilder(bin.absolutePath, "serve", "--hostname", "127.0.0.1", "--port", "$port")
            .directory(File(root, "work").apply { mkdirs() }).redirectErrorStream(true).redirectOutput(File(root, "serve.log"))
        pb.environment()["OPENCODE_SERVER_PASSWORD"] = pass
        pb.environment()["OPENCODE_DISABLE_AUTOUPDATE"] = "true"
        listOf("XDG_CONFIG_HOME" to "config", "XDG_DATA_HOME" to "data", "XDG_STATE_HOME" to "state", "XDG_CACHE_HOME" to "cache").forEach { (k, d) ->
            pb.environment()[k] = File(root, d).also { it.mkdirs() }.absolutePath
        }
        val p = pb.start()
        val http = Graph.api.http.newBuilder().readTimeout(10, TimeUnit.SECONDS).build()
        fun get(path: String): String? = runCatching {
            http.newCall(Request.Builder().url("http://127.0.0.1:$port$path").header("Authorization", Credentials.basic("opencode", pass)).build())
                .execute().use { if (it.isSuccessful) it.body.string() else null }
        }.getOrNull()
        try {
            var health: String? = null
            for (i in 0 until 120) { if (!p.isAlive) break; health = get("/global/health"); if (health != null) break; Thread.sleep(250) }
            if (health == null) return "the server did not answer" + (if (!p.isAlive) " (exit ${p.exitValue()})" else "")
            val h = runCatching { Json.parseToJsonElement(health).jsonObject }.getOrNull()
                ?: return "its API changed (the app speaks OpenCode $major.x)"
            if (h.str("version") != v) return "it reports version ${h.str("version")}"
            if (runCatching { Json.parseToJsonElement(get("/session")!!) as JsonArray }.isFailure) return "its API changed (the app speaks OpenCode $major.x)"
            val providers = runCatching { Json.parseToJsonElement(get("/config/providers")!!).jsonObject["providers"]!!.jsonArray }.getOrNull()
            if (providers == null || providers.none { (it.jsonObject["models"] as? JsonObject)?.isNotEmpty() == true }) return "the models did not load"
            val plugins = runCatching { Json.parseToJsonElement(get("/config")!!).jsonObject["plugin"]!!.jsonArray.map { (it as JsonPrimitive).content } }.getOrNull()
            if (plugins == null || plugins.none { "cl0udly-brain" in it }) return "the cl0udly plugin did not load"
            return null
        } finally {
            p.destroy(); if (!p.waitFor(3, TimeUnit.SECONDS)) p.destroyForcibly()
            root.deleteRecursively()
        }
    }

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.content

    private fun newer(a: String, b: String): Boolean {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }; val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) { val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }; if (d != 0) return d > 0 }
        return false
    }
}
