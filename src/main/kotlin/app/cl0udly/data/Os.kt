package app.cl0udly.data

import java.io.File

/**
 * Which system the app runs on, and the few things that differ: where data lives, which OpenCode
 * build to download, how to open a file. Linux is where it was made; Windows and macOS follow it.
 */
object Os {
    private val name = System.getProperty("os.name").lowercase()
    val isWindows = name.contains("win")
    val isMac = name.contains("mac") || name.contains("darwin")
    val isLinux = !isWindows && !isMac
    private val arm = System.getProperty("os.arch").lowercase().let { it.contains("aarch64") || it.contains("arm64") }

    /** The OpenCode release archive for this system. */
    val ocAsset: String = when {
        isWindows -> if (arm) "opencode-windows-arm64.zip" else "opencode-windows-x64.zip"
        isMac -> if (arm) "opencode-darwin-arm64.zip" else "opencode-darwin-x64.zip"
        else -> if (arm) "opencode-linux-arm64.tar.gz" else "opencode-linux-x64.tar.gz"
    }

    /** The OpenCode program's file name. */
    val ocExe = if (isWindows) "opencode.exe" else "opencode"

    /** Opens a file, a folder or a link with what the system uses for it. */
    fun open(target: String) {
        runCatching {
            when {
                isWindows -> ProcessBuilder("cmd", "/c", "start", "", target).start()
                isMac -> ProcessBuilder("open", target).start()
                else -> ProcessBuilder("xdg-open", target).start()
            }
        }
    }

    /** The shell the terminal starts when none is chosen. */
    val defaultShell: String get() = when {
        isWindows -> "powershell.exe"
        else -> System.getenv("SHELL") ?: if (isMac) "/bin/zsh" else "/bin/bash"
    }

    /** Where the app keeps its data and its settings. */
    val dataHome: File get() {
        val home = System.getProperty("user.home")
        return when {
            isWindows -> File(System.getenv("LOCALAPPDATA") ?: "$home\\AppData\\Local")
            isMac -> File(home, "Library/Application Support")
            else -> File(System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.local/share")
        }
    }
    val configHome: File get() {
        val home = System.getProperty("user.home")
        return when {
            isWindows -> File(System.getenv("APPDATA") ?: "$home\\AppData\\Roaming")
            isMac -> File(home, "Library/Application Support")
            else -> File(System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.config")
        }
    }
}
