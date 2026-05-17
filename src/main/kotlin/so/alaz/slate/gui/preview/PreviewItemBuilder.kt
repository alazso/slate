package so.alaz.slate.gui.preview

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import so.alaz.slate.crate.Prize
import so.alaz.slate.probability.ChanceFormatter
import so.alaz.slate.probability.ProbabilityEngine
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.util.ComponentUtil

object PreviewItemBuilder {
    fun build(crate: CrateDefinition, prize: Prize): ItemStack {
        val material = prize.items.firstOrNull()?.material
            ?.let(Material::matchMaterial)
            ?: Material.CHEST
        val item = ItemStack(material)
        val table = ProbabilityEngine().buildTable(crate)
        val row = table.rows.firstOrNull { it.prize.id == prize.id }
        item.editMeta { meta ->
            meta.displayName(ComponentUtil.parse(prize.name))
            val lore = mutableListOf<String>()
            prize.tier?.let { lore += "<gray>Tier: <yellow>$it</yellow></gray>" }
            row?.let {
                lore += "<gray>Chance: <aqua>${ChanceFormatter.percent(it.effectiveChance)}</aqua></gray>"
            }
            lore += "<gray>Weight: <white>${prize.weight}</white></gray>"
            lore += "<gray>Id: <dark_gray>${prize.id}</dark_gray></gray>"
            meta.lore(lore.map(ComponentUtil::parse))
        }
        return item
    }
}

