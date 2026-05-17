package so.alaz.slate.command.sub

object CommandFlagUtil {
    fun tokenize(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    }

    fun hasFlag(tokens: List<String>, flag: String): Boolean = tokens.any { it.equals(flag, ignoreCase = true) }

    fun extractReason(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val marker = "--reason"
        val idx = raw.indexOf(marker)
        if (idx < 0) return null
        val start = idx + marker.length
        if (start >= raw.length) return null
        return raw.substring(start).trim().trim('"').takeIf { it.isNotBlank() }
    }
}
