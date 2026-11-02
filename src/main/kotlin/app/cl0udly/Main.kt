package app.cl0udly

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.cl0udly.ui.Nav
import app.cl0udly.ui.QuickEntry
import app.cl0udly.ui.Screen
import app.cl0udly.ui.SearchOverlay
import app.cl0udly.ui.Shell
import app.cl0udly.ui.cloud.CloudIntro
import app.cl0udly.ui.cloud.Pointer
import app.cl0udly.ui.pickProject
import app.cl0udly.ui.theme.Oc
import app.cl0udly.ui.theme.OcTheme
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    System.setProperty("skiko.renderApi", System.getProperty("skiko.renderApi") ?: "OPENGL")
    // the window class on X11/XWayland: without it Hyprland sees "java-lang-Thread" and the .desktop icon doesn't match
    runCatching {
        val tk = java.awt.Toolkit.getDefaultToolkit()
        tk.javaClass.getDeclaredField("awtAppClassName").apply { isAccessible = true }.set(tk, "cl0udly")
    }
    Graph.start()
    // a killed window must not leave its opencode server running
    Runtime.getRuntime().addShutdownHook(Thread { Graph.runtime.stop() })
    application {
        val state = rememberWindowState(size = androidx.compose.ui.unit.DpSize(1440.dp, 900.dp))
        val icon = remember { runCatching { BitmapPainter(Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")!!.use(::loadImageBitmap)) }.getOrNull() }
        fun quit() { Graph.runtime.stop(); exitApplication() }
        Window(
            onCloseRequest = ::quit, state = state, title = "cl0udly", icon = icon, undecorated = true,
            onPreviewKeyEvent = ::shortcut,
        ) {
            LaunchedEffect(Unit) { window.minimumSize = java.awt.Dimension(720, 560) }
            OcTheme {
                Box(
                    Modifier.fillMaxSize().background(Oc.c.bg)
                        .onPointerEvent(PointerEventType.Move) { Pointer.pos = it.changes.firstOrNull()?.position }
                        .onPointerEvent(PointerEventType.Exit) { Pointer.pos = null },
                ) {
                    Shell(onClose = ::quit)
                    SearchOverlay()
                    QuickEntry()
                    Intro()
                }
            }
        }
    }
}

private fun shortcut(e: KeyEvent): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    val p = Graph.prefs
    if (e.isCtrlPressed) when (e.key) {
        Key.N -> { Nav.go(Screen.Home); return true }
        Key.K -> { Nav.search = !Nav.search; return true }
        Key.B -> { p.sidebarOpen = !p.sidebarOpen; return true }
        Key.Comma -> { Nav.go(Screen.Settings()); return true }
        Key.O -> { pickProject(); return true }
        Key.Spacebar -> if (e.isShiftPressed) { Nav.quick = !Nav.quick; return true }
        else -> {}
    }
    if (e.key == Key.Escape) {
        if (Nav.search || Nav.quick) { Nav.search = false; Nav.quick = false; return true }
        (Nav.screen as? Screen.Chat)?.id?.let { id -> if (Graph.hub.statuses[id] != null) { Graph.hub.stop(id); return true } }
    }
    return false
}

/**
 * The phone's cl0udly splash, in a phone-sized box: the ridge opens, the little cloud lands,
 * the letters pop up. It plays while the server starts and dissolves into the home screen.
 * Only in the cl0udly look; a click skips it.
 */
@Composable
private fun Intro() {
    if (!Oc.look.clouds) return
    var done by remember { mutableStateOf(false) }
    if (done) return
    val t = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(2300, easing = LinearEasing))
        // hold on the last frame until the server answers (at most a few more seconds)
        kotlinx.coroutines.withTimeoutOrNull(4000) { Graph.runtime.state.first { it is app.cl0udly.runtime.RuntimeState.Running || it is app.cl0udly.runtime.RuntimeState.Failed } }
        exit.animateTo(1f, tween(420))
        done = true
        Graph.prefs.introSeen = true
    }
    Box(
        Modifier.fillMaxSize().background(Oc.c.bg.copy(alpha = 1f - exit.value))
            .clickable(remember { MutableInteractionSource() }, null) { done = true },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(460.dp, 700.dp)) { CloudIntro(t.value, exit.value) }
    }
}
