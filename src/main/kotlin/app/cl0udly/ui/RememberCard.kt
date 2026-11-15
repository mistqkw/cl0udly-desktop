package app.cl0udly.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cl0udly.Graph
import app.cl0udly.api.Part
import app.cl0udly.api.obj
import app.cl0udly.api.str
import app.cl0udly.data.Memory
import app.cl0udly.ui.kit.Ic
import app.cl0udly.ui.kit.Icon
import app.cl0udly.ui.kit.Txt
import app.cl0udly.ui.theme.Oc

/**
 * "Remember this?": the agent heard something lasting about the user and offers it with the
 * `remember` tool (cl0udly-brain.js) instead of saving it on its own. Remember puts the fact on its
 * shelf; the answer is kept per call, so the card stays answered. The phone has the same card.
 */
@Composable
fun RememberCard(p: Part) {
    val input = p.raw.obj("state")?.obj("input") ?: return
    val fact = input.str("fact")?.trim()?.takeIf { it.isNotEmpty() } ?: return
    val shelf = input.str("shelf")?.trim().orEmpty().ifBlank { "about-me" }
    val call = p.raw.str("callID") ?: p.id
    val c = Oc.c
    val answer = Graph.prefs.memoryAnswer(call)
    Column(
        Modifier.widthIn(max = 520.dp).clip(Oc.round(14.dp)).background(c.surface).border(1.dp, c.border, Oc.round(14.dp)).animateContentSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.Bulb, 16.dp, color = if (answer == "y") c.accent else c.textDim)
            Spacer(Modifier.width(8.dp))
            Txt(
                when (answer) { "y" -> "Remembered"; "n" -> "Not saved"; else -> "Remember this?" },
                size = 13.sp, color = c.textDim, weight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            Txt(shelf.removeSuffix(".md"), size = 12.sp, family = Oc.mono, color = c.textDim)
        }
        Txt(fact, size = 15.sp, color = if (answer == "n") c.textDim else c.text)
        if (answer == null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            Button("Remember") { Memory.addFact(shelf.removeSuffix(".md"), fact); Graph.prefs.answerMemory(call, true) }
            Button("No", primary = false) { Graph.prefs.answerMemory(call, false) }
        }
    }
}
