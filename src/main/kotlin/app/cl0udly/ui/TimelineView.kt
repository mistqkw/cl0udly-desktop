package app.cl0udly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cl0udly.Graph
import app.cl0udly.data.Helpers
import app.cl0udly.data.Timeline
import app.cl0udly.ui.kit.Txt
import app.cl0udly.ui.theme.Oc

/** Which chat's timeline is open (from the chat's menu). */
object TimelineUi { var id by mutableStateOf<String?>(null) }

/**
 * The chat's timeline: who did what and when, on one line down the left, helpers in their own
 * color. On top: how long it took, how many steps and helpers, and what it cost. Same as the phone's.
 */
@Composable
fun TimelineHost() {
    val id = TimelineUi.id ?: return
    val c = Oc.c
    val chat = remember(id) { Graph.hub.chat(id) }
    val steps = remember(chat.messages.size, chat.messages.lastOrNull()?.parts?.size) { Timeline.of(chat.messages.toList()) { Helpers.label(it) } }
    val fmt = remember { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()) }
    androidx.compose.ui.window.Dialog(onDismissRequest = { TimelineUi.id = null }) {
        Column(Modifier.width(620.dp).clip(Oc.round(16.dp)).background(c.surface).border(1.dp, c.border, Oc.round(16.dp)).padding(22.dp)) {
            Txt("Timeline", size = 18.sp, weight = FontWeight.SemiBold, family = Oc.heading)
            val span = if (steps.size > 1) steps.last().at - steps.first().at else 0L
            val helpers = steps.count { it.kind == Timeline.Kind.Helper }
            val cost = steps.sumOf { it.cost }
            Txt(
                listOfNotNull(Timeline.duration(span), "${steps.count { it.kind == Timeline.Kind.Tool }} steps", if (helpers > 0) "$helpers helpers" else null, if (cost > 0) money(cost) else null).joinToString("  ·  "),
                size = 13.sp, color = c.textDim, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            if (steps.isEmpty()) Txt("Nothing has happened in this chat yet.", size = 14.sp, color = c.textDim)
            LazyColumn(Modifier.heightIn(max = 560.dp)) {
                items(steps) { s ->
                    val dot = when (s.kind) {
                        Timeline.Kind.You -> c.text
                        Timeline.Kind.Reply -> c.accent
                        Timeline.Kind.Helper -> androidx.compose.ui.graphics.Color(0xFF7CC6A4)
                        else -> c.textDim
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Txt(fmt.format(java.util.Date(s.at)), size = 12.sp, color = c.textDim, family = Oc.mono, modifier = Modifier.width(66.dp).padding(top = 2.dp))
                        Column(Modifier.width(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.padding(top = 5.dp).size(if (s.kind == Timeline.Kind.Tool || s.kind == Timeline.Kind.Thought) 6.dp else 9.dp).clip(CircleShape).background(dot))
                            Box(Modifier.width(1.dp).height(28.dp).background(c.border))
                        }
                        Column(Modifier.weight(1f).padding(start = 8.dp, bottom = 8.dp)) {
                            if (s.who.isNotBlank()) Txt(s.who, size = 13.sp, weight = FontWeight.Medium, color = if (s.kind == Timeline.Kind.Helper) dot else c.text, maxLines = 1)
                            Txt(s.what, size = 13.sp, color = if (s.kind == Timeline.Kind.You) c.text else c.textDim, maxLines = 2, raw = true)
                        }
                        Txt(
                            when { s.running -> "now"; s.ms != null && s.kind != Timeline.Kind.You -> Timeline.duration(s.ms); else -> "" },
                            size = 12.sp, color = if (s.running) c.accent else c.textDim, modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}
