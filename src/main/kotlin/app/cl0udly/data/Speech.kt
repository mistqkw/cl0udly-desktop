package app.cl0udly.data

import app.cl0udly.Graph
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.io.File

/**
 * Reading a reply aloud. A live voice first: Piper (an offline neural voice, the same kind the phone's
 * best system voices are) when its program and a voice for the language are in the voice folder or on PATH.
 * Without it, what the system has: say on macOS, Windows speech, spd-say or espeak-ng on Linux.
 */
object Speech {
    private val dir get() = File(Paths.data, "voice")
    private var now: Process? = null
    /** What is being read right now, so the same button stops it. */
    var reading by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set

    /** Starts reading [text] or stops it if it's the one being read. */
    fun toggle(text: String) {
        val was = reading
        stop()
        if (was == text) return
        val p = say(text) ?: run { Graph.hub.toasts.tryEmit(missing()); return }
        now = p; reading = text
        Thread { runCatching { p.waitFor() }; if (now === p) { now = null; reading = null } }.apply { isDaemon = true }.start()
    }

    fun stop() { now?.let { it.descendants().forEach { d -> d.destroy() }; it.destroy() }; now = null; reading = null }

    private fun has(n: String) = (System.getenv("PATH") ?: "").split(File.pathSeparatorChar).any { File(it, n).canExecute() }

    private fun piper(): File? = (listOf(File(dir, "piper/piper"), File(dir, "piper")) + (System.getenv("PATH") ?: "").split(File.pathSeparatorChar).map { File(it, "piper") })
        .firstOrNull { it.isFile && it.canExecute() }

    /** A Piper voice for the language: ru_RU-… or en_US-… .onnx with its .onnx.json next to it. */
    private fun piperVoice(ru: Boolean): File? = dir.walkTopDown().maxDepth(2)
        .filter { it.name.endsWith(".onnx") && File(it.path + ".json").exists() && it.name.startsWith(if (ru) "ru_" else "en_") }
        .maxByOrNull { if (it.name.contains("high")) 2 else if (it.name.contains("medium")) 1 else 0 }

    private fun rate(voice: File): Int = Regex("\"sample_rate\"\\s*:\\s*(\\d+)").find(runCatching { File(voice.path + ".json").readText() }.getOrDefault(""))?.groupValues?.get(1)?.toIntOrNull() ?: 22050

    fun missing(): String = "To read aloud, put Piper and a voice in ${dir.path} (or install espeak-ng)"

    /** Reads [text] aloud; null when this computer has no voice. */
    fun say(text: String): Process? {
        val clean = text.replace(Regex("```[\\s\\S]*?```"), " ").replace(Regex("[`*#>_|\\[\\]]"), "").replace(Regex("\\((/|https?://)[^)]*\\)"), "").take(4000).trim()
        if (clean.isEmpty()) return null
        val ru = clean.count { it in 'а'..'я' || it in 'А'..'Я' } > clean.length / 5
        val pv = piper()?.let { p -> piperVoice(ru)?.let { p to it } }
        val player = when { has("pw-cat") -> "pw-cat --playback --rate %d --channels 1 --format s16 -"; has("aplay") -> "aplay -q -r %d -f S16_LE -t raw -c 1 -"; else -> null }
        val cmd = when {
            Os.isMac -> listOf("say", clean)
            Os.isWindows -> listOf(
                "powershell", "-NoProfile", "-Command",
                "Add-Type -AssemblyName System.Speech; (New-Object System.Speech.Synthesis.SpeechSynthesizer).Speak(\$env:CL0UDLY_SAY)",
            )
            pv != null && player != null -> listOf(
                "sh", "-c",
                "printf '%s' \"\$CL0UDLY_SAY\" | '${pv.first.path}' --model '${pv.second.path}' --output-raw 2>/dev/null | " + player.format(rate(pv.second)),
            )
            has("spd-say") -> listOf("spd-say", "-w", "-l", if (ru) "ru" else "en", clean)
            has("espeak-ng") -> listOf("espeak-ng", "-v", if (ru) "ru" else "en-us", "-s", "160", clean)
            else -> return null
        }
        return runCatching { ProcessBuilder(cmd).apply { environment()["CL0UDLY_SAY"] = clean }.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start() }.getOrNull()
    }
}
