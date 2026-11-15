package app.cl0udly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cl0udly.ui.kit.Ic
import app.cl0udly.ui.kit.Icon
import app.cl0udly.ui.kit.Txt
import app.cl0udly.ui.kit.hoverTap
import app.cl0udly.ui.theme.Oc

private sealed interface Block {
    data class Para(val text: String) : Block
    data class Head(val level: Int, val text: String) : Block
    data class Code(val lang: String, val text: String) : Block
    data class Item(val mark: String, val depth: Int, val text: String) : Block
    data class Quote(val text: String) : Block
    data object Rule : Block
}

private fun parse(src: String): List<Block> {
    val out = ArrayList<Block>()
    val lines = src.replace("\r", "").split('\n')
    var i = 0
    val para = StringBuilder()
    fun flush() { if (para.isNotBlank()) out.add(Block.Para(para.toString().trim())); para.setLength(0) }
    while (i < lines.size) {
        val l = lines[i]
        val t = l.trimStart()
        when {
            t.startsWith("```") -> {
                flush()
                val lang = t.removePrefix("```").trim()
                val sb = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) { sb.appendLine(lines[i]); i++ }
                out.add(Block.Code(lang, sb.toString().trimEnd('\n')))
            }
            Regex("^#{1,6} ").containsMatchIn(t) -> { flush(); out.add(Block.Head(t.takeWhile { it == '#' }.length, t.dropWhile { it == '#' }.trim())) }
            Regex("^([-*+]|\\d+[.)]) ").containsMatchIn(t) -> {
                flush()
                val mark = t.substringBefore(' ')
                out.add(Block.Item(if (mark[0].isDigit()) mark else "•", (l.length - t.length) / 2, t.substringAfter(' ')))
            }
            t.startsWith(">") -> { flush(); out.add(Block.Quote(t.removePrefix(">").trim())) }
            Regex("^(-{3,}|\\*{3,}|_{3,})$").matches(t.trim()) -> { flush(); out.add(Block.Rule) }
            t.isBlank() -> flush()
            else -> { if (para.isNotEmpty()) para.append('\n'); para.append(l) }
        }
        i++
    }
    flush()
    return out
}

private const val CODE = "md-code"

/** Inline code is blue, like in Claude's app: a light blue on dark, a deeper one on light. */
private fun codeBlue(dark: Boolean) = if (dark) androidx.compose.ui.graphics.Color(0xFF7FA8F5) else androidx.compose.ui.graphics.Color(0xFF2F64CC)

/** Text with `inline code` drawn in a rounded frame with a faint fill, line by line when it wraps (as on the phone). */
@Composable
private fun MdText(text: AnnotatedString, style: TextStyle, modifier: Modifier = Modifier) {
    val c = Oc.c
    val codes = remember(text) { text.getStringAnnotations(CODE, 0, text.length) }
    var layout by remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    val draw = if (codes.isEmpty()) modifier else modifier.drawBehind {
        val l = layout ?: return@drawBehind
        val fs = style.fontSize.toPx()
        val h = fs * 0.88f * 1.45f
        val r = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())
        codes.forEach { a ->
            if (a.end <= a.start || a.end > l.layoutInput.text.length) return@forEach
            for (line in l.getLineForOffset(a.start)..l.getLineForOffset(a.end - 1)) {
                val s = maxOf(a.start, l.getLineStart(line)); val e = minOf(a.end, l.getLineEnd(line, visibleEnd = true))
                if (e <= s) continue
                val x0 = l.getHorizontalPosition(s, usePrimaryDirection = true)
                val x1 = if (e >= l.getLineEnd(line, visibleEnd = true)) l.getLineRight(line) else l.getHorizontalPosition(e, usePrimaryDirection = true)
                val mid = l.getLineBaseline(line) - fs * 0.33f
                val tl = androidx.compose.ui.geometry.Offset(minOf(x0, x1), mid - h / 2)
                val sz = androidx.compose.ui.geometry.Size(kotlin.math.abs(x1 - x0), h)
                drawRoundRect(c.surfaceHigh, tl, sz, r)
                drawRoundRect(c.border, tl, sz, r, style = stroke)
            }
        }
    }
    Text(text, style = style, modifier = draw, onTextLayout = { layout = it })
}

