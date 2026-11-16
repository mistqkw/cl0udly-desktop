package app.cl0udly.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.cl0udly.api.EventHub
import app.cl0udly.api.ModelInfo
import app.cl0udly.api.OcJson
import app.cl0udly.api.OpenCodeApi
import app.cl0udly.api.PermissionReq
import app.cl0udly.api.ProviderInfo
import app.cl0udly.api.QuestionReq
import app.cl0udly.api.Session
import app.cl0udly.api.obj
import app.cl0udly.api.parsePermission
import app.cl0udly.api.parseQuestion
import app.cl0udly.api.str
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonObject

/** A model the user can pick: provider, model and its name for the picker. */
data class Pick(val provider: ProviderInfo, val model: ModelInfo) {
    val key get() = "${provider.id}/${model.id}"
    val label get() = model.name.ifBlank { model.id }
}

/**
 * Everything the screens read: sessions, what each one is doing, open chats, requests from the agent
 * and models. All of it is Compose state changed on the UI thread (events are collected there).
 */
class Hub(private val api: OpenCodeApi, private val events: EventHub, private val prefs: Prefs, private val scope: CoroutineScope) {
    private val all = mutableStateMapOf<String, Session>()
    /** busy | retry per session; absent means idle. */
    val statuses = mutableStateMapOf<String, String>()
    val retryNote = mutableStateMapOf<String, String>()
    val permissions = mutableStateListOf<PermissionReq>()
    val questions = mutableStateListOf<QuestionReq>()
    private val chats = HashMap<String, Chat>()
    val toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** A reply finished while the window was not focused or another chat was open: the mascot cheers. */
    val finished = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** Chats whose task finished while you weren't looking at them: a bright blue dot until you open them. */
    val doneUnseen = androidx.compose.runtime.mutableStateListOf<String>()

    var picks by mutableStateOf<List<Pick>>(emptyList())
        private set
    var agents by mutableStateOf<List<String>>(listOf("build", "plan"))
        private set
    var loaded by mutableStateOf(false)
        private set
    var commands by mutableStateOf<List<app.cl0udly.api.NamedItem>>(emptyList())
        private set
    var skills by mutableStateOf<List<app.cl0udly.api.NamedItem>>(emptyList())
        private set

    val sessions: List<Session>
        get() = all.values.filter { it.parentID == null && it.time.archived == null }.sortedByDescending { it.time.updated }

    val archived: List<Session>
        get() = all.values.filter { it.parentID == null && it.time.archived != null }.sortedByDescending { it.time.updated }

    fun session(id: String) = all[id]

    /** Helper sessions the agent started from [id] (OpenCode's task tool), oldest first. */
    fun children(id: String): List<Session> = all.values.filter { it.parentID == id }.sortedBy { it.time.created }

    /** The top session a helper belongs to (itself when it is one). */
    fun rootOf(id: String): String { var cur = id; repeat(8) { cur = all[cur]?.parentID ?: return cur }; return cur }
    fun isChat(s: Session) = s.directory == CHAT_DIR

    fun start() {
        scope.launch { events.events.collect(::handle) }
        // a reply finished while you were elsewhere: a desktop notification
        scope.launch {
            finished.collect { id ->
                val open = (app.cl0udly.ui.Nav.screen as? app.cl0udly.ui.Screen.Chat)?.id == id
                if (!(open && Desktop.focused) && id !in doneUnseen) doneUnseen.add(id)
                if (prefs.notifyDone && !Desktop.focused) Desktop.notify(all[id]?.displayTitle ?: "cl0udly", "The reply is ready")
            }
        }
        // keep the computer awake while the agent works
        scope.launch {
            androidx.compose.runtime.snapshotFlow { statuses.isNotEmpty() && prefs.keepAwake }.collect { Desktop.inhibit(it) }
        }
        scope.launch { events.generation.collect { if (it > 0) reload() } }
    }

    private var routed = false

    suspend fun reload() {
        runCatching {
            // OpenCode answers per project: without a folder it only knows the plain chats (and folders
            // that aren't git repos). A git repo is a project of its own, so each one is asked by its folder.
            val dirs = listOf<String?>(null) + (prefs.projectList + runCatching { api.projects() }.getOrNull().orEmpty()
                .mapNotNull { (it as? JsonObject)?.str("worktree") }.filter { it != "/" }).distinct()
            val list = dirs.flatMap { d -> runCatching { api.listSessions(directory = d, limit = 500) }.getOrDefault(emptyList()) }
            all.clear(); list.forEach { all[it.id] = it }
            statuses.clear(); permissions.clear(); questions.clear()
            dirs.forEach { d ->
                runCatching { api.statuses(d) }.getOrNull()?.forEach { (id, o) -> o.str("type")?.takeIf { it != "idle" }?.let { statuses[id] = it } }
                runCatching { api.permissions(d) }.getOrNull()?.forEach { r -> if (permissions.none { it.id == r.id }) permissions.add(r) }
                runCatching { api.questions(d) }.getOrNull()?.forEach { q -> if (questions.none { it.id == q.id }) questions.add(q) }
            }
            chats.values.forEach { it.load() }
        }.onFailure { toasts.tryEmit(it.message ?: "Could not load sessions") }
        if (!routed) routed = runCatching { FreeModels.routeThroughFix() }.isSuccess
        loadModels()
        loaded = true
    }

