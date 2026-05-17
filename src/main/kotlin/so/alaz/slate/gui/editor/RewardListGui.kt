package so.alaz.slate.gui.editor

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.gui.SlateGui
import so.alaz.slate.gui.StaticSlot
import so.alaz.slate.util.ComponentUtil

class RewardListGui(
    private val crate: CrateDefinition,
) : SlateGui("Rewards: ${crate.id}", 6) {
    override suspend fun onBeforeOpen(player: org.bukkit.entity.Player) {
        crate.prizes.take(size).forEachIndexed { index, prize ->
            val stack = ItemStack(Material.EMERALD)
            stack.editMeta { meta ->
                meta.displayName(ComponentUtil.parse(prize.name))
                meta.lore(listOf("<gray>Weight: ${prize.weight}</gray>").map(ComponentUtil::parse))
            }
            slot(index, StaticSlot(stack))
        }
    }
}

