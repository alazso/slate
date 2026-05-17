package so.alaz.slate.gui

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

data class GuiClickContext(
    val player: Player,
    val session: GuiSession,
    val slotIndex: Int,
    val clickType: org.bukkit.event.inventory.ClickType,
)

sealed interface GuiSlot {
    suspend fun render(player: Player): ItemStack?
    suspend fun onClick(context: GuiClickContext) = Unit
}

data class StaticSlot(
    private val itemStack: ItemStack,
    private val clickHandler: (suspend (GuiClickContext) -> Unit)? = null,
) : GuiSlot {
    override suspend fun render(player: Player): ItemStack = itemStack.clone()
    override suspend fun onClick(context: GuiClickContext) {
        clickHandler?.invoke(context)
    }
}

data class DynamicSlot(
    private val provider: suspend (Player) -> ItemStack?,
    private val clickHandler: (suspend (GuiClickContext) -> Unit)? = null,
) : GuiSlot {
    override suspend fun render(player: Player): ItemStack? = provider(player)?.clone()
    override suspend fun onClick(context: GuiClickContext) {
        clickHandler?.invoke(context)
    }
}

data class PaginatedSlot(
    private val item: ItemStack,
    private val clickHandler: (suspend (GuiClickContext) -> Unit)? = null,
) : GuiSlot {
    override suspend fun render(player: Player): ItemStack = item.clone()
    override suspend fun onClick(context: GuiClickContext) {
        clickHandler?.invoke(context)
    }
}

data class AnimatedSlot(
    val frames: List<ItemStack>,
    val tickInterval: Int = 1,
    private val clickHandler: (suspend (GuiClickContext) -> Unit)? = null,
) : GuiSlot {
    override suspend fun render(player: Player): ItemStack? = frames.firstOrNull()?.clone()
    override suspend fun onClick(context: GuiClickContext) {
        clickHandler?.invoke(context)
    }

    fun frameAt(step: Int): ItemStack? {
        if (frames.isEmpty()) return null
        val normalized = if (tickInterval <= 1) step else step / tickInterval
        val index = normalized.mod(frames.size)
        return frames[index].clone()
    }
}

