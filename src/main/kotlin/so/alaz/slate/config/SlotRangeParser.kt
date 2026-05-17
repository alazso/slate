package so.alaz.slate.config

object SlotRangeParser {
    fun parse(raw: String?): List<Int> {
        if (raw.isNullOrBlank()) return emptyList()
        val result = linkedSetOf<Int>()
        raw.split(",")
            .map(String::trim)
            .filter(String::isNotBlank)
            .forEach { token ->
                if ("-" in token) {
                    val parts = token.split("-", limit = 2).map(String::trim)
                    val start = parts.getOrNull(0)?.toIntOrNull()
                    val end = parts.getOrNull(1)?.toIntOrNull()
                    if (start != null && end != null) {
                        val range = if (start <= end) start..end else end..start
                        range.forEach(result::add)
                    }
                } else {
                    token.toIntOrNull()?.let(result::add)
                }
            }
        return result.toList()
    }
}

