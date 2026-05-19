package so.alaz.slate.gui.editor

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import so.alaz.slate.gui.GuiClickContext
import so.alaz.slate.gui.StaticSlot
import so.alaz.slate.util.ComponentUtil

internal object EditorTileFactory {
    fun actionTile(
        material: Material,
        title: String,
        subtitle: String,
        onClick: suspend (GuiClickContext) -> Unit,
    ): StaticSlot {
        val item = ItemStack(material)
        item.editMeta { meta ->
            meta.displayName(ComponentUtil.parse(title))
            meta.lore(
                listOf(
                    subtitle,
                    "<dark_gray>Click to edit</dark_gray>",
                ).map(ComponentUtil::parse),
            )
        }
        return StaticSlot(item, onClick)
    }
}
