package so.alaz.slate.gui.editor

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.gui.SlateGui
import so.alaz.slate.gui.StaticSlot
import so.alaz.slate.util.ComponentUtil

class CrateEditorGui(
    private val crate: CrateDefinition,
) : SlateGui("Edit: ${crate.id}", 4) {
    override suspend fun onBeforeOpen(player: org.bukkit.entity.Player) {
        val summary = ItemStack(Material.WRITABLE_BOOK)
        summary.editMeta { meta ->
            meta.displayName(ComponentUtil.parse("<gold>${crate.name}</gold>"))
            meta.lore(
                listOf(
                    "<gray>Required keys: ${crate.requiredKeys}</gray>",
                    "<gray>Cooldown: ${crate.cooldown}</gray>",
                    "<gray>State: ${crate.lifecycle.state}</gray>",
                ).map(ComponentUtil::parse),
            )
        }
        slot(13, StaticSlot(summary))
    }
}

