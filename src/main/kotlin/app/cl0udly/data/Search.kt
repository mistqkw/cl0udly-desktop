package app.cl0udly.data

import app.cl0udly.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.sql.DriverManager

/**
 * Search through chat text and memory, as on the phone. OpenCode keeps conversations in SQLite
 * (data/opencode/opencode.db in cl0udly's own XDG folders); it's opened read-only while the server runs.
 * Connected to another computer's server there's no database here, so only memory is searched.
 */
object Search {
    class Hit(val sessionId: String, val title: String, val snippet: String, val user: Boolean, val time: Long)
    class MemoryHit(val shelf: String, val line: String)

    private val db = File(Paths.data, "opencode/data/opencode/opencode.db")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun chats(q: String, limit: Int = 40): List<Hit> = withContext(Dispatchers.IO) {
        if (q.length < 2 || Graph.prefs.remoteUrl.isNotBlank() || !db.exists()) return@withContext emptyList()
        runCatching {
            Class.forName("org.sqlite.JDBC")
            DriverManager.getConnection("jdbc:sqlite:file:${db.absolutePath}?mode=ro").use { con ->
                // only message text and only main chats (no team helpers); the words are matched here, not by
                // SQLite's LIKE, which ignores case only for Latin letters ("привет" didn't find "Привет")
                con.prepareStatement(
                    """SELECT p.session_id, s.title, p.data, m.data, p.time_created FROM part p
                       JOIN session s ON s.id = p.session_id JOIN message m ON m.id = p.message_id
                       WHERE s.parent_id IS NULL AND p.data LIKE '%"type":"text"%'
                       ORDER BY p.time_created DESC LIMIT 6000""",
                ).use { st ->
                    st.executeQuery().use { r ->
                        val seen = HashMap<String, Int>()
                        val out = ArrayList<Hit>()
                        while (r.next() && out.size < limit) {
                            val sid = r.getString(1)
                            if ((seen[sid] ?: 0) >= 3) continue          // at most three matches per chat
                            val text = runCatching { (json.parseToJsonElement(r.getString(3)) as JsonObject)["text"]?.jsonPrimitive?.content }.getOrNull() ?: continue
                            val at = text.indexOf(q, ignoreCase = true)
                            if (at < 0) continue
                            val role = runCatching { (json.parseToJsonElement(r.getString(4)) as JsonObject)["role"]?.jsonPrimitive?.content }.getOrNull()
                            seen[sid] = (seen[sid] ?: 0) + 1
                            out += Hit(sid, r.getString(2).orEmpty(), snippet(text, at, q.length), role == "user", r.getLong(5))
                        }
                        out
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun memory(q: String): List<MemoryHit> {
        if (q.length < 2) return emptyList()
        return runCatching {
            Memory.shelves().flatMap { f -> Memory.facts(f).filter { it.contains(q, true) && !it.startsWith("#") }.map { MemoryHit(f.nameWithoutExtension, it.trim().removePrefix("- ")) } }.take(20)
        }.getOrDefault(emptyList())
    }

    /** A piece of text around the match, on one line. */
    private fun snippet(text: String, at: Int, len: Int): String {
        val from = (at - 50).coerceAtLeast(0)
        val to = (at + len + 90).coerceAtMost(text.length)
        return (if (from > 0) "…" else "") + text.substring(from, to).replace(Regex("\\s+"), " ").trim() + if (to < text.length) "…" else ""
    }
}
