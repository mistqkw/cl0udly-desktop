package app.cl0udly.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cl0udly.Graph
import app.cl0udly.data.CHAT_DIR
import app.cl0udly.ui.cloud.CloudBackdrop
import app.cl0udly.ui.cloud.GhostCloud
import app.cl0udly.ui.cloud.PixelCloud
import app.cl0udly.ui.kit.Ic
import app.cl0udly.ui.kit.Icon
import app.cl0udly.ui.kit.Txt
import app.cl0udly.ui.kit.hoverTap
import app.cl0udly.ui.theme.Oc
import java.io.File
import kotlinx.coroutines.launch
import java.util.Calendar

/** Claude desktop's new-chat screen: a greeting over a big input, suggestions under it. */
@Composable
fun Home() {
    val c = Oc.c
    val look = Oc.look
    val prefs = Graph.prefs
    val code = prefs.mode == "code"
    Box(Modifier.fillMaxSize()) {
        if (look.clouds) Box(Modifier.fillMaxSize().then(with(CloudBackdrop) { Modifier.backdrop(c.bg, c.isDark, "desk-home-${prefs.mode}", false) }))
        if (code && Nav.project == null) { ProjectPicker(); return@Box }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Greeting(code)
            Spacer(Modifier.height(28.dp))
            val dir = if (code) Nav.project!! else CHAT_DIR
            Composer(
                big = true, busy = false,
                onSend = { Graph.scope.launchSend(dir, it) },
                placeholder = if (code) "Describe a task for ${File(dir).name}" else "How can I help you today?",
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
            )
            Spacer(Modifier.height(18.dp))
            Suggestions(code) { Graph.scope.launchSend(dir, it) }
            Spacer(Modifier.weight(1.3f))
        }
    }
}

/** The greeting rises in with the mascot (cl0udly) or the spark (other looks), ~150 ms like on the phone. */
@Composable
private fun Greeting(code: Boolean) {
    val c = Oc.c
    val look = Oc.look
    val rise = remember { Animatable(14f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.coroutineScope {
            launch { rise.animateTo(0f, tween(160, easing = FastOutSlowInEasing)) }
            alpha.animateTo(1f, tween(140))
        }
    }
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val part = when (hour) { in 5..11 -> "Morning"; in 12..16 -> "Afternoon"; in 17..22 -> "Evening"; else -> "Night owl" }
    val name = Graph.prefs.name
    val text = if (code) "What are we building, $name?" else "$part, $name"
    Column(Modifier.graphicsLayer { translationY = rise.value; this.alpha = alpha.value }, horizontalAlignment = Alignment.CenterHorizontally) {
        if (look.clouds) {
            PixelCloud(Modifier.size(width = 168.dp, height = 120.dp), watching = ComposerState.typing)
            Spacer(Modifier.height(18.dp))
            Txt(text, size = 34.sp, family = Oc.heading, weight = FontWeight.Medium, align = TextAlign.Center)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spark(30.dp)
                Spacer(Modifier.width(14.dp))
                Txt(text, size = 38.sp, family = Oc.heading, weight = if (Oc.edition == "hairline") FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

/** The mark next to the greeting in non-cloud looks: a small ghost cloud in the accent's place. */
@Composable
fun Spark(size: androidx.compose.ui.unit.Dp) = GhostCloud(size, active = true, eyesLook = true)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Suggestions(code: Boolean, onPick: (String) -> Unit) {
    val items = if (code) listOf(
        Ic.Search to "Explain this project" to "Look through this project and explain how it is put together: main parts, how they talk, where to start reading.",
        Ic.Check to "Find bugs" to "Read the code and list the most likely bugs, with file and line, most serious first.",
        Ic.Terminal to "Run the tests" to "Find how tests run in this project, run them and tell me what fails and why.",
        Ic.Edit to "Write a README" to "Write a short README for this project: what it is, how to run it, how it is laid out.",
    ) else listOf(
        Ic.Edit to "Write" to "Help me write ",
        Ic.Bulb to "Learn" to "Explain to me how ",
        Ic.Code to "Code" to "Write a small program that ",
        Ic.Sun to "Life stuff" to "Give me ideas for ",
    )
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    AnimatedVisibility(shown, enter = fadeIn(tween(160, 60)) + slideInVertically(tween(160, 60)) { it / 3 }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { (head, prompt) ->
                val (ic, label) = head
                Chip(ic, label) { if (code) onPick(prompt) else ComposerPrefill.text = prompt }
            }
        }
    }
}

/** A suggestion for chats only starts the sentence: it goes into the field, not straight to the model. */
object ComposerPrefill { var text by mutableStateOf<String?>(null) }

@Composable
fun Chip(ic: Ic?, label: String, onClick: () -> Unit) {
    val c = Oc.c
    Row(
        Modifier.clip(Oc.round(10.dp)).border(1.dp, c.border, Oc.round(10.dp)).background(c.bg.copy(alpha = 0.6f))
            .hoverTap(Oc.round(10.dp), onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ic != null) { Icon(ic, 16.dp, c.textDim); Spacer(Modifier.width(8.dp)) }
        Txt(label, size = 14.sp, color = c.text.copy(alpha = 0.9f))
    }
}

/** Code with no folder yet: open one or pick a recent one. */
@Composable
private fun ProjectPicker() {
    val c = Oc.c
    val prefs = Graph.prefs
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (Oc.look.clouds) PixelCloud(Modifier.size(width = 140.dp, height = 100.dp), watching = false) else Spark(34.dp)
        Spacer(Modifier.height(18.dp))
        Txt("Pick a project", size = 32.sp, family = Oc.heading, weight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Txt("cl0udly works inside a folder: reads the code, runs commands, edits files.", size = 15.sp, color = c.textDim)
        Spacer(Modifier.height(26.dp))
        Column(Modifier.width(520.dp).clip(Oc.round(14.dp)).background(c.surface).border(1.dp, c.border, Oc.round(14.dp)).padding(6.dp)) {
            Row(Modifier.fillMaxWidth().height(48.dp).hoverTap(Oc.round(10.dp)) { pickProject() }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.Plus, 18.dp, c.text)
                Spacer(Modifier.width(12.dp))
                Txt("Open a folder…", size = 15.sp, weight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Txt("Ctrl+O", size = 13.sp, color = c.textDim, family = Oc.mono)
            }
            prefs.projectList.take(8).forEach { d ->
                Row(
                    Modifier.fillMaxWidth().height(48.dp).hoverTap(Oc.round(10.dp)) { Nav.project = d; prefs.addProject(d) }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Ic.Folder, 18.dp, c.textDim)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Txt(File(d).name, size = 15.sp, maxLines = 1)
                        Txt(d.replace(System.getProperty("user.home"), "~"), size = 12.sp, color = c.textDim, maxLines = 1, family = Oc.mono)
                    }
                    Box(Modifier.size(28.dp).hoverTap(Oc.round(8.dp)) { prefs.removeProject(d) }, contentAlignment = Alignment.Center) { Icon(Ic.Close, 13.dp, c.textDim) }
                }
            }
        }
    }
}
