package so.alaz.slate.gui.editor

import org.bukkit.Material

internal object KeyEditorInputParser {
    fun parseLore(raw: String): List<String> =
        raw.split("|")
            .map(String::trim)
            .filter { it.isNotEmpty() }

    fun parseCustomModelData(raw: String): List<Float>? {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) return emptyList()
        val values = cleaned
            .split(",", " ")
            .map(String::trim)
            .filter { it.isNotEmpty() }
        if (values.isEmpty()) return emptyList()
        return values.map { token -> token.toFloatOrNull() ?: return null }
    }

    fun parseMaterial(raw: String): Material? =
        Material.matchMaterial(raw.trim().uppercase())
}
