package so.alaz.slate.gui.editor

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.gui.SlateGui
import so.alaz.slate.gui.StaticSlot
import so.alaz.slate.util.ComponentUtil

class CrateListGui(
    private val player: Player,
) : SlateGui("Crate Editor", 6) {
    override suspend fun onBeforeOpen(player: Player) {
        val crates = SlateAPI.crateManager()?.all()?.sortedBy { it.id }.orEmpty()
        crates.take(size).forEachIndexed { index, crate ->
            val item = ItemStack(Material.CHEST)
            item.editMeta { meta ->
                meta.displayName(ComponentUtil.parse(crate.name))
                meta.lore(listOf("<gray>ID: ${crate.id}</gray>").map(ComponentUtil::parse))
            }
            slot(index, StaticSlot(item))
        }
    }
}

