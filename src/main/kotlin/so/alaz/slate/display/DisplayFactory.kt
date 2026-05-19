package so.alaz.slate.display

import org.bukkit.Location
import org.bukkit.util.Vector
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.CratePlaceholderResolver
import so.alaz.slate.item.ItemFactory
import so.alaz.slate.item.visualStackForCrateDisplay

class DisplayFactory {
    fun crateDisplay(crate: CrateDefinition, baseLocation: Location): DisplayGroupSpec? {
        val config = crate.display
        if (!config.enabled) return null

        val elements = mutableListOf<DisplayElementSpec>()
        if (config.item.enabled) {
            elements += DisplayElementSpec(
                type = DisplayElementType.ITEM,
                offset = Vector(0.0, config.item.heightOffset, 0.0),
                itemStack = ItemFactory.visualStackForCrateDisplay(crate, config.item.material),
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
        return CratePlaceholderResolver.render(
            template = template,
            crate = crate,
            playerName = playerName,
            crateNameOverride = crate.display.crateName ?: crate.name,
        )
    }

}
