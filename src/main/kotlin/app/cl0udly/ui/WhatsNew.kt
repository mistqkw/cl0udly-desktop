package app.cl0udly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cl0udly.Graph
import app.cl0udly.ui.kit.Txt
import app.cl0udly.ui.kit.hoverTap
import app.cl0udly.ui.theme.Oc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** This build's version; the "what's new" window and the update check go by it. */
const val APP_VERSION = "0.0.1 beta-2"

/** What each version brought, said simply, like on the phone. Add a line per feature when it lands. */
private val NOTES: Map<String, List<String>> = mapOf(
    "0.0.1 beta-2" to listOf(
        "New look: a night sky with pixel clouds, and a new launch screen",
        "Search (Ctrl+K) finds words inside messages and in memory",
        "A speaker under each reply reads it aloud",
        "A card says when the server or the internet is gone; a stopped server restarts on a click",
        "The cloud reacts when a reply is done or fails",
        "The sidebar rows can be reordered in Settings → Appearance",
        "Code in replies is highlighted, with line numbers; a deleted chat can be brought back",
        "Fixed: the \"OK\" error with GitHub Copilot, broken replies from GPT-OSS, the chat jumping down when you scroll up",
    ),
    "0.0.1 beta-1" to listOf(
        "cl0udly on the computer: chats, Code with projects, a terminal, a browser and files side by side",
        "Chats and memory move between the phone and the computer by themselves when you're home",
        "Remember this? cards: the agent offers what it heard about you, you decide",
        "Sub-agents work as a team: add them in the model picker, they know the whole conversation",
        "Memory as a graph in the sidebar, plus import and export (a memory zip from Claude works too)",
        "OpenCode updates itself, and you can go back to the previous version in Settings → Server",
        "Russian and English: pick it in Settings → General",
    ),
)

/**
 * After an update: a small window "Updated to v…" with what's new, once per version. While a newer
 * release waits on GitHub, a bar at the bottom says so; a click opens its page.
 */
@Composable
fun WhatsNewHost() {
    val p = Graph.prefs
    val c = Oc.c
    var newer by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(Unit) { newer = withContext(Dispatchers.IO) { latestRelease() } }
    newer?.let { (tag, url) ->
        Box(Modifier.fillMaxWidth().padding(bottom = 92.dp), contentAlignment = Alignment.BottomCenter) {
            Row(
                Modifier.clip(CircleShape).background(c.surfaceHigh).border(1.dp, c.border, CircleShape).hoverTap(CircleShape) { Nav.openUrl(url) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(c.accent))
                Spacer(Modifier.width(10.dp))
                Txt("$tag is out", size = 14.sp)
                Spacer(Modifier.width(10.dp))
                Txt("Update", size = 14.sp, color = c.accent, weight = FontWeight.Medium)
            }
        }
    }
    if (p.ocNotice.isNotBlank()) {
        LaunchedEffect(p.ocNotice) { kotlinx.coroutines.delay(8000); p.ocNotice = "" }
        Box(Modifier.fillMaxWidth().padding(bottom = 140.dp), contentAlignment = Alignment.BottomCenter) {
            Row(
                Modifier.clip(CircleShape).background(c.surfaceHigh).border(1.dp, c.border, CircleShape).hoverTap(CircleShape) { p.ocNotice = "" }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(c.ok))
                Spacer(Modifier.width(10.dp))
                Txt("OpenCode updated to ${p.ocNotice}", size = 14.sp)
            }
        }
    }
    if (p.seenVersion == APP_VERSION) return
    val lines = NOTES[APP_VERSION]
    if (lines.isNullOrEmpty()) { LaunchedEffect(Unit) { p.seenVersion = APP_VERSION }; return }
    androidx.compose.ui.window.Dialog(onDismissRequest = { p.seenVersion = APP_VERSION }) {
        Column(Modifier.width(460.dp).clip(Oc.round(16.dp)).background(c.surface).border(1.dp, c.border, Oc.round(16.dp)).padding(24.dp)) {
            Txt("Updated to v$APP_VERSION", size = 20.sp, weight = FontWeight.SemiBold, family = Oc.heading)
            Txt("What's new", size = 14.sp, color = c.textDim, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
            lines.forEach { l ->
                Row(Modifier.padding(vertical = 5.dp)) {
                    Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(c.accent))
                    Spacer(Modifier.width(12.dp))
                    Txt(l, size = 14.sp)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Button("Nice") { p.seenVersion = APP_VERSION } }
        }
    }
}

/** The newest release on GitHub when it's newer than this build: (tag, page), or null (also when GitHub can't be reached). */
private fun latestRelease(): Pair<String, String>? = runCatching {
    val req = okhttp3.Request.Builder().url("https://api.github.com/repos/mistqkw/cl0udly-desktop/releases?per_page=5")
        .header("Accept", "application/vnd.github+json").header("User-Agent", "cl0udly").build()
    val body = Graph.api.http.newCall(req).execute().use { if (!it.isSuccessful) return null; it.body.string() }
    val arr = kotlinx.serialization.json.Json.parseToJsonElement(body) as kotlinx.serialization.json.JsonArray
    arr.mapNotNull { e ->
        val o = e as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
        val tag = (o["tag_name"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return@mapNotNull null
        val url = (o["html_url"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return@mapNotNull null
        (tag to url).takeIf { newer(tag, APP_VERSION) }
    }.firstOrNull()
}.getOrNull()

private fun newer(a: String, b: String): Boolean {
    fun key(v: String): List<Int> {
        val m = Regex("""(\d+)\.(\d+)(?:\.(\d+))?(?:[\s._-]*(alpha|beta|rc)[\s._-]*(\d+)?)?""", RegexOption.IGNORE_CASE).find(v) ?: return listOf(0, 0, 0, 0, 0)
        val stage = when (m.groupValues[4].lowercase()) { "alpha" -> 0; "beta" -> 1; "rc" -> 2; else -> 3 }
        return listOf(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toIntOrNull() ?: 0, stage, m.groupValues[5].toIntOrNull() ?: 0)
    }
    val ka = key(a); val kb = key(b)
    for (i in ka.indices) if (ka[i] != kb[i]) return ka[i] > kb[i]
    return false
}
