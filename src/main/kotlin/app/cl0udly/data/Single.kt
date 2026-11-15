package app.cl0udly.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files

/**
 * One cl0udly at a time. Opening it again (from the app list while it sits in the tray) used to
 * start a second copy with a second tray icon; now the new one only knocks on the running one's
 * socket, which shows its window, and the new one quits.
 */
object Single {
    private val path get() = java.io.File(Paths.data, "instance.sock").toPath()

    /** Grows each time another launch asks to show the window. */
    var asks by mutableIntStateOf(0)
        private set

    /** True when a running cl0udly took the call: this launch should just exit. */
    fun handOff(): Boolean = runCatching {
        SocketChannel.open(UnixDomainSocketAddress.of(path)).use { it.write(ByteBuffer.wrap("show\n".toByteArray())) }
        true
    }.getOrDefault(false)

    /** Become the one: listen for later launches. */
    fun listen() {
        runCatching {
            Paths.data.mkdirs()
            Files.deleteIfExists(path)   // left over from a copy that was killed
            val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX).apply { bind(UnixDomainSocketAddress.of(path)) }
            Thread({
                while (true) {
                    val ch = runCatching { server.accept() }.getOrNull() ?: break
                    runCatching { ch.use { it.read(ByteBuffer.allocate(64)) } }
                    asks++
                }
            }, "single-instance").apply { isDaemon = true }.start()
        }
    }
}
