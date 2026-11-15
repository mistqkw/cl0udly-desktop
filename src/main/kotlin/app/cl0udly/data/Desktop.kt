package app.cl0udly.data

import java.io.File

/** Small bits of the Linux desktop: notifications, staying awake, starting with the session. */
object Desktop {
    /** Whether the window has focus (set by Main): no notifications while you're looking at it. */
    @Volatile var focused = true
    private var inhibitor: Process? = null

    fun notify(title: String, body: String) {
        runCatching { ProcessBuilder("notify-send", "-a", "cl0udly", "-i", "cl0udly", title, body.take(240)).start() }
    }

    /** While the agent works the computer doesn't fall asleep (systemd-inhibit, like Claude's "Keep computer awake"). */
    @Synchronized fun inhibit(on: Boolean) {
        if (on && inhibitor?.isAlive != true) {
            inhibitor = runCatching {
                // it waits on our own pid, not forever: if cl0udly dies the lock goes with it
                ProcessBuilder("systemd-inhibit", "--what=idle:sleep", "--who=cl0udly", "--why=The agent is working",
                    "tail", "--pid=${ProcessHandle.current().pid()}", "-f", "/dev/null").start()
            }.getOrNull()
        } else if (!on) { inhibitor?.destroy(); inhibitor = null }
    }

    private val autostart = File(System.getProperty("user.home"), ".config/autostart/cl0udly.desktop")
    val startsWithSession get() = autostart.exists()

    fun runOnStartup(on: Boolean) {
        if (!on) { autostart.delete(); return }
        val exe = File(System.getProperty("user.home"), ".local/share/cl0udly-app/bin/cl0udly")
        autostart.parentFile.mkdirs()
        autostart.writeText("[Desktop Entry]\nType=Application\nName=cl0udly\nExec=${exe.absolutePath}\nIcon=cl0udly\nX-GNOME-Autostart-enabled=true\n")
    }

    fun open(target: String) { runCatching { ProcessBuilder("xdg-open", target).start() } }

    /** The address other devices on the network reach this computer at. */
    /**
     * Addresses a phone can reach this computer at, with what they are: the home network,
     * Tailscale (100.64.0.0/10, reachable from anywhere once both devices are in it), or another VPN.
     */
    fun addresses(): List<Pair<String, String>> = runCatching {
        java.net.NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.name.startsWith("docker") && !it.name.startsWith("br-") && !it.name.startsWith("veth") }
            .flatMap { nif -> nif.inetAddresses.toList().filterIsInstance<java.net.Inet4Address>().map { nif.name to it } }
            .mapNotNull { (name, a) ->
                val b = a.address.map { it.toInt() and 0xff }
                when {
                    b[0] == 100 && b[1] in 64..127 -> a.hostAddress to "Tailscale: from anywhere"
                    a.isSiteLocalAddress && (name.startsWith("tun") || name.startsWith("wg") || name.startsWith("zt")) -> a.hostAddress to "VPN ($name)"
                    a.isSiteLocalAddress -> a.hostAddress to "Home network ($name)"
                    else -> null
                }
            }
            .sortedBy { if (it.second.startsWith("Home")) 0 else 1 }
    }.getOrDefault(emptyList())

    fun lanAddress(): String? = runCatching {
        java.net.NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual && !it.name.startsWith("docker") && !it.name.startsWith("br-") }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is java.net.Inet4Address && it.isSiteLocalAddress }?.hostAddress
    }.getOrNull()
}
