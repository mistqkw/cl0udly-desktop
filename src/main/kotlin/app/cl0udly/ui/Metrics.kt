package app.cl0udly.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The window frame, measured off Claude desktop at scale 1 (docs/measurements.md). */
object M {
    val titleBar = 47.dp
    val titleButton = 32.dp
    val sidebar = 362.dp
    val navRow = 44.dp
    val navText = 17.sp
    val listInset = 12.dp
    val listRow = 40.dp
    val listRadius = 10.dp
    val column = 962.dp
    val composerExtra = 23.dp
    val composerHeight = 56.dp
    val composerRadius = 14.dp
    val bubbleMax = 797.dp
    val bubbleRadius = 16.dp
    val body = 17.sp
    val bodyLine = 27.5.sp
    val paragraphGap = 15.dp
}

sealed interface Screen {
    data object Home : Screen
    data class Chat(val id: String) : Screen
    data class Settings(val section: String = "general") : Screen
}

/** Back and forward like a browser: the arrows in the title bar (Claude desktop has them too). */
object Nav {
    var screen by mutableStateOf<Screen>(Screen.Home)
        private set
    private val back = mutableStateListOf<Screen>()
    private val forward = mutableStateListOf<Screen>()
    /** The project open in Code mode (a folder), or null for the project picker. */
    var project by mutableStateOf<String?>(null)
    var search by mutableStateOf(false)
    var quick by mutableStateOf(false)

    val canBack get() = back.isNotEmpty()
    val canForward get() = forward.isNotEmpty()

    fun go(s: Screen) {
        if (s == screen) return
        back.add(screen); if (back.size > 50) back.removeAt(0)
        forward.clear()
        screen = s
    }

    fun back() { back.removeLastOrNull()?.let { forward.add(screen); screen = it } }
    fun forward() { forward.removeLastOrNull()?.let { back.add(screen); screen = it } }
}
