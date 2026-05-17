package so.alaz.slate.gui.editor

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.gui.SlateGui
import so.alaz.slate.gui.StaticSlot
import so.alaz.slate.util.ComponentUtil

class DisplayEditorGui(
    private val crate: CrateDefinition,
) : SlateGui("Display: ${crate.id}", 3) {
    override suspend fun onBeforeOpen(player: org.bukkit.entity.Player) {
        val stack = ItemStack(Material.ITEM_FRAME)
        stack.editMeta { meta ->
            meta.displayName(ComponentUtil.parse("<gold>Display Settings</gold>"))
            meta.lore(
                listOf(
                    "<gray>Enabled: ${crate.display.enabled}</gray>",
                    "<gray>View range: ${crate.display.viewRange}</gray>",
                ).map(ComponentUtil::parse),
            )
        }
        slot(13, StaticSlot(stack))
    }
}

