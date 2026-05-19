package so.alaz.slate.gui.editor

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import so.alaz.slate.gui.StaticSlot

internal object EditorBackgroundSupport {
    private val fillerItem: ItemStack =
        ItemStack(Material.GRAY_STAINED_GLASS_PANE).apply {
            editMeta { meta ->
                meta.displayName(net.kyori.adventure.text.Component.empty())
            }
        }

    fun fillerSlot(): StaticSlot = StaticSlot(fillerItem.clone())
}
