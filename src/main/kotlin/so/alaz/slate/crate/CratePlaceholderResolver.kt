package so.alaz.slate.crate

import kotlin.time.Duration

object CratePlaceholderResolver {
    fun render(
        template: String,
        crate: CrateDefinition,
        playerName: String? = null,
        crateNameOverride: String? = null,
    ): String {
        val values = mapOf(
            "crate_id" to crate.id,
            "crate_name" to (crateNameOverride ?: crate.name),
            "key_name" to crate.keyDefinition.name,
            "key_amount" to crate.requiredKeys.toString(),
            "cooldown" to crate.cooldown.displayValue(),
            "player_name" to (playerName ?: ""),
        )
        return values.entries.fold(template) { current, (key, value) ->
            current.replace("{$key}", value)
        }
    }

    fun renderLore(lines: List<String>, crate: CrateDefinition, playerName: String? = null): List<String> =
        lines.map { render(it, crate, playerName) }

    private fun Duration.displayValue(): String {
        if (inWholeMilliseconds <= 0) return "none"
        val seconds = inWholeSeconds
        if (seconds < 60) return "${seconds}s"
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        if (minutes < 60) return if (remainingSeconds == 0L) "${minutes}m" else "${minutes}m ${remainingSeconds}s"
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        return if (remainingMinutes == 0L) "${hours}h" else "${hours}h ${remainingMinutes}m"
    }
}

