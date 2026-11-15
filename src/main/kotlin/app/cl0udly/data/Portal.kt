package app.cl0udly.data

import java.io.File
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

/**
 * The system's own open dialog. On Linux it is xdg-desktop-portal (KDE's here, the one Dolphin uses):
 * the portal answers with a Response signal sent only to the caller's bus connection, so the request
 * and the wait must share one connection: a tiny python-gi script does both. Without python-gi it
 * falls back to kdialog, then zenity; on macOS and Windows the native AWT dialog.
 * [onPick] runs on the UI thread with the chosen paths (empty if cancelled); [onFail] runs when no
 * dialog could be shown, so the caller can fall back to its own picker.
 */
object Portal {
    private val linux = System.getProperty("os.name").lowercase().contains("linux")

    fun open(title: String, folders: Boolean, multiple: Boolean, start: String?, onPick: (List<String>) -> Unit, onFail: () -> Unit) {
        if (!linux) return SwingUtilities.invokeLater { awt(title, folders, multiple, start, onPick) }
        thread(isDaemon = true, name = "portal") {
            val dir = start?.takeIf { File(it).isDirectory } ?: System.getProperty("user.home")
            val tries = listOf(
                listOf("python3", "-I", "-c", PORTAL, title, if (folders) "1" else "0", if (multiple) "1" else "0", dir),
                if (folders) listOf("kdialog", "--title", title, "--getexistingdirectory", dir)
                else listOf("kdialog", "--title", title, "--getopenfilename", dir) + if (multiple) listOf("--multiple", "--separate-output") else emptyList(),
                listOf("zenity", "--file-selection", "--title=$title", "--filename=$dir/", "--separator=\n") +
                    (if (folders) listOf("--directory") else emptyList()) + (if (multiple) listOf("--multiple") else emptyList()),
            )
            for (cmd in tries) {
                val r = run(cmd) ?: continue
                // python exits 0 with no lines on cancel; kdialog and zenity exit 1
                if (r.first == 0 || (cmd[0] != "python3" && r.first == 1)) {
                    val paths = r.second.filter { it.isNotBlank() && File(it).exists() }
                    return@thread SwingUtilities.invokeLater { onPick(paths) }
                }
            }
            SwingUtilities.invokeLater(onFail)
        }
    }

    /** Exit code and stdout lines, or null when the program is missing. */
    private fun run(cmd: List<String>): Pair<Int, List<String>>? = runCatching {
        val p = ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val lines = p.inputStream.bufferedReader().readLines()
        p.waitFor() to lines
    }.getOrNull()

    private fun awt(title: String, folders: Boolean, multiple: Boolean, start: String?, onPick: (List<String>) -> Unit) {
        val mac = System.getProperty("os.name").lowercase().contains("mac")
        if (folders && !mac) {
            val ch = javax.swing.JFileChooser(start).apply { dialogTitle = title; fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY }
            onPick(if (ch.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) listOf(ch.selectedFile.path) else emptyList())
            return
        }
        if (folders) System.setProperty("apple.awt.fileDialogForDirectories", "true")
        val d = java.awt.FileDialog(null as java.awt.Frame?, title, java.awt.FileDialog.LOAD).apply { isMultipleMode = multiple; directory = start }
        d.isVisible = true
        System.setProperty("apple.awt.fileDialogForDirectories", "false")
        onPick(d.files.map { it.path })
    }

    /** OpenFile on the portal and the wait for its Response on the same bus connection; prints the paths. */
    private val PORTAL = """
import os, sys
import gi
gi.require_version('Gio', '2.0')
from gi.repository import Gio, GLib
title, folders, multiple, start = sys.argv[1], sys.argv[2] == '1', sys.argv[3] == '1', sys.argv[4]
bus = Gio.bus_get_sync(Gio.BusType.SESSION, None)
token = 'cl0udly%d' % os.getpid()
sender = bus.get_unique_name()[1:].replace('.', '_')
path = '/org/freedesktop/portal/desktop/request/%s/%s' % (sender, token)
loop = GLib.MainLoop()
out = {}
def done(conn, snd, obj, iface, sig, params):
    code, res = params.unpack()
    out['code'] = code
    out['uris'] = res.get('uris', [])
    loop.quit()
bus.signal_subscribe('org.freedesktop.portal.Desktop', 'org.freedesktop.portal.Request', 'Response', path, None, 0, done)
opts = {'handle_token': GLib.Variant('s', token), 'modal': GLib.Variant('b', True),
        'multiple': GLib.Variant('b', multiple), 'directory': GLib.Variant('b', folders)}
if start:
    opts['current_folder'] = GLib.Variant('ay', start.encode() + b'\0')
bus.call_sync('org.freedesktop.portal.Desktop', '/org/freedesktop/portal/desktop', 'org.freedesktop.portal.FileChooser',
              'OpenFile', GLib.Variant('(ssa{sv})', ('', title, opts)), None, 0, -1, None)
loop.run()
if out.get('code') == 0:
    for u in out['uris']:
        print(Gio.File.new_for_uri(u).get_path())
""".trimIndent()
}
