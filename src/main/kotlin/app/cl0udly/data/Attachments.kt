package app.cl0udly.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.File
import java.util.Base64

/**
 * Files, folders and pictures sent along with a message, as OpenCode `file` parts.
 * Pictures travel inline (a data: URL, shrunk to 2048 px when big) so the session keeps them:
 * the chat can show them later, on this computer and on a phone driving it by Remote Control.
 * Text files and folders go as file:// links the server reads itself.
 */
object Attachments {
    private val images = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")
    private const val MAX_SIDE = 2048
    private const val MAX_INLINE = 4 * 1024 * 1024

    fun isImage(path: String) = path.substringAfterLast('.', "").lowercase() in images

    /** Where pasted and dropped pictures are kept. */
    val dir: File get() = File(Paths.data, "uploads").apply { mkdirs() }

    fun mime(f: File): String = when {
        f.isDirectory -> "application/x-directory"
        else -> when (f.extension.lowercase()) {
            "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; "gif" -> "image/gif"; "webp" -> "image/webp"; "bmp" -> "image/bmp"
            "pdf" -> "application/pdf"
            else -> runCatching { java.nio.file.Files.probeContentType(f.toPath()) }.getOrNull() ?: "text/plain"
        }
    }

    /** The part for one path; null when the file is gone. */
    fun part(path: String): JsonObject? {
        val f = File(path)
        if (!f.exists()) return null
        val mime = mime(f)
        val url = if (isImage(path)) inline(f) ?: f.toURI().toString() else f.toURI().toString()
        val (m, u) = if (url.startsWith("data:image/jpeg") && mime != "image/jpeg") "image/jpeg" to url else mime to url
        return buildJsonObject {
            put("type", JsonPrimitive("file"))
            put("mime", JsonPrimitive(m))
            put("url", JsonPrimitive(u))
            put("filename", JsonPrimitive(f.name))
        }
    }

    /** A picture as a data: URL; a big one is scaled down and re-encoded as JPEG. */
    private fun inline(f: File): String? = runCatching {
        val bytes = f.readBytes()
        val img = javax.imageio.ImageIO.read(f)
        if (img != null && (maxOf(img.width, img.height) > MAX_SIDE || bytes.size > MAX_INLINE)) {
            val k = MAX_SIDE.toDouble() / maxOf(img.width, img.height)
            val w = (img.width * minOf(1.0, k)).toInt().coerceAtLeast(1); val h = (img.height * minOf(1.0, k)).toInt().coerceAtLeast(1)
            val out = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB)
            out.createGraphics().apply {
                setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                drawImage(img, 0, 0, w, h, java.awt.Color.WHITE, null); dispose()
            }
            val buf = java.io.ByteArrayOutputStream()
            javax.imageio.ImageIO.write(out, "jpg", buf)
            "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(buf.toByteArray())
        } else "data:${mime(f)};base64," + Base64.getEncoder().encodeToString(bytes)
    }.getOrNull()

    /** Saves a picture from the clipboard as a PNG; returns its path. */
    fun saveImage(img: java.awt.Image): String? = runCatching {
        val w = img.getWidth(null); val h = img.getHeight(null)
        if (w <= 0 || h <= 0) return null
        val b = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        b.createGraphics().apply { drawImage(img, 0, 0, null); dispose() }
        val f = File(dir, "pasted-${System.currentTimeMillis()}.png")
        javax.imageio.ImageIO.write(b, "png", f)
        f.path
    }.getOrNull()

    /** What the clipboard holds as attachments: copied files (from a file manager) or a picture. */
    fun fromClipboard(): List<String> {
        val cb = runCatching { java.awt.Toolkit.getDefaultToolkit().systemClipboard }.getOrNull() ?: return emptyList()
        return from(runCatching { cb.getContents(null) }.getOrNull() ?: return emptyList())
    }

    /** Files or a picture carried by a paste or a drop. */
    fun from(t: java.awt.datatransfer.Transferable): List<String> {
        val flavors = java.awt.datatransfer.DataFlavor.javaFileListFlavor
        if (t.isDataFlavorSupported(flavors)) {
            @Suppress("UNCHECKED_CAST")
            val list = runCatching { t.getTransferData(flavors) as List<File> }.getOrNull()
            if (!list.isNullOrEmpty()) return list.map { it.path }
        }
        // Wayland/KDE puts copied files as text/uri-list lines
        val uriFlavor = runCatching { java.awt.datatransfer.DataFlavor("text/uri-list;class=java.lang.String") }.getOrNull()
        if (uriFlavor != null && t.isDataFlavorSupported(uriFlavor)) {
            val s = runCatching { t.getTransferData(uriFlavor) as String }.getOrNull()
            val files = s?.lines()?.filter { it.startsWith("file://") }?.mapNotNull { runCatching { File(java.net.URI(it.trim())).path }.getOrNull() }
            if (!files.isNullOrEmpty()) return files
        }
        if (t.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.imageFlavor)) {
            val img = runCatching { t.getTransferData(java.awt.datatransfer.DataFlavor.imageFlavor) as java.awt.Image }.getOrNull()
            img?.let(::saveImage)?.let { return listOf(it) }
        }
        return emptyList()
    }
}
