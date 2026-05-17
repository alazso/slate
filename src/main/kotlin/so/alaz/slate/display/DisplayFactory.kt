package so.alaz.slate.display

import kotlin.time.Duration
import org.bukkit.Location
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector
import so.alaz.slate.crate.CrateDefinition

class DisplayFactory {
    fun crateDisplay(crate: CrateDefinition, baseLocation: Location): DisplayGroupSpec? {
        val config = crate.display
        if (!config.enabled) return null

        val elements = mutableListOf<DisplayElementSpec>()
        if (config.item.enabled) {
            elements += DisplayElementSpec(
                type = DisplayElementType.ITEM,
                offset = Vector(0.0, config.item.heightOffset, 0.0),
                itemStack = ItemStack(config.item.material),
                scale = config.item.scale,
                billboard = config.billboard,
                viewRange = config.viewRange,
                itemTransform = config.item.transform,
            )
        }

        val lines = config.lines.ifEmpty {
            listOf(
                "<gradient:#a855f7:#38bdf8><bold>{crate_name}</bold></gradient>",
                "<gray>Requires: <yellow>{key_name}</yellow>",
                "<green>Right-click to open",
                "<aqua>Left-click to preview",
            )
        }
        lines.forEachIndexed { index, line ->
            elements += DisplayElementSpec(
                type = DisplayElementType.TEXT,
                offset = Vector(0.0, config.item.heightOffset - 0.45 - (index * config.lineSpacing), 0.0),
                text = renderCratePlaceholders(crate, line),
                scale = 1.0f,
                billboard = config.billboard,
                viewRange = config.viewRange,
            )
        }

        return DisplayGroupSpec(
            ownerType = "crate",
            ownerId = crate.id,
            baseLocation = baseLocation,
            elements = elements,
        )
    }

    fun renderCratePlaceholders(crate: CrateDefinition, template: String, playerName: String? = null): String {
        val values = mapOf(
            "crate_id" to crate.id,
            "crate_name" to (crate.display.crateName ?: crate.name),
            "key_name" to crate.keyDefinition.name,
            "key_amount" to crate.requiredKeys.toString(),
            "cooldown" to crate.cooldown.displayValue(),
            "player_name" to (playerName ?: ""),
        )
        return values.entries.fold(template) { current, (key, value) ->
            current.replace("{$key}", value)
        }
    }

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
