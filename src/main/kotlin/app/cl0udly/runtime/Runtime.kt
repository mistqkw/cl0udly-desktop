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
 * Linux build is downloaded once into ~/.local/share/cl0udly/opencode and pinned to the 1.x line
 * the client is written for (OpenCode 2 has a different API).
 */
class RuntimeController(private val prefs: Prefs, private val scope: CoroutineScope) {
    companion object {
        const val OC_VERSION = "1.19.4"
        const val OC_SHA = "849bd6b3d959a1d2ef200ed1634a51c79a45ca236aa26665d75debd694805cce"
        fun ocUrl(v: String) = "https://github.com/anomalyco/opencode/releases/download/v$v/opencode-linux-x64.tar.gz"
    }

    val state = MutableStateFlow<RuntimeState>(RuntimeState.Idle)
    private val home = File(Paths.data, "opencode")
    private val bin = File(home, "bin/opencode")
    private val log = File(home, "serve.log")
    private var process: Process? = null
    private var watch: Job? = null
    @Volatile private var stopping = false

    val logFile: File get() = log
    val installed get() = bin.canExecute()

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

    private fun spawn(): Process {
        Paths.chats.mkdirs()
        val pb = // Remote control: the phone on the same network joins this server (the password still guards it)
        ProcessBuilder(bin.absolutePath, "serve", "--hostname", if (prefs.remoteControl) "0.0.0.0" else "127.0.0.1", "--port", prefs.serverPort)
            .directory(Paths.chats)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(log.also { it.parentFile.mkdirs(); if (it.length() > 2_000_000) it.delete() }))
        pb.environment()["OPENCODE_SERVER_PASSWORD"] = prefs.serverPassword
        pb.environment()["OPENCODE_DISABLE_AUTOUPDATE"] = "true"
        // its own config, keys and history: the OpenCode 2 installed on this PC keeps its files apart
        listOf("XDG_CONFIG_HOME" to "config", "XDG_DATA_HOME" to "data", "XDG_STATE_HOME" to "state", "XDG_CACHE_HOME" to "cache").forEach { (k, d) ->
            pb.environment()[k] = File(home, d).also { it.mkdirs() }.absolutePath
        }
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
        home.mkdirs()
        val tgz = File(home, "opencode.tar.gz.part")
        state.value = RuntimeState.Installing(0f, "Downloading OpenCode $OC_VERSION")
        Graph.api.http.newCall(Request.Builder().url(ocUrl(OC_VERSION)).build()).execute().use { r ->
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
                        if (got - last > 512 * 1024) {
                            last = got
                            state.value = RuntimeState.Installing(if (total > 0) got.toFloat() / total else -1f, "Downloading OpenCode $OC_VERSION")
                        }
                    }
                }
            }
            val sha = md.digest().joinToString("") { "%02x".format(it) }
            if (!OC_SHA.startsWith("@") && sha != OC_SHA) { tgz.delete(); error("checksum does not match") }
        }
        state.value = RuntimeState.Installing(1f, "Unpacking")
        File(home, "bin").mkdirs()
        val tar = ProcessBuilder("tar", "-xzf", tgz.absolutePath, "-C", File(home, "bin").absolutePath).redirectErrorStream(true).start()
        val out = tar.inputStream.bufferedReader().readText()
        if (tar.waitFor() != 0) error("tar: $out")
        tgz.delete()
        bin.setExecutable(true)
        if (!bin.canExecute()) error("no opencode binary in the archive")
    }

    private fun newPassword(): String {
        val b = ByteArray(18); SecureRandom().nextBytes(b)
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    }
}
