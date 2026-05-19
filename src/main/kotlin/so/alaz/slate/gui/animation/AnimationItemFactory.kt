package so.alaz.slate.gui.animation

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import so.alaz.slate.crate.Prize
import so.alaz.slate.item.ItemFactory
import so.alaz.slate.item.visualStackForPrizeItem
import so.alaz.slate.util.ComponentUtil

internal object AnimationItemFactory {
    fun fromPrize(prize: Prize): ItemStack {
        return (prize.items.firstOrNull()
            ?.let { ItemFactory.visualStackForPrizeItem(it, Material.CHEST) }
            ?: ItemStack(Material.CHEST)).apply {
            editMeta { meta ->
                meta.displayName(ComponentUtil.parse(prize.name))
                val lore = mutableListOf<String>()
                prize.tier?.let { lore += "<gray>Tier: <yellow>$it</yellow></gray>" }
                lore += "<gray>Weight: <white>${prize.weight}</white></gray>"
                meta.lore(lore.map(ComponentUtil::parse))
            }
        }
    }
}

