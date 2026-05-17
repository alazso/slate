package so.alaz.slate.gui.editor

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.gui.SlateGui
import so.alaz.slate.gui.StaticSlot
import so.alaz.slate.util.ComponentUtil

class TierEditorGui(
    private val crate: CrateDefinition,
) : SlateGui("Tiers: ${crate.id}", 4) {
    override suspend fun onBeforeOpen(player: org.bukkit.entity.Player) {
        crate.tiers.values.sortedBy { it.id }.take(size).forEachIndexed { index, tier ->
            val stack = ItemStack(Material.PAPER)
            stack.editMeta { meta ->
                meta.displayName(ComponentUtil.parse(tier.displayName))
                meta.lore(listOf("<gray>Weight: ${tier.weight}</gray>").map(ComponentUtil::parse))
            }
            slot(index, StaticSlot(stack))
        }
    }
}

