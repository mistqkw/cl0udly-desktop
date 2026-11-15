package app.cl0udly.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cl0udly.data.UsageSnapshot
import app.cl0udly.data.UsageWindow
import app.cl0udly.ui.kit.Txt
import app.cl0udly.ui.theme.Oc

/** Three rings, one inside the other: 5 hours outside, the week, the month inside. They fill in when the page opens. */
@Composable
internal fun UsageHero(snap: UsageSnapshot) {
    val c = Oc.c
    val wins = listOf(Triple("5 hours", snap.fiveHour, 1f), Triple("7 days", snap.week, 0.62f), Triple("30 days", snap.month, 0.36f))
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 120f)) }
    fun tint(w: UsageWindow, a: Float) = if (w.percent >= 85) c.warn else c.accent.copy(alpha = a)
    Row(
        Modifier.fillMaxWidth().clip(Oc.round(14.dp)).background(c.surface).border(1.dp, c.border, Oc.round(14.dp)).padding(22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 11.dp.toPx(); val gap = 5.dp.toPx()
                wins.forEachIndexed { i, (_, w, a) ->
                    val inset = stroke / 2 + i * (stroke + gap)
                    val tl = Offset(inset, inset)
                    val sz = Size(size.width - inset * 2, size.height - inset * 2)
                    val st = Stroke(stroke, cap = StrokeCap.Round)
                    drawArc(c.surfaceHigh, -90f, 360f, false, tl, sz, style = st)
                    val sweep = (w.fraction.toFloat() * 360f * grow.value).coerceAtLeast(if (w.used > 0) 4f else 0f)
                    if (sweep > 0f) drawArc(tint(w, a), -90f, sweep, false, tl, sz, style = st)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val p = snap.fiveHour.percent
                Txt(if (p > 0 && p < 1) "<1%" else "${(p * grow.value).toInt()}%", size = 26.sp, weight = FontWeight.SemiBold)
                Txt("5 hours", size = 12.sp, color = c.textDim)
            }
        }
        Spacer(Modifier.width(28.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            wins.forEach { (name, w, a) ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 5.dp).size(8.dp).clip(Oc.round(4.dp)).background(tint(w, a)))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Txt("$name   ${money(w.used)} of ${money(w.limit)}", size = 14.sp)
                        Txt(w.resetAt?.let { "Frees up ${resetIn(it)}" } ?: "Nothing used in this window", size = 12.sp, color = c.textDim)
                    }
                }
            }
        }
    }
}

/** Cost per day as bars that rise one after another; today is the bright one. */
@Composable
internal fun DailyBars(days: List<Double>) {
    val c = Oc.c
    if (days.isEmpty()) return
    val max = days.max().coerceAtLeast(0.0001)
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    Column(Modifier.fillMaxWidth().clip(Oc.round(14.dp)).background(c.surface).border(1.dp, c.border, Oc.round(14.dp)).padding(20.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Txt("Today ${money(days.last())}", size = 14.sp, modifier = Modifier.weight(1f))
            Txt("Last 14 days ${money(days.sum())}", size = 13.sp, color = c.textDim)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            days.forEachIndexed { i, d ->
                // each bar starts a little after the one before it
                val t = (grow.value * 1.6f - i / days.size.toFloat() * 0.6f).coerceIn(0f, 1f)
                val h = (d / max).toFloat() * t
                Box(
                    Modifier.weight(1f).fillMaxHeight(h.coerceAtLeast(0.03f)).clip(Oc.round(3.dp))
                        .background(if (i == days.lastIndex) c.accent else c.accent.copy(alpha = if (d > 0) 0.35f else 0.12f)),
                )
            }
        }
    }
}
