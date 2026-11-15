package app.cl0udly.data

import app.cl0udly.Graph
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.net.InetSocketAddress
import java.util.Base64
import java.util.concurrent.Executors

/**
 * The phone's way in for sync, next to the OpenCode server (its port + 1), only while Remote
 * Control is on and with the same password. The phone drives: it reads our chats through OpenCode
 * itself and brings its own here, since OpenCode 1.x has no import over HTTP.
 *  - GET  /sync/info    this computer's name and its chats folder;
 *  - POST /sync/import  one chat as `opencode export` JSON, imported into our chats (new messages are added);
 *  - POST /sync/memory  the phone's memory as a zip: merged into ours, and ours goes back the same way.
 */
object SyncServer {
    private var server: HttpServer? = null
    @Volatile var lastSync = 0L

    fun port() = (Graph.prefs.serverPort.toIntOrNull() ?: 4196) + 1

    /** Started or stopped to follow Remote Control. */
    fun follow(on: Boolean) {
        if (on && server == null) runCatching {
            server = HttpServer.create(InetSocketAddress("0.0.0.0", port()), 8).apply {
                executor = Executors.newFixedThreadPool(2)
                createContext("/sync/") { ex -> runCatching { handle(ex) }.onFailure { send(ex, 500, it.message ?: "error") } }
                start()
            }
        }
        if (!on) { server?.stop(0); server = null }
    }

    private fun handle(ex: HttpExchange) {
        val auth = ex.requestHeaders.getFirst("Authorization").orEmpty()
        val want = "Basic " + Base64.getEncoder().encodeToString("opencode:${Graph.prefs.serverPassword}".toByteArray())
        if (auth != want) return send(ex, 401, "wrong password")
        when (ex.requestURI.path) {
            "/sync/info" -> send(ex, 200, buildJsonObject { put("name", Graph.prefs.name); put("chats", Paths.chats.absolutePath) }.toString(), "application/json")
            "/sync/import" -> {
                val body = ex.requestBody.readBytes().decodeToString()
                val o = app.cl0udly.api.OcJson.parseToJsonElement(body) as JsonObject
                val info = o["info"] as JsonObject
                // the chat lands in our chats folder, wherever it lived on the phone
                val moved = JsonObject(o + ("info" to JsonObject(info + mapOf(
                    "directory" to JsonPrimitive(Paths.chats.absolutePath), "path" to JsonPrimitive(Paths.chats.absolutePath.trimStart('/')),
                ))))
                val f = File.createTempFile("cl0udly-sync", ".json").apply { writeText(moved.toString()) }
                val (code, out) = try { Graph.runtime.cli("import", f.absolutePath) } finally { f.delete() }
                if (code != 0) return send(ex, 500, out.takeLast(400))
                lastSync = System.currentTimeMillis()
                Graph.scope.launch { Graph.hub.reload() }
                send(ex, 200, "ok")
            }
            "/sync/memory" -> {
                val tmp = File.createTempFile("cl0udly-mem", "").apply { delete(); mkdirs() }
                try {
                    MemoryIO.unzip(ex.requestBody, tmp)
                    MemoryIO.import(tmp, Paths.memory)
                } finally { tmp.deleteRecursively() }
                ex.responseHeaders.add("Content-Type", "application/zip")
                ex.sendResponseHeaders(200, 0)
                ex.responseBody.use { MemoryIO.zip(Paths.memory, it) }
            }
            else -> send(ex, 404, "no such thing")
        }
    }

    private fun send(ex: HttpExchange, code: Int, text: String, type: String = "text/plain; charset=utf-8") {
        val b = text.toByteArray()
        ex.responseHeaders.add("Content-Type", type)
        ex.sendResponseHeaders(code, b.size.toLong())
        ex.responseBody.use { it.write(b) }
    }
}