    suspend fun loadModels() {
        runCatching {
            val r = api.connectedProviders()
            // only what can really answer: providers you gave a key (or whose key is in the environment),
            // and free models; not the whole catalog of every provider OpenCode knows
            val keyed = runCatching {
                (kotlinx.serialization.json.Json.parseToJsonElement(java.io.File(Paths.data, "opencode/data/opencode/auth.json").readText()) as kotlinx.serialization.json.JsonObject).keys
            }.getOrDefault(emptySet())
            picks = r.providers.flatMap { p -> p.models.values.filter { it.status != "deprecated" }.map { Pick(p, it) } }
                .filter { it.provider.id in keyed || it.provider.env.any { e -> !System.getenv(e).isNullOrBlank() } || Helpers.cost(it) == "free" }
                .sortedWith(compareBy({ it.provider.name }, { it.label }))
            if (picks.none { it.provider.id == prefs.providerId && it.model.id == prefs.modelId }) {
                val def = r.default.entries.firstOrNull()
                val fallback = picks.firstOrNull { it.provider.id == def?.key && it.model.id == def.value }
                    ?: picks.firstOrNull { it.model.id.endsWith("-free") } ?: picks.firstOrNull()
                fallback?.let { prefs.providerId = it.provider.id; prefs.modelId = it.model.id }
            }
        }
        runCatching { agents = api.agents().filter { it.mode != "subagent" && !it.hidden }.map { it.name } }
        runCatching { commands = api.commands() }
        runCatching { skills = api.skills() }
        if (picks.isNotEmpty()) Helpers.sync()
    }

    val pick: Pick? get() = picks.firstOrNull { it.provider.id == prefs.providerId && it.model.id == prefs.modelId }

    fun chat(id: String): Chat = chats.getOrPut(id) {
        Chat(id, api).also { c ->
            c.directory = all[id]?.directory
            scope.launch { c.load() }
        }
    }

    private fun handle(e: EventHub.Event) {
        val p = e.props
        when (e.type) {
            "session.created", "session.updated" -> p.obj("info")?.let {
                runCatching { OcJson.decodeFromJsonElement(Session.serializer(), it) }.getOrNull()?.let { s ->
                    all[s.id] = s; chats[s.id]?.directory = s.directory
                }
            }
            "session.deleted" -> p.obj("info")?.str("id")?.let { all.remove(it) }
            "session.status" -> {
                val id = p.str("sessionID") ?: return
                val st = p.obj("status") ?: return
                val t = st.str("type") ?: return
                val was = statuses[id]
                if (t == "idle") statuses.remove(id) else statuses[id] = t
                if (t == "retry") retryNote[id] = st.str("message") ?: "Retrying…" else retryNote.remove(id)
                if (t == "retry" && st.str("message")?.contains("Free usage", true) == true && prefs.providerId != FreeModels.POLLINATIONS_ID) fallToPollinations(id)
                if (t == "idle" && was != null) { reacted(id); finished.tryEmit(id) }
            }
            "session.idle" -> p.str("sessionID")?.let { id -> if (statuses.remove(id) != null) { reacted(id); finished.tryEmit(id); Sounds.play(Sounds.S.Done); Limits.checkSpending() } }
            "permission.asked" -> parsePermission(p).let { r ->
                // Auto mode: the agent goes on by itself, like Claude's Auto
                // a helper model is the user's call even in Auto (always-allowed ones never ask)
                if (prefs.permMode == "auto" && r.permission != "task") { scope.launch { runCatching { api.replyPermission(r.id, e.directory ?: all[r.sessionID]?.directory, "once") }.onFailure { permissions.add(r) } }; return }
                permissions.removeAll { it.id == r.id }; permissions.add(r)
                if (prefs.notifyPermission) Desktop.notify("cl0udly needs your OK", "${all[rootOf(r.sessionID)]?.displayTitle ?: "A session"} " +
                    if (r.permission == "task") "wants help from ${Helpers.label(r.patterns.firstOrNull())}" else "wants to use ${r.permission}")
            }
            "permission.replied" -> p.str("requestID")?.let { rid -> permissions.removeAll { it.id == rid } }
            "question.asked" -> parseQuestion(p).let { q -> questions.removeAll { it.id == q.id }; questions.add(q) }
            "question.replied", "question.rejected" -> p.str("requestID")?.let { rid -> questions.removeAll { it.id == rid } }
            "session.error" -> {
                val msg = p.obj("error")?.obj("data")?.str("message") ?: p.obj("error")?.str("name")
                if (msg != null && msg != "MessageAbortedError") {
                    toasts.tryEmit(msg)
                    Sounds.play(Sounds.S.Error)
                    if (prefs.notifyErrors) Desktop.notify("cl0udly ran into an error", msg)
                    app.cl0udly.ui.cloud.MascotSignals.outcome = app.cl0udly.ui.cloud.Outcome(ok = false, sessionId = p.str("sessionID"))
                }
            }
            else -> {
                val sid = p.str("sessionID") ?: p.obj("info")?.str("sessionID") ?: p.obj("part")?.str("sessionID")
                if (sid != null) chats[sid]?.onEvent(e.type, p)
            }
        }
    }

