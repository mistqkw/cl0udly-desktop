package app.cl0udly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cl0udly.data.Memory
import app.cl0udly.data.Paths
import app.cl0udly.ui.kit.Ic
import app.cl0udly.ui.kit.Txt
import app.cl0udly.ui.theme.Oc
import kotlinx.coroutines.delay

/** The sidebar's icon for Memory: dots joined by lines, a small graph. */
val MEMORY_ICON = Ic.Bulb

/**
 * Memory as its own screen, like on the phone: the graph on the whole page (drag, scroll to zoom,
 * click a dot to light it up), what you clicked in a card at the bottom, and More for the
 * settings. When the agent saves something new, the graph picks it up within a few seconds and
 * the new dot pops in.
 */
@Composable
fun MemoryScreen() {
    val c = Oc.c
    var rev by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf<MemNode?>(null) }
    // the folder changed (the agent remembered or forgot something): read it again
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            val stamp = Paths.memory.listFiles()?.maxOfOrNull { it.lastModified() } ?: 0L
            if (stamp != last) { last = stamp; rev++ }
            delay(3000)
        }
    }
    val shelves = remember(rev) { Memory.shelves() }
    val facts = remember(rev) { shelves.sumOf { Memory.facts(it).count { l -> l.trim().startsWith("- ") } } }
    Box(Modifier.fillMaxSize()) {
        MemoryGraphView(
            Paths.memory, java.io.File(Paths.data, "memory-graph-seen"), rev,
            GraphColors(c.border, c.text, c.textDim, c.accent, c.text.copy(alpha = 0.75f), c.bg),
            onSelect = { picked = it },
            onNew = { app.cl0udly.data.Sounds.play(app.cl0udly.data.Sounds.S.Pop) },
        )
        Column(Modifier.align(Alignment.BottomStart).padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            picked?.let { n ->
                Column(Modifier.width(420.dp).clip(Oc.round(14.dp)).background(c.surface).border(1.dp, c.border, Oc.round(14.dp)).padding(16.dp)) {
                    Txt(n.label, size = 15.sp, maxLines = 6, raw = true)
                    n.file?.takeIf { n.kind == 2 }?.let { Txt(it.nameWithoutExtension, size = 12.sp, color = c.textDim, raw = true) }
                }
            }
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt(if (facts == 0) "Nothing saved yet" else "$facts facts on ${shelves.size} shelves", size = 13.sp, color = c.textDim, modifier = Modifier.padding(end = 12.dp))
            Button("More", primary = false) { Nav.settings = "memory" }
        }
    }
}
