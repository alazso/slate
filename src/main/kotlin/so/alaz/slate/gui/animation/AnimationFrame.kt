package so.alaz.slate.gui.animation

import org.bukkit.inventory.ItemStack

data class AnimationFrame(
    val slotItems: Map<Int, ItemStack>,
    val sound: String? = null,
)

