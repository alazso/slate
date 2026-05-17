package so.alaz.slate.gui.editor

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import so.alaz.slate.crate.Prize
import so.alaz.slate.gui.SlateGui
import so.alaz.slate.gui.StaticSlot
import so.alaz.slate.util.ComponentUtil

class RewardEditorGui(
    private val prize: Prize,
) : SlateGui("Reward: ${prize.id}", 3) {
    override suspend fun onBeforeOpen(player: org.bukkit.entity.Player) {
        val details = ItemStack(Material.NAME_TAG)
        details.editMeta { meta ->
            meta.displayName(ComponentUtil.parse(prize.name))
            meta.lore(
                listOf(
                    "<gray>Tier: ${prize.tier ?: "none"}</gray>",
                    "<gray>Weight: ${prize.weight}</gray>",
                    "<gray>Chance: ${prize.chance ?: 0.0}</gray>",
                ).map(ComponentUtil::parse),
            )
        }
        slot(13, StaticSlot(details))
    }
}