/** Inline marks: **bold**, *italic*, `code`, [links](url). */
@Composable
private fun inline(s: String): AnnotatedString {
    val c = Oc.c
    val mono = Oc.mono
    return buildAnnotatedString {
        var i = 0
        while (i < s.length) {
            val rest = s.substring(i)
            when {
                rest.startsWith("**") && rest.indexOf("**", 2) > 2 -> {
                    val e = rest.indexOf("**", 2)
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(rest.substring(2, e)) }
                    i += e + 2
                }
                rest.startsWith("`") && rest.indexOf('`', 1) > 1 -> {
                    val e = rest.indexOf('`', 1)
                    // a blue word in a rounded frame, like Claude's app (MdText draws the frame); thin spaces give it room
                    withStyle(SpanStyle(fontFamily = mono, color = codeBlue(c.isDark), fontSize = 0.88.em())) {
                        pushStringAnnotation(CODE, ""); append("\u2009" + rest.substring(1, e) + "\u2009"); pop()
                    }
                    i += e + 1
                }
                rest.startsWith("[") && Regex("^\\[([^]]+)]\\(([^)\\s]+)\\)").find(rest) != null -> {
                    val m = Regex("^\\[([^]]+)]\\(([^)\\s]+)\\)").find(rest)!!
                    withLink(LinkAnnotation.Url(m.groupValues[2], TextLinkStyles(SpanStyle(color = c.link)))) { append(m.groupValues[1]) }
                    i += m.value.length
                }
                (rest.startsWith("*") || rest.startsWith("_")) && rest.length > 2 && !rest[1].isWhitespace() && rest.indexOf(rest[0], 1) > 1 -> {
                    val e = rest.indexOf(rest[0], 1)
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(rest.substring(1, e)) }
                    i += e + 1
                }
                else -> { append(s[i]); i++ }
            }
        }
    }
}

private fun Double.em() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Em)

@Composable
fun Markdown(text: String, modifier: Modifier = Modifier) {
    val c = Oc.c
    val blocks = remember(text) { parse(text) }
    val body = TextStyle(color = c.text, fontSize = M.body, lineHeight = M.bodyLine, fontFamily = Oc.reply)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(M.paragraphGap)) {
        blocks.forEach { b ->
            when (b) {
                is Block.Para -> MdText(inline(b.text), body)
                is Block.Head -> MdText(
                    inline(b.text),
                    body.copy(fontSize = when (b.level) { 1 -> 24.sp; 2 -> 21.sp; else -> 18.sp }, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, fontFamily = Oc.heading),
                    modifier = Modifier.padding(top = 6.dp),
                )
                is Block.Item -> Row(Modifier.padding(start = (b.depth * 18 + 4).dp)) {
                    Text(b.mark, style = body.copy(color = c.text), modifier = Modifier.width(if (b.mark == "•") 18.dp else 26.dp))
                    MdText(inline(b.text), body)
                }
                is Block.Quote -> Row {
                    Box(Modifier.width(3.dp).height(26.dp).background(c.border))
                    Spacer(Modifier.width(12.dp))
                    MdText(inline(b.text), body.copy(color = c.textDim))
                }
                is Block.Code -> CodeBlock(b.lang, b.text)
                Block.Rule -> Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            }
        }
    }
}

@Composable
fun CodeBlock(lang: String, code: String) {
    val c = Oc.c
    var copied by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(Oc.round(12.dp)).background(c.codeBg).border(1.dp, c.border, Oc.round(12.dp))) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt(lang.ifBlank { "text" }, size = 12.sp, color = c.textDim, family = Oc.mono, modifier = Modifier.weight(1f))
            Row(Modifier.hoverTap(Oc.round(6.dp)) { copyText(code); copied = true }.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (copied) Ic.Check else Ic.Copy, 14.dp, c.textDim)
                Spacer(Modifier.width(6.dp))
                Txt(if (copied) "Copied" else "Copy", size = 12.sp, color = c.textDim)
            }
        }
        Text(
            code, modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 14.dp),
            style = TextStyle(color = c.text, fontFamily = Oc.mono, fontSize = 14.sp, lineHeight = 21.sp), softWrap = false,
        )
    }
}

fun copyText(s: String) {
    runCatching { java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(s), null) }
}
