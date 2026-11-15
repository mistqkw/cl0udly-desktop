package app.cl0udly.data

import java.io.File

/** Small bits of the desktop: notifications, staying awake, starting with the session (Linux, Windows, macOS). */
object Desktop {
    /** Whether the window has focus (set by Main): no notifications while you're looking at it. */
    @Volatile var focused = true
    private var inhibitor: Process? = null

    fun notify(title: String, body: String) {
        runCatching {
            when {
                // Windows: a balloon from the tray icon; macOS: Notification Center through AppleScript
                Os.isWindows -> Tray.balloon(title, body.take(240))
                Os.isMac -> ProcessBuilder("osascript", "-e", "display notification ${appleQuote(body.take(240))} with title ${appleQuote(title)}").start()
                else -> ProcessBuilder("notify-send", "-a", "cl0udly", "-i", "cl0udly", title, body.take(240)).start()
            }
        }
    }

    private fun appleQuote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    /** While the agent works the computer doesn't fall asleep (systemd-inhibit, like Claude's "Keep computer awake"). */
    @Synchronized fun inhibit(on: Boolean) {
        if (on && inhibitor?.isAlive != true) {
            val pid = ProcessHandle.current().pid()
            inhibitor = runCatching {
                // it waits on our own pid, not forever: if cl0udly dies the lock goes with it
                when {
                    Os.isMac -> ProcessBuilder("caffeinate", "-i", "-w", "$pid").start()
                    // Windows: a PowerShell that keeps the system awake (SetThreadExecutionState) while we live
                    Os.isWindows -> ProcessBuilder(
                        "powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command",
                        "\$s='[DllImport(\"kernel32.dll\")]public static extern uint SetThreadExecutionState(uint f);';" +
                            "\$t=Add-Type -MemberDefinition \$s -Name P -Namespace W -PassThru;\$t::SetThreadExecutionState(0x80000001)|Out-Null;Wait-Process -Id $pid",
                    ).start()
                    else -> ProcessBuilder("systemd-inhibit", "--what=idle:sleep", "--who=cl0udly", "--why=The agent is working",
                        "tail", "--pid=$pid", "-f", "/dev/null").start()
                }
            }.getOrNull()
        } else if (!on) { inhibitor?.destroy(); inhibitor = null }
    }

    private val home = System.getProperty("user.home")
    private val autostart = when {
        Os.isWindows -> File(System.getenv("APPDATA") ?: "$home\\AppData\\Roaming", "Microsoft\\Windows\\Start Menu\\Programs\\Startup\\cl0udly.cmd")
        Os.isMac -> File(home, "Library/LaunchAgents/app.cl0udly.plist")
        else -> File(home, ".config/autostart/cl0udly.desktop")
    }
    val startsWithSession get() = autostart.exists()

    fun runOnStartup(on: Boolean) {
        if (!on) { autostart.delete(); return }
        // the program that is running now: wherever it was installed
        val exe = ProcessHandle.current().info().command().orElse(null)?.takeIf { !it.endsWith("java") && !it.endsWith("java.exe") }
            ?: File(home, ".local/share/cl0udly-app/bin/cl0udly").absolutePath
        autostart.parentFile.mkdirs()
        autostart.writeText(when {
            Os.isWindows -> "@start \"\" \"$exe\"\r\n"
            Os.isMac -> """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict><key>Label</key><string>app.cl0udly</string><key>ProgramArguments</key><array><string>$exe</string></array><key>RunAtLoad</key><true/></dict></plist>
"""
            else -> "[Desktop Entry]\nType=Application\nName=cl0udly\nExec=$exe\nIcon=cl0udly\nX-GNOME-Autostart-enabled=true\n"
        })
    }

    fun open(target: String) = Os.open(target)

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
