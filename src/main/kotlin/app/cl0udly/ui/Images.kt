package app.cl0udly.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cl0udly.ui.kit.hoverTap
import app.cl0udly.ui.theme.Oc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Base64

/**
 * Pictures in the chat: decoded off the UI thread from a data: URL, a file:// URL or a path,
 * kept in a small cache so scrolling back doesn't decode them again.
 */
object Images {
    private val cache = object : LinkedHashMap<String, ImageBitmap>(32, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 40
    }

    private fun key(src: String) = if (src.startsWith("data:")) "d" + src.length + ":" + src.hashCode() + ":" + src.takeLast(32) else src

    fun cached(src: String): ImageBitmap? = synchronized(cache) { cache[key(src)] }

    fun bytes(src: String): ByteArray? = runCatching {
        when {
            src.startsWith("data:") -> Base64.getDecoder().decode(src.substringAfter("base64,", ""))
            src.startsWith("file:") -> File(java.net.URI(src)).readBytes()
            else -> File(src).takeIf { it.isFile && it.length() < 40L * 1024 * 1024 }?.readBytes()
        }
    }.getOrNull()

    suspend fun load(src: String): ImageBitmap? {
        cached(src)?.let { return it }
        val img = withContext(Dispatchers.IO) {
            runCatching { org.jetbrains.skia.Image.makeFromEncoded(bytes(src) ?: return@runCatching null).toComposeImageBitmap() }.getOrNull()
        } ?: return null
        synchronized(cache) { cache[key(src)] = img }
        return img
    }

    fun isImageSrc(src: String, mime: String? = null) =
        mime?.startsWith("image/") == true || src.startsWith("data:image/") || src.substringBefore('?').substringAfterLast('.').lowercase() in setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")
}

/** The picture opened full size; drawn over everything by [LightboxHost]. */
object Lightbox { var src by mutableStateOf<String?>(null) }

@Composable
fun rememberImage(src: String): ImageBitmap? = produceState(Images.cached(src), src) { if (value == null) value = Images.load(src) }.value

/** A picture in a message: rounded, at most [max] wide, a click opens it full size. */
@Composable
fun ChatImage(src: String, max: Dp = 340.dp, maxH: Dp = 300.dp) {
    val c = Oc.c
    val img = rememberImage(src)
    val shape = Oc.round(14.dp)
    val a = remember { Animatable(if (img != null) 1f else 0f) }
    LaunchedEffect(img != null) { if (img != null) a.animateTo(1f, tween(140)) }
    if (img == null) {
        Box(Modifier.size(160.dp, 110.dp).clip(shape).background(c.surfaceHigh))
        return
    }
    // the picture's own proportions, no wider than [max] and no taller than [maxH]
    val ratio = img.width.toFloat() / img.height.coerceAtLeast(1)
    val w = minOf(max, maxH * ratio)
    Image(
        img, null,
        Modifier.size(w, w / ratio)
            .graphicsLayer { alpha = a.value }.clip(shape).border(1.dp, c.border, shape)
            .hoverTap(shape) { Lightbox.src = src },
        contentScale = ContentScale.Crop,
    )
}

@Composable
fun ImageStrip(srcs: List<String>, max: Dp = 340.dp) {
    if (srcs.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val each = if (srcs.size == 1) max else 200.dp
        srcs.forEach { ChatImage(it, each, if (srcs.size == 1) 300.dp else 180.dp) }
    }
}

/** Side of a media tile: the size Claude's app shows a picture at (as on the phone). */
val TILE = 108.dp

/** A picture the agent sent as a small square tile with rounded corners; a click opens it full size. */
@Composable
fun ImageTile(src: String) {
    val c = Oc.c
    val img = rememberImage(src)
    val shape = Oc.round(18.dp)
    val a = remember { Animatable(if (img != null) 1f else 0f) }
    LaunchedEffect(img != null) { if (img != null) a.animateTo(1f, tween(140)) }
    Box(Modifier.size(TILE).clip(shape).background(c.surfaceHigh).border(1.dp, c.border, shape).hoverTap(shape) { Lightbox.src = src }) {
        if (img != null) Image(img, null, Modifier.size(TILE).graphicsLayer { alpha = a.value }, contentScale = ContentScale.Crop)
    }
}

/** Full-size picture: it grows from 92 % with a fade, Esc or a click closes it. */
@Composable
fun LightboxHost() {
    val src = Lightbox.src ?: return
    val c = Oc.c
    val img = rememberImage(src)
    val a = remember(src) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(src) { a.animateTo(1f, tween(150, easing = FastOutSlowInEasing)) }
    fun close() = scope.launch { a.animateTo(0f, tween(110)); Lightbox.src = null }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(src) { runCatching { focus.requestFocus() } }
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = a.value }.background(c.scrim.copy(alpha = 0.86f))
            .clickable(remember { MutableInteractionSource() }, null) { close() }
            .focusRequester(focus).focusable()
            .onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { close(); true } else false },
        contentAlignment = Alignment.Center,
    ) {
        if (img != null) Image(
            img, null,
            Modifier.padding(48.dp).graphicsLayer { val s = 0.92f + 0.08f * a.value; scaleX = s; scaleY = s }.clip(Oc.round(12.dp)),
            contentScale = ContentScale.Fit,
        )
    }
}