    private var switching = false

    /** A reply ended: the mascot is glad, unless that same reply just failed (an error also ends with "idle"). */
    private fun reacted(id: String) {
        val was = app.cl0udly.ui.cloud.MascotSignals.outcome
        if (!(was != null && !was.ok && was.sessionId == id && System.currentTimeMillis() - was.at < 3000))
            app.cl0udly.ui.cloud.MascotSignals.outcome = app.cl0udly.ui.cloud.Outcome(ok = true, sessionId = id)
    }

    /** Zen's free tier is capped: switch to Pollinations and send the last message again (like the phone). */
    private fun fallToPollinations(id: String) {
        if (switching) return
        switching = true
        scope.launch {
            try {
                val c = chat(id)
                val last = c.messages.lastOrNull { it.info.isUser } ?: return@launch
                val text = last.parts.filter { it.type == "text" && it.raw.str("synthetic") != "true" }.joinToString("\n") { it.text }
                if (text.isBlank()) return@launch
                runCatching { api.abort(id, all[id]?.directory) }
                FreeModels.enablePollinations()
                toasts.tryEmit("OpenCode Zen's free limit is used up — switched to GPT-OSS 20B on Pollinations")
                runCatching { api.revert(id, all[id]?.directory, last.id) }
                c.truncateFrom(last.id)
                send(id, text)
            } catch (e: Exception) {
                toasts.tryEmit(e.message ?: "Could not switch the model")
            } finally { switching = false }
        }
    }

    // ---------- actions ----------

    /** Starts a session in [directory] (chats folder or a project) and sends the first message. */
    suspend fun startAndSend(directory: String, text: String, files: List<String> = emptyList()): String? = try {
        java.io.File(directory).mkdirs()
        val s = api.createSession(directory)
        all[s.id] = s
        send(s.id, text, files)
        s.id
    } catch (e: Exception) { toasts.tryEmit(e.message ?: "Could not start a chat"); null }

