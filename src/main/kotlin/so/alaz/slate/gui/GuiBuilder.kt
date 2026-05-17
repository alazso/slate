package so.alaz.slate.gui

import org.bukkit.Material
import org.bukkit.inventory.ItemStack

class GuiBuilder internal constructor(
    private val title: String,
    private val rows: Int,
) {
    private val slots = mutableMapOf<Int, GuiSlot>()

    fun border(material: Material) {
        val size = rows * 9
        val top = 0 until 9
        val bottom = (size - 9) until size
        top.forEach { slots[it] = StaticSlot(ItemStack(material)) }
        bottom.forEach { slots[it] = StaticSlot(ItemStack(material)) }
        for (y in 1 until rows - 1) {
            slots[y * 9] = StaticSlot(ItemStack(material))
            slots[(y * 9) + 8] = StaticSlot(ItemStack(material))
        }
    }

    fun slot(x: Int, y: Int, block: SlotBuilder.() -> Unit) {
        val index = (y * 9) + x
        val slotBuilder = SlotBuilder().apply(block)
        slotBuilder.slot?.let { slots[index] = it }
    }

    fun slot(index: Int, block: SlotBuilder.() -> Unit) {
        val slotBuilder = SlotBuilder().apply(block)
        slotBuilder.slot?.let { slots[index] = it }
    }

    fun region(xs: IntRange, ys: IntRange, block: SlotBuilder.() -> Unit) {
        ys.forEach { y ->
            xs.forEach { x ->
                slot(x, y, block)
            }
        }
    }

    internal fun build(base: SlateGui) {
        slots.forEach { (index, slot) ->
            base.slot(index, slot)
        }
    }

    class SlotBuilder {
        internal var slot: GuiSlot? = null

        fun static(item: ItemStack, onClick: (suspend (GuiClickContext) -> Unit)? = null) {
            slot = StaticSlot(item, onClick)
        }

        fun dynamic(provider: suspend (org.bukkit.entity.Player) -> ItemStack?, onClick: (suspend (GuiClickContext) -> Unit)? = null) {
            slot = DynamicSlot(provider, onClick)
        }

        fun paginated(item: ItemStack, onClick: (suspend (GuiClickContext) -> Unit)? = null) {
            slot = PaginatedSlot(item, onClick)
        }

        fun animated(frames: List<ItemStack>, tickInterval: Int = 1, onClick: (suspend (GuiClickContext) -> Unit)? = null) {
            slot = AnimatedSlot(frames, tickInterval, onClick)
        }

        fun prevPage(item: ItemStack = ItemStack(Material.ARROW)) {
            slot = StaticSlot(item)
        }

        fun nextPage(item: ItemStack = ItemStack(Material.ARROW)) {
            slot = StaticSlot(item)
        }

        fun close(item: ItemStack = ItemStack(Material.BARRIER)) {
            slot = StaticSlot(item) { it.player.closeInventory() }
        }
    }
}

fun gui(
    title: String,
    rows: Int,
    block: GuiBuilder.() -> Unit,
): BuiltGuiDefinition = BuiltGuiDefinition(title, rows, GuiBuilder(title, rows).apply(block))

data class BuiltGuiDefinition(
    val title: String,
    val rows: Int,
    private val builder: GuiBuilder,
) {
    fun materialize(): SlateGui = object : SlateGui(title, rows) {
        init {
            builder.build(this)
        }
    }
}

