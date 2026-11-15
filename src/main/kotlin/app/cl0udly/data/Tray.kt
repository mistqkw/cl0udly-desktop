@file:Suppress("FunctionName", "unused")

package app.cl0udly.data

import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.Tuple
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBus
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/**
 * The icon in the panel's tray, like Claude's: the mascot's silhouette in flat white with its eyes cut
 * out. It speaks StatusNotifierItem over D-Bus (what Quickshell, Waybar and KDE show; Java's own tray
 * only knows the old X11 one). A click opens the window, the right click has a small menu. While it's
 * there, closing the window only hides it, so the agent keeps working.
 */
object Tray {
    @Volatile var active = false
        private set

    private var conn: DBusConnection? = null
    private lateinit var onOpen: () -> Unit
    private lateinit var onQuick: () -> Unit
    /** The lines under the separator: the model in use and how much of the limit is gone. */
    @Volatile var plan = ""
    @Volatile var usage = ""
    private lateinit var onQuit: () -> Unit
    /** How many chats are working right now: the tooltip and the Quit item say so. */
    @Volatile var busy = 0

    private const val ITEM = "/StatusNotifierItem"
    private const val MENU = "/MenuBar"
    private const val WATCHER = "org.kde.StatusNotifierWatcher"

    fun start(open: () -> Unit, quick: () -> Unit, quit: () -> Unit) {
        onOpen = open; onQuick = quick; onQuit = quit
        Thread({
            runCatching {
                val c = DBusConnectionBuilder.forSessionBus().build()
                conn = c
                val name = "org.kde.StatusNotifierItem-${ProcessHandle.current().pid()}-1"
                c.requestBusName(name)
                c.exportObject(ITEM, Item)
                c.exportObject(MENU, Menu)
                fun register() = runCatching {
                    c.getRemoteObject(WATCHER, "/StatusNotifierWatcher", Watcher::class.java).RegisterStatusNotifierItem(name)
                    active = true
                }.onFailure { System.err.println("tray: can't register: $it") }
                register()
                // the panel restarted (or started after us): introduce ourselves again
                c.addSigHandler(DBus.NameOwnerChanged::class.java) { s ->
                    if (s.name == WATCHER) { if (s.newOwner.isNotEmpty()) register() else active = false }
                }
            }.onFailure { System.err.println("tray: $it") }
        }, "tray").apply { isDaemon = true }.start()
    }

    fun stop() { runCatching { conn?.close() }; conn = null; active = false }

    // ---------- the icon ----------

    class Px(@field:Position(0) @JvmField val w: Int, @field:Position(1) @JvmField val h: Int, @field:Position(2) @JvmField val data: ByteArray) : Struct()

    // D-Bus can't read Kotlin's List<? extends …>: plain List<…> in the bytecode
    @JvmSuppressWildcards
    class Tip(
        @field:Position(0) @JvmField val icon: String,
        @field:Position(1) @JvmField val pixmaps: List<Px>,
        @field:Position(2) @JvmField val title: String,
        @field:Position(3) @JvmField val text: String,
    ) : Struct()

    /** The mascot in white, eyes as holes, from its drawing's own rectangles (748 × 428 units). */
    internal fun glyph(size: Int): Px {
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
        val k = size * 0.94f / 748f
        val x0 = (size - 748f * k) / 2; val y0 = (size - 428f * k) / 2
        fun r(a: Float, b: Float, c: Float, d: Float) {
            val l = Math.round(x0 + a * k); val t = Math.round(y0 + b * k)
            g.fillRect(l, t, maxOf(1, Math.round(x0 + c * k) - l), maxOf(1, Math.round(y0 + d * k) - t))
        }
        g.color = java.awt.Color.WHITE
        r(0f, 113f, 748f, 428f); r(152f, 0f, 635f, 114f)
        g.composite = java.awt.AlphaComposite.Clear
        r(245f, 110f, 313f, 308f); r(430f, 110f, 498f, 308f)
        g.dispose()
        val out = ByteArray(size * size * 4)
        for (y in 0 until size) for (x in 0 until size) {
            val p = img.getRGB(x, y); val i = (y * size + x) * 4
            out[i] = (p ushr 24).toByte(); out[i + 1] = (p ushr 16).toByte(); out[i + 2] = (p ushr 8).toByte(); out[i + 3] = p.toByte()
        }
        return Px(size, size, out)
    }

    private val pixmaps by lazy { listOf(16, 22, 24, 32, 48, 64).map(::glyph) }

    private fun tip() = if (busy > 0) "Working on $busy chat${if (busy == 1) "" else "s"}" else "Ready"

    @DBusInterfaceName("org.kde.StatusNotifierWatcher")
    interface Watcher : DBusInterface {
        fun RegisterStatusNotifierItem(service: String)
    }

    @DBusInterfaceName("org.kde.StatusNotifierItem")
    interface Sni : DBusInterface {
        fun Activate(x: Int, y: Int)
        fun SecondaryActivate(x: Int, y: Int)
        fun ContextMenu(x: Int, y: Int)
        fun Scroll(delta: Int, orientation: String)
    }

    @JvmSuppressWildcards
    private object Item : Sni, Properties {
        override fun getObjectPath() = ITEM
        override fun Activate(x: Int, y: Int) = onOpen()
        override fun SecondaryActivate(x: Int, y: Int) = onQuick()
        override fun ContextMenu(x: Int, y: Int) = onOpen()
        override fun Scroll(delta: Int, orientation: String) {}

