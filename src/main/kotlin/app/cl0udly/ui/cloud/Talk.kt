package app.cl0udly.ui.cloud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cl0udly.Graph
import app.cl0udly.data.CHAT_DIR
import app.cl0udly.data.Voice
import app.cl0udly.ui.Button
import app.cl0udly.ui.kit.Txt
import app.cl0udly.ui.kit.hoverTap
import app.cl0udly.ui.theme.Oc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Whether the voice conversation with the cloud is open. */
object TalkUi { var open by mutableStateOf(false) }

/**
 * A voice conversation with the cloud, like the phone's voice mode: it listens (Whisper, on this
 * computer), stops by itself when you go quiet, sends it to a chat, and reads the answer aloud with
 * the system's speech (say on macOS, Windows speech, spd-say or espeak-ng on Linux; without one the answer is only shown). Then it listens again.
 * Click the cloud to interrupt it or to stop listening early.
 */
@Composable
fun TalkHost() {
    if (!TalkUi.open) return
    val c = Oc.c
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf("listening") }   // listening, thinking, speaking, idle
    var caption by remember { mutableStateOf("") }
    var session by remember { mutableStateOf<String?>(null) }
    var speech by remember { mutableStateOf<Process?>(null) }

    fun close() { Voice.cancel(); speech?.destroy(); TalkUi.open = false }

    fun ask(text: String) {
        if (text.isBlank()) { phase = "listening"; return }
        phase = "thinking"; caption = text
        scope.launch {
            val hub = Graph.hub
            // the first phrase starts the chat, the next ones go into it
            val old = session
            val id = old ?: hub.startAndSend(CHAT_DIR, text)?.also { session = it } ?: run { phase = "idle"; return@launch }
            val chat = hub.chat(id)
            if (old != null) hub.send(id, text)
            withTimeoutOrNull(15_000) { snapshotFlow { hub.statuses[id] }.first { it != null } }
            withTimeoutOrNull(10 * 60_000) { snapshotFlow { hub.statuses[id] }.first { it == null } }
            delay(300)
            val reply = chat.messages.lastOrNull { !it.info.isUser }
            val answer = reply?.parts?.filter { it.type == "text" }?.joinToString("\n") { it.text }.orEmpty().ifBlank { reply?.info?.errorMessage.orEmpty() }
            caption = answer
            val p = withContext(Dispatchers.IO) { app.cl0udly.data.Speech.say(answer) }
            if (p == null) { phase = "idle"; return@launch }
            speech = p; phase = "speaking"
            withContext(Dispatchers.IO) { p.waitFor() }
            if (phase == "speaking") phase = "listening"
        }
    }

    // listening: record until there was a voice and then ~1 s of quiet
    LaunchedEffect(phase) {
        if (phase != "listening") return@LaunchedEffect
        Voice.start { caption = it; phase = "idle" }
        var heard = false; var quiet = 0
        while (phase == "listening" && Voice.state == Voice.State.Listening) {
            delay(100)
            val lv = Voice.levels.lastOrNull() ?: 0f
            if (lv > 0.35f) { heard = true; quiet = 0 } else if (heard && lv < 0.08f) quiet++
            if (heard && quiet >= 11) { Voice.stop({ ask(it) }, { caption = it; phase = "idle" }); break }
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = ::close) {
        Column(
            Modifier.width(460.dp).clip(Oc.round(20.dp)).background(c.surface).border(1.dp, c.border, Oc.round(20.dp)).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(220.dp, 150.dp).hoverTap(Oc.round(16.dp)) {
                when (phase) {
                    "speaking" -> { speech?.destroy(); phase = "listening" }
                    "listening" -> Voice.stop({ ask(it) }, { caption = it; phase = "idle" })
                    "idle" -> phase = "listening"
                }
            }, contentAlignment = Alignment.Center) {
                PixelCloud(Modifier.size(170.dp, 118.dp), thinking = phase == "thinking", interactive = false, watching = phase == "listening", cue = phase)
            }
            Spacer(Modifier.height(14.dp))
            Txt(
                when (phase) {
                    "listening" -> if (Voice.state == Voice.State.Transcribing) "Got it…" else "Listening…"
                    "thinking" -> "Thinking…"
                    "speaking" -> "Click the cloud to interrupt"
                    else -> "Click the cloud to talk"
                },
                size = 20.sp, family = Oc.heading,
            )
            Spacer(Modifier.height(10.dp))
            Box(Modifier.heightIn(min = 60.dp, max = 140.dp)) {
                Txt(caption.takeLast(400), size = 14.sp, color = c.textDim, align = TextAlign.Center, raw = true)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                session?.let { id -> Button("Open the chat", primary = false) { close(); app.cl0udly.ui.Nav.go(app.cl0udly.ui.Screen.Chat(id)) } }
                Button("Done") { close() }
            }
        }
    }
}
