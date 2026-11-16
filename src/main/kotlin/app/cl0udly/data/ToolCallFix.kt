package app.cl0udly.data

import app.cl0udly.Graph
import app.cl0udly.api.OcJson
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Pass-through for Pollinations, as on the phone (ToolCallFix there). GPT-OSS on it sometimes starts a
 * streamed tool call without an id ("Expected 'id' to be a string") or without a name ("Expected
 * 'function.name' to be a string"); the AI SDK inside OpenCode then fails the answer, and the broken
 * call stays in the chat, so every later message fails too. The proxy fills in the id, holds a call
 * back until its name comes, and leaves it out if it never does.
 * OpenCode reaches it on this computer only: http://127.0.0.1:<server port + 2>/llm/<provider>/…
 */
object ToolCallFix {
    val upstream = mapOf(FreeModels.POLLINATIONS_ID to "https://text.pollinations.ai/openai")

    private var server: HttpServer? = null

    fun port() = (Graph.prefs.serverPort.toIntOrNull() ?: 4196) + 2
    fun baseUrl(provider: String) = "http://127.0.0.1:${port()}/llm/$provider"

    fun start() {
        if (server != null) return
        server = runCatching {
            HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), port()), 8).apply {
                executor = Executors.newCachedThreadPool()
                createContext("/llm/") { ex -> runCatching { forward(ex) }.onFailure { runCatching { ex.sendResponseHeaders(502, -1) } }; ex.close() }
                start()
            }
        }.getOrNull()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(5, TimeUnit.MINUTES).build()

    // headers the provider needs; hop-by-hop ones (host, length, connection) are not copied
    private val passHeaders = listOf("authorization", "content-type", "accept", "user-agent")

    private fun forward(ex: HttpExchange) {
        val path = ex.requestURI.rawPath.removePrefix("/llm/")
        val base = upstream[path.substringBefore('/')] ?: return ex.sendResponseHeaders(404, -1)
        val query = ex.requestURI.rawQuery?.let { "?$it" }.orEmpty()
        val body = ex.requestBody.readBytes()
        val req = Request.Builder().url(base + "/" + path.substringAfter('/', "") + query).apply {
            passHeaders.forEach { h -> ex.requestHeaders.getFirst(h)?.let { header(h, it) } }
            if (ex.requestMethod == "GET") get()
            else method(ex.requestMethod, body.toRequestBody(ex.requestHeaders.getFirst("content-type")?.toMediaTypeOrNull()))
        }.build()
        client.newCall(req).execute().use { r ->
            val type = r.header("content-type") ?: "application/json"
            ex.responseHeaders.add("Content-Type", type)
            ex.responseHeaders.add("Cache-Control", "no-store")
            // no length: chunked, so the stream goes out line by line
            ex.sendResponseHeaders(r.code, 0)
            val out = ex.responseBody
            val src = r.body.source()
            if (!type.contains("event-stream")) { out.write(src.readByteArray()); out.close(); return }
            val stream = Stream()
            while (true) {
                val line = src.readUtf8Line() ?: break
                out.write((fixLine(line, stream) + "\n").toByteArray())
                if (line.isEmpty()) out.flush()
            }
            out.close()
        }
    }

    /** What one streamed answer has passed on so far. */
    class Stream {
        internal val named = HashSet<String>()
        /** Calls that started without a name: their id and the arguments that came meanwhile. */
        internal val waiting = HashMap<String, Pair<String?, String>>()
    }

    /**
     * One SSE line. The first chunk of each tool call goes out with an id, a type and a name: if it
     * came without the name, it waits for it. A finish of "tool_calls" with no call passed on becomes "stop".
     */
    fun fixLine(line: String, s: Stream): String {
        if (!line.startsWith("data:") || !(line.contains("tool_calls") || line.contains("finish_reason"))) return line
        val json = runCatching { OcJson.parseToJsonElement(line.removePrefix("data:").trim()).jsonObject }.getOrNull() ?: return line
        val choices = json["choices"] as? JsonArray ?: return line
        var changed = false
        val fixed = JsonArray(choices.map { ch ->
            var c = ch as? JsonObject ?: return@map ch
            val ci = (c["index"] as? JsonPrimitive)?.content ?: "0"
            val delta = c["delta"] as? JsonObject
            val calls = delta?.get("tool_calls") as? JsonArray
            if (delta != null && calls != null) {
                val kept = calls.mapIndexedNotNull { n, tc ->
                    val t = tc as? JsonObject ?: return@mapIndexedNotNull tc
                    val key = ci + ":" + ((t["index"] as? JsonPrimitive)?.content ?: n.toString())
                    if (key in s.named) return@mapIndexedNotNull tc
                    val fn = t["function"] as? JsonObject
                    val name = (fn?.get("name") as? JsonPrimitive)?.takeIf { it.isString && it.content.isNotBlank() }
                    val id = (t["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                    val args = (fn?.get("arguments") as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
                    val held = s.waiting.remove(key)
                    if (name == null) {
                        s.waiting[key] = (held?.first ?: id) to (held?.second.orEmpty() + args)
                        changed = true
                        return@mapIndexedNotNull null
                    }
                    s.named.add(key)
                    val m = t.toMutableMap()
                    if (id == null) { m["id"] = JsonPrimitive(held?.first ?: ("call_" + key.replace(':', '_') + "_" + System.nanoTime().toString(36))); changed = true }
                    if ((m["type"] as? JsonPrimitive)?.content != "function") { m["type"] = JsonPrimitive("function"); changed = true }
                    if (held != null && held.second.isNotEmpty()) {
                        m["function"] = JsonObject(fn!! + ("arguments" to JsonPrimitive(held.second + args))); changed = true
                    }
                    JsonObject(m)
                }
                c = JsonObject(c + ("delta" to JsonObject(if (kept.isEmpty()) delta - "tool_calls" else delta + ("tool_calls" to JsonArray(kept)))))
            }
            if ((c["finish_reason"] as? JsonPrimitive)?.content == "tool_calls" && s.named.none { it.startsWith("$ci:") }) {
                c = JsonObject(c + ("finish_reason" to JsonPrimitive("stop"))); changed = true
            }
            c
        })
        return if (changed) "data: " + JsonObject(json + ("choices" to fixed)) else line
    }
}