        private fun props(): Map<String, Variant<*>> = mapOf(
            "Category" to Variant("ApplicationStatus"),
            "Id" to Variant("cl0udly"),
            "Title" to Variant("cl0udly"),
            "Status" to Variant("Active"),
            "WindowId" to Variant(0),
            "IconName" to Variant(""),
            "IconThemePath" to Variant(""),
            "IconPixmap" to Variant(pixmaps, "a(iiay)"),
            "OverlayIconName" to Variant(""),
            "AttentionIconName" to Variant(""),
            "ToolTip" to Variant(Tip("", emptyList(), "cl0udly", tip()), "(sa(iiay)ss)"),
            "ItemIsMenu" to Variant(false),
            "Menu" to Variant(DBusPath(MENU)),
        )

        @Suppress("UNCHECKED_CAST")
        override fun <A : Any?> Get(interfaceName: String, propertyName: String): A = props()[propertyName] as A
        override fun <A : Any?> Set(interfaceName: String, propertyName: String, value: A) {}
        override fun GetAll(interfaceName: String): Map<String, Variant<*>> = props()
    }

    // ---------- the right-click menu (com.canonical.dbusmenu) ----------

    @JvmSuppressWildcards
    class Layout(
        @field:Position(0) @JvmField val id: Int,
        @field:Position(1) @JvmField val props: Map<String, Variant<*>>,
        @field:Position(2) @JvmField val children: List<Variant<*>>,
    ) : Struct()

    /** Two answers at once; D-Bus reads their types from the type parameters, so it has to be generic. */
    @JvmSuppressWildcards
    class Two<A, B>(@field:Position(0) @JvmField val a: A, @field:Position(1) @JvmField val b: B) : Tuple()

    @JvmSuppressWildcards
    class ItemProps(@field:Position(0) @JvmField val id: Int, @field:Position(1) @JvmField val props: Map<String, Variant<*>>) : Struct()

    @JvmSuppressWildcards
    class MenuEvent(
        @field:Position(0) @JvmField val id: Int,
        @field:Position(1) @JvmField val eventId: String,
        @field:Position(2) @JvmField val data: Variant<*>,
        @field:Position(3) @JvmField val timestamp: UInt32,
    ) : Struct()

    @JvmSuppressWildcards
    @DBusInterfaceName("com.canonical.dbusmenu")
    interface DbusMenu : DBusInterface {
        fun GetLayout(parentId: Int, recursionDepth: Int, propertyNames: List<String>): Two<UInt32, Layout>
        fun GetGroupProperties(ids: List<Int>, propertyNames: List<String>): List<ItemProps>
        fun GetProperty(id: Int, name: String): Variant<*>
        fun Event(id: Int, eventId: String, data: Variant<*>, timestamp: UInt32)
        fun EventGroup(events: List<MenuEvent>): List<Int>
        fun AboutToShow(id: Int): Boolean
        fun AboutToShowGroup(ids: List<Int>): Two<List<Int>, List<Int>>
    }

    @JvmSuppressWildcards
    private object Menu : DbusMenu, Properties {
        override fun getObjectPath() = MENU

        // Claude's tray menu: Show App, Open Quick Entry | plan, usage | Quit
        private fun item(id: Int): Map<String, Variant<*>> = when (id) {
            1 -> mapOf("label" to Variant("Show App"))
            2 -> mapOf("label" to Variant("Open Quick Entry"))
            3, 7 -> mapOf("type" to Variant("separator"))
            4 -> mapOf("label" to Variant(plan.ifBlank { "No model yet" }), "enabled" to Variant(false))
            5 -> mapOf("label" to Variant(usage.ifBlank { "Usage: –" }), "enabled" to Variant(false))
            6 -> mapOf("label" to Variant("Working on $busy chat${if (busy == 1) "" else "s"}"), "enabled" to Variant(false), "visible" to Variant(busy > 0))
            8 -> mapOf("label" to Variant("Quit"))
            else -> mapOf("children-display" to Variant("submenu"))
        }
        private val ids = listOf(1, 2, 3, 4, 5, 6, 7, 8)
        private var revision = 1L

        override fun GetLayout(parentId: Int, recursionDepth: Int, propertyNames: List<String>): Two<UInt32, Layout> {
            val kids = if (parentId == 0) ids.map { Variant(Layout(it, item(it), emptyList()), "(ia{sv}av)") } else emptyList()
            return Two(UInt32(revision), Layout(parentId, item(parentId), kids))
        }
        override fun GetGroupProperties(ids: List<Int>, propertyNames: List<String>) = ids.map { ItemProps(it, item(it)) }
        override fun GetProperty(id: Int, name: String): Variant<*> = item(id)[name] ?: Variant("")
        override fun Event(id: Int, eventId: String, data: Variant<*>, timestamp: UInt32) {
            if (eventId != "clicked") return
            when (id) { 1 -> onOpen(); 2 -> onQuick(); 8 -> onQuit() }
        }
        override fun EventGroup(events: List<MenuEvent>): List<Int> { events.forEach { Event(it.id, it.eventId, it.data, it.timestamp) }; return emptyList() }
        // the plan, usage and work lines change: say so every time the menu opens
        override fun AboutToShow(id: Int): Boolean { revision++; return true }
        override fun AboutToShowGroup(ids: List<Int>): Two<List<Int>, List<Int>> = Two(emptyList(), emptyList())

        private fun props(): Map<String, Variant<*>> = mapOf(
            "Version" to Variant(UInt32(3)),
            "TextDirection" to Variant("ltr"),
            "Status" to Variant("normal"),
            "IconThemePath" to Variant(emptyList<String>(), "as"),
        )
        @Suppress("UNCHECKED_CAST")
        override fun <A : Any?> Get(interfaceName: String, propertyName: String): A = props()[propertyName] as A
        override fun <A : Any?> Set(interfaceName: String, propertyName: String, value: A) {}
        override fun GetAll(interfaceName: String): Map<String, Variant<*>> = props()
    }
}
