package app.cl0udly.data

import androidx.compose.runtime.getValue
import app.cl0udly.Graph
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Dictation, like Claude's: the microphone is recorded with PipeWire (pw-record, or parecord/arecord),
 * and when you stop, whisper.cpp turns it into text on this computer. Whisper puts the commas and
 * full stops itself and keeps English words in Latin letters inside Russian speech (the prompt below
 * shows it how). Nothing leaves the computer.
 */
object Voice {
    enum class State { Idle, Listening, Transcribing }

    var state by mutableStateOf(State.Idle)
        private set
    /** The last levels of the voice, 0..1, newest last: the wave under the field. */
    val levels = mutableStateListOf<Float>().apply { repeat(BARS) { add(0f) } }

    const val BARS = 18
    private const val RATE = 16000
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var rec: Process? = null
    private var pcm = ByteArrayOutputStream()

    private val dir get() = File(Paths.data, "voice")

    /** whisper-cli: next to the model (the Vulkan build, on the video card), on PATH, or built in ~/toolchain. */
    private fun engine(): File? {
        val home = System.getProperty("user.home")
        return (listOf(File(dir, "whisper-cli"), File("$home/toolchain/whisper.cpp/build-vk/bin/whisper-cli"), File("$home/toolchain/whisper.cpp/build/bin/whisper-cli")) +
            (System.getenv("PATH") ?: "").split(':').map { File(it, "whisper-cli") })
            .firstOrNull { it.canExecute() }
    }

    /** The biggest model wins: turbo understands mixed Russian and English best. */
    private fun model(): File? = dir.listFiles { f -> f.name.startsWith("ggml-") && f.name.endsWith(".bin") }
        ?.maxByOrNull { it.length() }

    /** What's missing for dictation, or null when it's ready. */
    fun missing(): String? = when {
        engine() == null -> "Voice input needs whisper.cpp: put whisper-cli in ${dir.path} or on PATH"
        model() == null -> "Voice input needs a Whisper model: put ggml-large-v3-turbo-q5_0.bin in ${dir.path}"
        recorder() == null -> "Voice input needs pw-record, parecord or arecord"
        else -> null
    }

    private fun onPath(name: String) = (System.getenv("PATH") ?: "").split(':').any { File(it, name).canExecute() }

    private fun recorder(): List<String>? = when {
        onPath("pw-record") -> listOf("pw-record", "--raw", "--rate", "$RATE", "--channels", "1", "--format", "s16", "-")
        onPath("parecord") -> listOf("parecord", "--raw", "--rate=$RATE", "--channels=1", "--format=s16le")
        onPath("arecord") -> listOf("arecord", "-q", "-t", "raw", "-r", "$RATE", "-c", "1", "-f", "S16_LE")
        else -> null
    }

    fun start(onError: (String) -> Unit) {
        if (state != State.Idle) return
        missing()?.let { onError(it); return }
        val p = runCatching { ProcessBuilder(recorder()!!).redirectError(ProcessBuilder.Redirect.DISCARD).start() }
            .getOrElse { onError("Couldn't open the microphone: ${it.message}"); return }
        rec = p; pcm = ByteArrayOutputStream()
        for (i in levels.indices) levels[i] = 0f
        var floor = -60.0
        state = State.Listening
        scope.launch {
            val buf = ByteArray(RATE / 1000 * 2 * 60) // 60 ms
            val input = p.inputStream
            while (true) {
                val n = runCatching { input.readNBytes(buf, 0, buf.size) }.getOrDefault(-1)
                if (n <= 0) break
                synchronized(this@Voice) { pcm.write(buf, 0, n) }
                val sh = ByteBuffer.wrap(buf, 0, n).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                var sum = 0.0
                val cnt = sh.remaining()
                while (sh.hasRemaining()) { val v = sh.get() / 32768.0; sum += v * v }
                // above the room's own hum: a quiet room shows dots, talking near the mic shows bars
                val db = 20 * kotlin.math.log10(sqrt(sum / cnt.coerceAtLeast(1)).coerceAtLeast(1e-5))
                floor = minOf(db, floor + 0.05).coerceAtLeast(-70.0)
                val lvl = ((db - floor - 8) / 30).toFloat().coerceIn(0f, 1f)
                withContext(Dispatchers.Main) { if (state == State.Listening) { levels.removeAt(0); levels.add(lvl) } }
            }
        }
    }

    /** Stops listening and hands back the text (empty when nothing was said). */
    fun stop(onText: (String) -> Unit, onError: (String) -> Unit = {}) {
        if (state != State.Listening) return
        val p = rec ?: return
        rec = null
        state = State.Transcribing
        scope.launch {
            p.destroy(); runCatching { p.waitFor(1, java.util.concurrent.TimeUnit.SECONDS) }; p.destroyForcibly()
            val bytes = synchronized(this@Voice) { pcm.toByteArray() }
            // the last recording stays next to the model (one file, overwritten): to look into what went wrong
            runCatching { File(dir, "last.wav").writeBytes(wavHeader(bytes.size) + bytes) }
            val result = runCatching { nearVoice(bytes)?.let { transcribe(it) } ?: "" }
            withContext(Dispatchers.Main) {
                state = State.Idle
                result.onSuccess(onText).onFailure { onError(it.message ?: "Couldn't recognize the speech") }
            }
        }
    }

