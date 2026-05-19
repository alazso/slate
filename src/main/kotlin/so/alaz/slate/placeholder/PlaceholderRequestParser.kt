package so.alaz.slate.placeholder

data class PlaceholderRequest(
    val key: String,
    val args: List<String>,
)

object PlaceholderRequestParser {
    fun parse(params: String): PlaceholderRequest? {
        val raw = params.trim()
        if (raw.isBlank()) return null

        if (":" in raw) {
            val parts = raw.split(':').map(String::trim)
            if (parts.isEmpty() || parts.any { it.isBlank() }) return null
            return PlaceholderRequest(
                key = parts.first().lowercase(),
                args = parts.drop(1),
            )
        }

        val lower = raw.lowercase()
        val noArgKeys = setOf(
            "total_opened",
            "pending_count",
            "anchor_count",
            "recent_operation_state",
        )
        return if (lower in noArgKeys) PlaceholderRequest(lower, emptyList()) else null
    }
}
