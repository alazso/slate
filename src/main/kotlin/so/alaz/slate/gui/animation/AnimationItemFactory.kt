package so.alaz.slate.gui.animation

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import so.alaz.slate.crate.Prize
import so.alaz.slate.util.ComponentUtil

internal object AnimationItemFactory {
    fun fromPrize(prize: Prize): ItemStack {
        val material = prize.items.firstOrNull()?.material?.let(Material::matchMaterial) ?: Material.CHEST
        return ItemStack(material).apply {
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

