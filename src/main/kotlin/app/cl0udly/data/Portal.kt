package app.cl0udly.data

import java.io.File
import java.net.URI
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

/**
 * The system's own open dialog through xdg-desktop-portal (here KDE's, the one Dolphin uses),
 * like Claude desktop. gdbus starts the request; the answer comes as a Response signal, which
 * `gdbus monitor` prints. [onPick] runs on the UI thread with the chosen paths (empty if cancelled);
 * [onFail] runs when there is no portal, so the caller can fall back to its own picker.
 */
object Portal {
    fun open(title: String, folders: Boolean, multiple: Boolean, start: String?, onPick: (List<String>) -> Unit, onFail: () -> Unit) {
        thread(isDaemon = true, name = "portal") {
            val token = "cl0udly${System.nanoTime()}"
            val monitor = runCatching {
                ProcessBuilder("gdbus", "monitor", "--session", "--dest", "org.freedesktop.portal.Desktop").redirectErrorStream(true).start()
            }.getOrNull() ?: return@thread SwingUtilities.invokeLater(onFail)
            try {
                Thread.sleep(150) // let the monitor subscribe before the dialog can answer
                val opts = buildString {
                    append("{'handle_token': <'$token'>, 'modal': <true>, 'multiple': <$multiple>, 'directory': <$folders>")
                    start?.takeIf { File(it).isDirectory }?.let { append(", 'current_folder': <b'${q(it)}'>") }
                    append("}")
                }
                val call = ProcessBuilder(
                    "gdbus", "call", "--session", "--dest", "org.freedesktop.portal.Desktop", "--object-path", "/org/freedesktop/portal/desktop",
                    "--method", "org.freedesktop.portal.FileChooser.OpenFile", "", "'${q(title)}'", opts,
                ).redirectErrorStream(true).start()
                if (call.waitFor() != 0) return@thread SwingUtilities.invokeLater(onFail)
                val out = monitor.inputStream.bufferedReader()
                while (true) {
                    val line = out.readLine() ?: return@thread SwingUtilities.invokeLater { onPick(emptyList()) }
                    if (token !in line || "Request.Response" !in line) continue
                    val paths = Regex("'(file://[^']*)'").findAll(line).mapNotNull { runCatching { File(URI(it.groupValues[1])).path }.getOrNull() }.toList()
                    return@thread SwingUtilities.invokeLater { onPick(if (line.contains("(uint32 0,")) paths else emptyList()) }
                }
            } finally { monitor.destroy() }
        }
    }

    /** A GVariant string literal: escape the quote and the backslash. */
    private fun q(s: String) = s.replace("\\", "\\\\").replace("'", "\\'")
}
