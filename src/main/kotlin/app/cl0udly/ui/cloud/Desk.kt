package app.cl0udly.ui.cloud

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import java.io.File

/** Where the cursor is in the window (null when it left): the mascot's eyes follow it instead of the phone's tilt. */
object Pointer {
    var pos by mutableStateOf<Offset?>(null)
}

/** Laptop battery from /sys/class/power_supply: (on the charger, percent), or null on a desktop. */
object Battery {
    fun read(): Pair<Boolean, Int>? {
        val supplies = File("/sys/class/power_supply").listFiles() ?: return null
        val bat = supplies.firstOrNull { runCatching { File(it, "type").readText().trim() == "Battery" }.getOrDefault(false) } ?: return null
        val cap = runCatching { File(bat, "capacity").readText().trim().toInt() }.getOrNull() ?: return null
        val status = runCatching { File(bat, "status").readText().trim() }.getOrDefault("")
        val ac = supplies.any { s -> runCatching { File(s, "type").readText().trim() == "Mains" && File(s, "online").readText().trim() == "1" }.getOrDefault(false) }
        return (ac || status == "Charging" || status == "Full") to cap
    }
}