    suspend fun send(id: String, text: String, files: List<String> = emptyList()) {
        Sounds.play(Sounds.S.Send)
        Helpers.pull()
        val c = chat(id)
        // "/name args": a slash command the server knows runs as a command, not as plain text
        if (text.startsWith("/") && files.isEmpty()) {
            val name = text.drop(1).substringBefore(' ')
            if (commands.any { it.name == name }) {
                c.addLocalUser(text); statuses[id] = "busy"
                runCatching { api.command(id, all[id]?.directory, name, text.drop(1).substringAfter(' ', ""), prefs.providerId, prefs.modelId, prefs.agent) }
                    .onFailure { statuses.remove(id); toasts.tryEmit(it.message ?: "Could not run /$name") }
                return
            }
            if (name == "compact") { compact(id); return }
        }
        // pictures for a model that can't see them: say so instead of failing quietly
        if (files.any { Attachments.isImage(it) } && pick?.model?.supportsImages == false) toasts.tryEmit("${pick?.label} can't see pictures: it gets the file name only")
        val fileParts = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { files.mapNotNull { Attachments.part(it) } }
        val local = c.addLocalUser(text, fileParts)
        statuses[id] = "busy"
        try {
            val parts = buildJsonArray {
                if (text.isNotBlank()) add(buildJsonObject { put("type", JsonPrimitive("text")); put("text", JsonPrimitive(text)) })
                fileParts.forEach { add(it) }
                // the date and time, as a hidden part of the message (in the system text it would break the prompt cache every minute)
                add(buildJsonObject { put("type", JsonPrimitive("text")); put("text", JsonPrimitive(nowLine())); put("synthetic", JsonPrimitive(true)) })
            }
            val system = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                Helpers.writeSettings()
                listOfNotNull(
                    prefs.userAbout.takeIf { it.isNotBlank() }?.let { "About the user (from their settings):\n$it" },
                    Memory.systemBlock(id),
                ).joinToString("\n\n").ifBlank { null }
            }
            api.promptAsync(id, all[id]?.directory, parts, prefs.providerId, prefs.modelId, prefs.agent, prefs.variant.ifBlank { null }, system)
            pick?.let { prefs.useModel(it.key) }
        } catch (e: Exception) {
            c.dropLocal(local); statuses.remove(id)
            toasts.tryEmit(e.message ?: "Could not send")
        }
    }

    fun fork(id: String) = scope.launch {
        runCatching { val s = api.forkSession(id, all[id]?.directory, null); all[s.id] = s; app.cl0udly.ui.Nav.go(app.cl0udly.ui.Screen.Chat(s.id)) }
            .onFailure { toasts.tryEmit(it.message ?: "Could not fork") }
    }

    fun archive(id: String) = scope.launch {
        runCatching { api.archiveSession(id, all[id]?.directory); all[id]?.let { all[id] = it.copy(time = it.time.copy(archived = System.currentTimeMillis())) } }
            .onFailure { toasts.tryEmit(it.message ?: "Could not archive") }
    }

    /** Claude's "Compact session": OpenCode summarizes the conversation so far and keeps going from the summary. */
    fun compact(id: String) = scope.launch {
        runCatching { api.summarize(id, all[id]?.directory, prefs.providerId, prefs.modelId) }
            .onFailure { toasts.tryEmit(it.message ?: "Could not compact") }
    }

    /** The whole transcript as Markdown, for Export. */
    fun exportMarkdown(id: String): String {
        val s = all[id]
        val sb = StringBuilder("# ${s?.displayTitle ?: id}\n\n")
        chat(id).messages.forEach { m ->
            sb.append(if (m.info.isUser) "## You\n\n" else "## cl0udly\n\n")
            m.parts.filter { it.type == "text" }.forEach { sb.append(it.text.trim()).append("\n\n") }
        }
        return sb.toString()
    }

    fun stop(id: String) = scope.launch { runCatching { api.abort(id, all[id]?.directory) } }

    fun rename(id: String, title: String) = scope.launch {
        runCatching { api.renameSession(id, all[id]?.directory, title) }.onFailure { toasts.tryEmit(it.message ?: "Could not rename") }
    }

    /** Something that can be taken back for a few seconds: the bar at the bottom shows [text] and "Undo". */
    class Undo(val text: String, val undo: () -> Unit)
    var undo by androidx.compose.runtime.mutableStateOf<Undo?>(null)

    /**
     * The chat disappears at once and "Undo" brings it back for four seconds; only then is it removed on
     * the server, so a slip of the mouse loses nothing (as on the phone).
     */
    fun delete(id: String) {
        val s = all.remove(id) ?: return
        var undone = false
        val u = Undo("Chat deleted") { undone = true; all[id] = s }
        undo = u
        scope.launch {
            kotlinx.coroutines.delay(4200)
            if (undo === u) undo = null
            if (undone) return@launch
            runCatching { api.deleteSession(id, s.directory); chats.remove(id) }
                .onFailure { all[id] = s; toasts.tryEmit(it.message ?: "Could not delete") }
        }
    }

    /** Sends your last message again in place of the failed reply. */
    fun retryLast(id: String) = scope.launch {
        val c = chat(id)
        val last = c.messages.lastOrNull { it.info.isUser } ?: return@launch
        val text = last.parts.filter { it.type == "text" && it.raw.str("synthetic") != "true" }.joinToString("\n") { it.text }
        if (text.isBlank()) return@launch
        runCatching { api.revert(id, all[id]?.directory, last.id) }
        c.truncateFrom(last.id)
        send(id, text)
    }

    fun replyPermission(r: PermissionReq, reply: String) = scope.launch {
        runCatching { api.replyPermission(r.id, all[r.sessionID]?.directory, reply); permissions.removeAll { it.id == r.id } }
            .onFailure { toasts.tryEmit(it.message ?: "Could not answer") }
    }

    fun replyQuestion(q: QuestionReq, answers: List<List<String>>?) = scope.launch {
        runCatching {
            val dir = all[q.sessionID]?.directory
            if (answers == null) api.rejectQuestion(q.id, dir) else api.replyQuestion(q.id, dir, answers)
            questions.removeAll { it.id == q.id }
        }.onFailure { toasts.tryEmit(it.message ?: "Could not answer") }
    }
}

/** "Now: Sunday, 15 November 2026, 15:42 (Europe/Warsaw)", for the agent. */
internal fun nowLine(): String {
    val z = java.time.ZonedDateTime.now()
    return "Now: " + z.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, HH:mm", java.util.Locale.ENGLISH)) + " (" + z.zone.id + ")"
}

