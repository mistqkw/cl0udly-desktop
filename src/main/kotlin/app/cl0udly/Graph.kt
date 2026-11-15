package app.cl0udly

import app.cl0udly.api.EventHub
import app.cl0udly.api.OpenCodeApi
import app.cl0udly.data.Hub
import app.cl0udly.data.Prefs
import app.cl0udly.runtime.RuntimeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.swing.Swing

/** The app's singletons, created once at start (like Graph on the phone). */
object Graph {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)
    val prefs = Prefs()
    val api = OpenCodeApi(prefs)
    val runtime = RuntimeController(prefs, scope)
    /** Address of another computer's server, or empty for our own: EventHub reconnects when it changes. */
    val remote = MutableStateFlow(prefs.remoteUrl)
    val events = EventHub(api, runtime)
    val hub = Hub(api, events, prefs, scope)
    val usage = app.cl0udly.data.UsageTracker(api, prefs, events)
    val ocUpdater = app.cl0udly.runtime.OcUpdater(prefs, runtime, scope)

    fun start() {
        if (prefs.remoteUrl.isBlank()) runtime.start()
        events.start(scope)
        hub.start()
        usage.start()
        ocUpdater.start()
        // the phone syncs its chats and memory with this computer while Remote Control is on
        scope.launch { androidx.compose.runtime.snapshotFlow { prefs.remoteControl }.collect { app.cl0udly.data.SyncServer.follow(it) } }
    }

    fun useRemote(url: String, password: String) {
        prefs.remoteUrl = url.trim().trimEnd('/'); prefs.remotePassword = password
        remote.value = prefs.remoteUrl
        if (prefs.remoteUrl.isBlank()) runtime.start() else runtime.stop()
    }
}
