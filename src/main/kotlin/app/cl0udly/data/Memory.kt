package app.cl0udly.data

import app.cl0udly.Graph
import java.io.File

/**
 * Memory, as on the phone: plain markdown "shelves" in [Paths.memory] with a MEMORY.md index.
 * Its rules and current contents go with every request as the system text (not AGENTS.md),
 * so memory can be off for a single chat. Off everywhere: nothing is sent and nothing is saved.
 */
object Memory {
    val dir: File get() = Paths.memory

    private fun rules(path: String, mode: String) = """
# Memory

You have a persistent memory: plain markdown files in $path. It survives between conversations.

- Its current contents are below under "What you remember". Do not list or read $path just to recall something; answer from what is below. Open a shelf file only when it says it was too long to include.
- Use what you remember quietly; do not announce that you looked it up.
""".trim() + "\n" + (if (mode != "off") """
- Remembering means writing a file with your file tools in the same turn. Never just say "I will remember".
""".trim() + "\n" + (if (mode == "ask") """
- When the user asks you to remember something, save it yourself.
- When the user mentions something lasting about themselves without asking (preferences, people, projects, goals, routines, decisions), do not save it yourself: call the remember tool with the fact (one short line in the user's language) and a shelf (a topic file name without .md, like about-me, work, people). The user sees a "Remember this?" card and decides. At most two per reply, never something already remembered, never one-off details or sensitive things.
""".trim() else """
- When the user tells you something lasting (preferences, people, projects, goals, routines, decisions) or asks you to remember something, save it. Skip one-off details and sensitive things unless asked.
""".trim()) + "\n" + """
- One file per topic (about-me.md, work.md, projects.md, people.md, ideas.md…); each starts with a one-line description. MEMORY.md is a short index: "- [Title](file.md) — what is inside". Merge duplicates, fix outdated facts.
- When asked to forget something, delete it. After changing memory, say in one short line what you saved or removed.
""".trim() else "- Save to memory ONLY when the user explicitly asks you to remember something. Otherwise only read it.")

    private const val INLINE_LIMIT = 24_000
    private const val SHELF_LIMIT = 4_000
    private const val MAP_LIMIT = 12_000

    fun shelves(): List<File> = dir.listFiles { f -> f.isFile && f.extension == "md" && f.name != "MEMORY.md" }?.sortedBy { it.name }.orEmpty()

    /** Rules plus what is remembered, for the request's system text; null when memory is off for [sessionId]. */
    fun systemBlock(sessionId: String?): String? {
        val p = Graph.prefs
        if (!p.memoryOn(sessionId)) return null
        ensure()
        val index = runCatching { File(dir, "MEMORY.md").readText().trim() }.getOrDefault("")
        val files = shelves().map { it.name to runCatching { it.readText().trim() }.getOrDefault("") }.filter { it.second.isNotEmpty() }
        val whole = files.sumOf { it.second.length } <= INLINE_LIMIT
        // too big to send every time: the map (from the graph) instead, the agent opens what it needs
        if (files.sumOf { it.second.length } > MAP_LIMIT) return rules(dir.absolutePath, p.memoryMode) + "\n\n" + MemoryIO.map(dir, dir.absolutePath)
        val snap = buildString {
            append("## What you remember\n\n### MEMORY.md\n").append(index.ifEmpty { "(empty)" }).append("\n")
            if (files.isEmpty()) append("\nNo shelves yet.\n")
            files.forEach { (name, text) ->
                append("\n### ").append(name).append("\n")
                if (whole || text.length <= SHELF_LIMIT) append(text).append("\n")
                else append(text.take(SHELF_LIMIT)).append("\n… (too long: open ${File(dir, name).path} for the rest)\n")
            }
        }
        return rules(dir.absolutePath, p.memoryMode) + "\n\n" + snap.trim()
    }

    fun ensure() {
        runCatching {
            dir.mkdirs()
            val index = File(dir, "MEMORY.md")
            if (!index.exists()) index.writeText("# Memory index\n\nNothing saved yet.\n")
        }
    }

    /** Facts on a shelf: its "- …" lines. */
    fun facts(f: File): List<String> = runCatching { f.readLines() }.getOrDefault(emptyList())
        .filter { it.trimStart().startsWith("- ") }.map { it.trimStart().removePrefix("- ").trim() }

    fun removeFact(f: File, fact: String) = runCatching {
        val lines = f.readLines().toMutableList()
        val i = lines.indexOfFirst { it.trimStart().startsWith("- ") && it.trimStart().removePrefix("- ").trim() == fact }
        if (i >= 0) { lines.removeAt(i); f.writeText(lines.joinToString("\n") + "\n") }
    }

    fun addFact(shelf: String, fact: String) = runCatching {
        ensure()
        val name = shelf.lowercase().replace(Regex("[^a-z0-9а-яё]+"), "-").trim('-').ifBlank { "notes" }
        val f = File(dir, "$name.md")
        val title = name.replace('-', ' ').replaceFirstChar { it.uppercase() }
        val old = if (f.exists()) f.readText().trimEnd() else "# $title"
        f.writeText(old + "\n- " + fact.trim().replace("\n", " ") + "\n")
        val idx = File(dir, "MEMORY.md")
        val t = idx.readText()
        if (!t.contains("(${f.name})")) idx.writeText(t.replace("Nothing saved yet.\n", "").trimEnd() + "\n- [$title](${f.name})\n")
    }

    fun forgetAll() = runCatching { dir.listFiles()?.forEach { it.deleteRecursively() }; ensure() }
}
