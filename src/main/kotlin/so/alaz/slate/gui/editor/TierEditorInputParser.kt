package so.alaz.slate.gui.editor

internal object TierEditorInputParser {
    private val tierIdPattern = Regex("^[A-Za-z0-9_-]+$")

    fun parseTierId(raw: String): String? {
        val normalized = raw.trim()
        if (normalized.isEmpty()) return null
        if (!tierIdPattern.matches(normalized)) return null
        return normalized
    }

    fun parseDisplayName(raw: String): String = raw.trim()

    fun parsePositiveWeight(raw: String): Int? {
        val value = raw.trim().toIntOrNull() ?: return null
        return value.takeIf { it > 0 }
    }
}
