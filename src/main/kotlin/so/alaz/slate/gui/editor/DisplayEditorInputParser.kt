package so.alaz.slate.gui.editor

import org.bukkit.Material
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay

internal object DisplayEditorInputParser {
    fun parseBoolean(raw: String): Boolean? = when (raw.trim().lowercase()) {
        "true", "yes", "y", "1", "on" -> true
        "false", "no", "n", "0", "off" -> false
        else -> null
    }

    fun parseFloat(raw: String): Float? = raw.trim().toFloatOrNull()

    fun parseDouble(raw: String): Double? = raw.trim().toDoubleOrNull()

    fun parseMaterial(raw: String): Material? =
        Material.matchMaterial(raw.trim().uppercase())

    fun parseBillboard(raw: String): Display.Billboard? = when (raw.trim().lowercase()) {
        "fixed" -> Display.Billboard.FIXED
        "vertical" -> Display.Billboard.VERTICAL
        "horizontal" -> Display.Billboard.HORIZONTAL
        "center", "centre" -> Display.Billboard.CENTER
        else -> null
    }

    fun parseTransform(raw: String): ItemDisplay.ItemDisplayTransform? =
        ItemDisplay.ItemDisplayTransform.entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }

    fun parseLines(raw: String): List<String> =
        raw.split("|").map { it.trim() }.filter { it.isNotBlank() }
}
