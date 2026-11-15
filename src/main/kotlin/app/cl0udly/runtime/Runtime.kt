package app.cl0udly.runtime

import app.cl0udly.Graph
import app.cl0udly.data.Paths
import app.cl0udly.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.swing.Swing
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom

sealed interface RuntimeState {
    data object Idle : RuntimeState
    /** Downloading OpenCode: share 0..1, or -1 when the size is unknown. */
    data class Installing(val share: Float, val note: String) : RuntimeState
    data object Starting : RuntimeState
    data class Running(val version: String) : RuntimeState
    data class Failed(val reason: String) : RuntimeState
}

/**
 * Our own `opencode serve` on 127.0.0.1, like the phone app, only without proot: the official
 * Linux build is downloaded into ~/.local/share/cl0udly/opencode/bin and kept on the 1.x line the
 * client is written for (OpenCode 2 has a different API). [OcUpdater] brings newer 1.x builds in;
 * the one before stays in prev/ to go back to.
 */
class RuntimeController(private val prefs: Prefs, private val scope: CoroutineScope) {
    companion object {
        const val OC_VERSION = "1.19.4"
        const val OC_SHA = "849bd6b3d959a1d2ef200ed1634a51c79a45ca236aa26665d75debd694805cce"
        fun ocUrl(v: String) = "https://github.com/anomalyco/opencode/releases/download/v$v/${app.cl0udly.data.Os.ocAsset}"
    }

    val state = MutableStateFlow<RuntimeState>(RuntimeState.Idle)
    private val home = File(Paths.data, "opencode")
    private val bin = File(home, "bin/" + app.cl0udly.data.Os.ocExe)
    private val log = File(home, "serve.log")
    private var process: Process? = null
    private var watch: Job? = null
    @Volatile private var stopping = false

    val logFile: File get() = log
    val installed get() = bin.canExecute()
    /** The version in bin/, and the one kept in prev/ (null when there is none). */
    val version get() = runCatching { File(home, "bin/VERSION").readText().trim() }.getOrNull()?.ifBlank { null } ?: OC_VERSION
    val previous get() = File(home, "prev/" + app.cl0udly.data.Os.ocExe).takeIf { it.canExecute() }?.let { runCatching { File(home, "prev/VERSION").readText().trim() }.getOrNull() ?: OC_VERSION }
    val folder: File get() = home

    fun start() {
        if (watch?.isActive == true) return
        stopping = false
        watch = scope.launch(Dispatchers.IO) {
            if (prefs.serverPassword.isBlank()) prefs.serverPassword = newPassword()
            if (!installed) try { install() } catch (e: Exception) {
                state.value = RuntimeState.Failed("Could not download OpenCode: ${e.message}"); return@launch
            }
            var pause = 1000L
            while (isActive && !stopping) {
                state.value = RuntimeState.Starting
                val p = try { spawn() } catch (e: Exception) { state.value = RuntimeState.Failed(e.message ?: "Could not start"); return@launch }
                process = p
                val version = waitReady(p)
                if (version != null) { state.value = RuntimeState.Running(version); pause = 1000L }
                runCatching { p.waitFor() }
                process = null
                if (stopping) break
                state.value = RuntimeState.Failed("The server stopped, restarting…")
                delay(pause); pause = (pause * 2).coerceAtMost(30_000)
            }
            state.value = RuntimeState.Idle
        }
    }

    fun stop() {
        stopping = true
        process?.let { p -> p.destroy(); if (!p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly() }
        process = null
        watch?.cancel(); watch = null
        state.value = RuntimeState.Idle
    }

    fun restart() { scope.launch { withContext(Dispatchers.IO) { stop() }; start() } }

    /** Runs `opencode <args>` against our server's data (import, export): exit code and output. */
    fun cli(vararg args: String): Pair<Int, String> {
        val pb = ProcessBuilder(listOf(bin.absolutePath) + args).directory(Paths.chats.also { it.mkdirs() }).redirectErrorStream(true)
        env(pb)
        val p = pb.start()
        val out = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(120, java.util.concurrent.TimeUnit.SECONDS)) { p.destroyForcibly(); return -1 to "timed out" }
        return p.exitValue() to out
    }

    private fun env(pb: ProcessBuilder) {
        pb.environment()["OPENCODE_SERVER_PASSWORD"] = prefs.serverPassword
        pb.environment()["OPENCODE_DISABLE_AUTOUPDATE"] = "true"
        // its own config, keys and history: the OpenCode 2 installed on this PC keeps its files apart
        listOf("XDG_CONFIG_HOME" to "config", "XDG_DATA_HOME" to "data", "XDG_STATE_HOME" to "state", "XDG_CACHE_HOME" to "cache").forEach { (k, d) ->
            pb.environment()[k] = File(home, d).also { it.mkdirs() }.absolutePath
        }
    }