    fun cancel() {
        rec?.destroyForcibly(); rec = null
        if (state == State.Listening) state = State.Idle
    }

    /**
     * Keeps only the voice near the microphone. Someone talking in the next room is quieter by 20 dB
     * or more, so: pieces of sound that never get close to the speaker's own loud syllables are cut
     * out, and what's left is glued with short pauses. Null when nobody spoke near the mic.
     */
    internal fun nearVoice(raw: ByteArray): ByteArray? {
        val f = RATE / 50 // 20 ms
        val s = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val n = s.remaining() / f
        if (n < 15) return null
        val db = DoubleArray(n) { i ->
            var sum = 0.0
            for (k in 0 until f) { val v = s.get(i * f + k) / 32768.0; sum += v * v }
            20 * kotlin.math.log10(sqrt(sum / f).coerceAtLeast(1e-5))
        }
        val sorted = db.sorted()
        val floor = sorted[(n * 0.2).toInt()]
        val peak = sorted[minOf(n - 1, (n * 0.95).toInt())]
        if (peak < -42) return null
        val gate = maxOf(floor + 10, peak - 20, -50.0)
        // a little around every loud moment, so the starts and ends of words aren't clipped
        val pad = 10
        val keep = BooleanArray(n) { i -> (maxOf(0, i - pad)..minOf(n - 1, i + pad)).any { db[it] >= gate } }
        var i = 0
        while (i < n) {
            if (!keep[i]) { i++; continue }
            var j = i
            while (j < n && keep[j]) j++
            if ((i until j).maxOf { db[it] } < peak - 10) for (t in i until j) keep[t] = false
            i = j
        }
        if (keep.count { it } * f < RATE * 3 / 10) return null
        val out = ByteArrayOutputStream()
        val gap = ByteArray(RATE * 2 * 3 / 10)
        var prev = false
        for (k in 0 until n) {
            if (keep[k]) {
                if (!prev && out.size() > 0) out.write(gap)
                out.write(raw, k * f * 2, f * 2)
            }
            prev = keep[k]
        }
        return out.toByteArray()
    }

    private fun transcribe(raw: ByteArray): String {
        val wav = File.createTempFile("cl0udly-voice", ".wav")
        try {
            wav.writeBytes(wavHeader(raw.size) + raw)
            val cmd = listOf(
                engine()!!.path, "-m", model()!!.path, "-f", wav.path,
                "-l", Graph.prefs.voiceLang.ifBlank { "ru" },
                // only as much of Whisper's 30-second window as was said: a short phrase is several times faster
                "-ac", "${(raw.size / 2 / (RATE / 50) + 64).coerceIn(256, 1500)}",
                "-t", "${Runtime.getRuntime().availableProcessors().coerceAtMost(4)}",
                // no carried-over text between windows and no "(music)" tokens: that's what makes Whisper loop
                "-mc", "0", "-sns",
                "-nt", "-np", "--prompt", PROMPT,
            )
            // a ceiling on memory: the computer has no swap
            val full = if (onPath("systemd-run")) listOf("systemd-run", "--user", "--scope", "-q", "-p", "MemoryMax=2500M", "nice", "-n", "5") + cmd else cmd
            val p = ProcessBuilder(full).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            val out = p.inputStream.bufferedReader().readText()
            if (p.waitFor() != 0) throw IllegalStateException("whisper-cli failed (${p.exitValue()})")
            return clean(out)
        } finally { wav.delete() }
    }

    /** Whisper's leftovers: line breaks between pieces, and the made-up lines it says over silence. */
    internal fun clean(out: String): String {
        var t = out.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
            .replace(Regex("\\[(BLANK_AUDIO|Music|музыка|MUSIC)]|\\((music|музыка)\\)", RegexOption.IGNORE_CASE), "")
        // "э", "эм", "мм", "uh", "um" between words are thinking out loud, not words
        t = t.replace(Regex("(?<![\\p{L}])(э+м*|м{2,}|ммм*|uh+|um+|hmm+|эээ+)(?![\\p{L}])[,.…]*", RegexOption.IGNORE_CASE), "")
        // Whisper's loop: the same piece said again and again in a row stays once
        t = t.replace(Regex("(.{6,}?[.!?…]?)(?:\\s*\\1){1,}", RegexOption.IGNORE_CASE), "$1")
        t = t.replace(Regex("\\s+([,.!?…])"), "$1").replace(Regex("\\s+"), " ").trim().trimStart(',', '.', ' ')
        if (t.isNotEmpty()) t = t[0].uppercase() + t.substring(1)
        val junk = listOf("Продолжение следует", "Субтитры", "Редактор субтитров", "Thank you for watching", "Спасибо за просмотр")
        return if (junk.any { t.startsWith(it, true) } && t.length < 60) "" else t
    }

    // shows Whisper the style: punctuation, Russian with English words left as they are
    private const val PROMPT = "Привет. Открой settings.json, поправь Composer и сделай commit в GitHub. " +
        "Let's check the build, ок? Запусти тесты через Gradle, потом push."

    private fun wavHeader(len: Int): ByteArray = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray()); putInt(36 + len); put("WAVE".toByteArray())
        put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(RATE); putInt(RATE * 2); putShort(2); putShort(16)
        put("data".toByteArray()); putInt(len)
    }.array()
}
