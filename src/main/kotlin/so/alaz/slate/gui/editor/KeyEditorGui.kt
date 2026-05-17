package so.alaz.slate.gui.editor

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.gui.SlateGui
import so.alaz.slate.gui.StaticSlot
import so.alaz.slate.util.ComponentUtil

class KeyEditorGui(
    private val crate: CrateDefinition,
) : SlateGui("Key: ${crate.id}", 3) {
    override suspend fun onBeforeOpen(player: org.bukkit.entity.Player) {
        val item = ItemStack(crate.keyDefinition.material.takeIf { it != Material.AIR } ?: Material.TRIPWIRE_HOOK)
        item.editMeta { meta ->
            meta.displayName(ComponentUtil.parse(crate.keyDefinition.name))
            meta.lore(
                listOf(
                    "<gray>Provider: ${crate.keyDefinition.provider}</gray>",
                    "<gray>Glowing: ${crate.keyDefinition.glowing}</gray>",
                ).map(ComponentUtil::parse),
            )
        }
        slot(13, StaticSlot(item))
    }
}