    private fun spawn(): Process {
        Paths.chats.mkdirs()
        app.cl0udly.data.Helpers.installPlugin()
        app.cl0udly.data.Memory.ensure()
        val pb = // Remote control: the phone on the same network joins this server (the password still guards it)
        ProcessBuilder(bin.absolutePath, "serve", "--hostname", if (prefs.remoteControl) "0.0.0.0" else "127.0.0.1", "--port", prefs.serverPort)
            .directory(Paths.chats)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(log.also { it.parentFile.mkdirs(); if (it.length() > 2_000_000) it.delete() }))
        env(pb)
        return pb.start()
    }

    private suspend fun waitReady(p: Process): String? {
        repeat(150) {
            if (!p.isAlive) return null
            runCatching { Graph.api.healthVersionOrThrow() }.getOrNull()?.let { return it }
            delay(200)
        }
        return null
    }

    private fun install() {
        // the pinned sum is for the Linux x64 build; other systems take the sum GitHub lists for their archive
        val sha = if (app.cl0udly.data.Os.ocAsset == "opencode-linux-x64.tar.gz") OC_SHA else releaseDigest(OC_VERSION, app.cl0udly.data.Os.ocAsset)
        download(ocUrl(OC_VERSION), sha, File(home, "bin"), "Downloading OpenCode $OC_VERSION")
        if (!bin.canExecute()) error("no opencode binary in the archive")
    }

    /** Fetches a release archive, checks its sha256 and unpacks it into [into]. */
    fun download(url: String, sha256: String, into: File, note: String) {
        home.mkdirs()
        val zip = url.endsWith(".zip")
        val tgz = File(home, if (zip) "opencode.zip.part" else "opencode.tar.gz.part")
        state.value = RuntimeState.Installing(0f, note).takeIf { into.name == "bin" } ?: state.value
        Graph.api.http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            val total = r.body.contentLength()
            val md = MessageDigest.getInstance("SHA-256")
            r.body.byteStream().use { input ->
                tgz.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var got = 0L
                    var last = 0L
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf, 0, n); md.update(buf, 0, n); got += n
                        if (got - last > 512 * 1024 && into.name == "bin") {
                            last = got
                            state.value = RuntimeState.Installing(if (total > 0) got.toFloat() / total else -1f, note)
                        }
                    }
                }
            }
            val sha = md.digest().joinToString("") { "%02x".format(it) }
            if (!sha256.startsWith("@") && sha != sha256) { tgz.delete(); error("checksum does not match") }
        }
        if (into.name == "bin") state.value = RuntimeState.Installing(1f, "Unpacking")
        into.mkdirs()
        if (zip) unzip(tgz, into) else {
            val tar = ProcessBuilder("tar", "-xzf", tgz.absolutePath, "-C", into.absolutePath).redirectErrorStream(true).start()
            val out = tar.inputStream.bufferedReader().readText()
            if (tar.waitFor() != 0) error("tar: $out")
        }
        tgz.delete()
        File(into, app.cl0udly.data.Os.ocExe).setExecutable(true)
    }

    /** Windows and macOS builds come as zip: the program is at the top of it. */
    private fun unzip(zip: File, into: File) {
        java.util.zip.ZipInputStream(zip.inputStream().buffered()).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val f = File(into, e.name)
                if (!e.isDirectory && f.canonicalPath.startsWith(into.canonicalPath)) { f.parentFile.mkdirs(); f.outputStream().use { z.copyTo(it) } }
                e = z.nextEntry
            }
        }
    }

    /** The sha256 GitHub lists for an OpenCode release archive. */
    fun releaseDigest(v: String, asset: String): String {
        val req = Request.Builder().url("https://api.github.com/repos/anomalyco/opencode/releases/tags/v$v")
            .header("Accept", "application/vnd.github+json").header("User-Agent", "cl0udly").build()
        val body = Graph.api.http.newCall(req).execute().use { if (!it.isSuccessful) error("GitHub: HTTP ${it.code}"); it.body.string() }
        val o = app.cl0udly.api.OcJson.parseToJsonElement(body) as kotlinx.serialization.json.JsonObject
        val a = (o["assets"] as kotlinx.serialization.json.JsonArray).map { it as kotlinx.serialization.json.JsonObject }
            .firstOrNull { (it["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content == asset } ?: error("no $asset in OpenCode $v")
        return (a["digest"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.removePrefix("sha256:") ?: error("no checksum for $asset")
    }

    /**
     * Puts the build in [dir] (version [v]) in place of the running one, which moves to prev/, and
     * starts it. When it doesn't come up within a minute the old one goes back: returns why, or null.
     */
    suspend fun switchTo(dir: File, v: String): String? = withContext(Dispatchers.IO) {
        stop()
        val prev = File(home, "prev"); val cur = File(home, "bin"); val tmp = File(home, "swap")
        tmp.deleteRecursively()
        if (dir == prev) { if (!prev.renameTo(tmp)) return@withContext "could not move files" } else prev.deleteRecursively()
        if (!File(cur, "VERSION").exists()) File(cur, "VERSION").writeText(OC_VERSION)
        cur.renameTo(prev)
        (if (dir == prev) tmp else dir).renameTo(cur)
        File(cur, "VERSION").writeText(v)
        withContext(Dispatchers.Swing) { start() }
        val ok = withTimeoutOrNull(60_000) { state.first { it is RuntimeState.Running } } != null
        if (ok) return@withContext null
        val why = (state.value as? RuntimeState.Failed)?.reason ?: "it did not start"
        // back to the one that worked
        stop()
        cur.renameTo(tmp); prev.renameTo(cur); tmp.renameTo(prev)
        withContext(Dispatchers.Swing) { start() }
        why
    }

    private fun newPassword(): String {
        val b = ByteArray(18); SecureRandom().nextBytes(b)
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    }
}
