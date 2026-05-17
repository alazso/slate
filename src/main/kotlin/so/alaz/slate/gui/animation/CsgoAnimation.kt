package so.alaz.slate.gui.animation

import org.bukkit.inventory.ItemStack
import so.alaz.slate.crate.Prize

class CsgoAnimation(
    override val totalTicks: Int = 40,
    override val tickInterval: Int = 1,
) : AnimationTemplate {
    override val id: String = "csgo"

    override fun frame(tick: Int, prize: Prize, decoys: List<Prize>): AnimationFrame {
        val rowSlots = listOf(10, 11, 12, 13, 14, 15, 16)
        val items = mutableMapOf<Int, ItemStack>()
        val pool = if (decoys.isEmpty()) listOf(prize) else decoys
        rowSlots.forEachIndexed { index, slot ->
            val source = if (tick >= totalTicks - 2 && index == rowSlots.lastIndex / 2) prize else pool[(tick + index).mod(pool.size)]
            items[slot] = AnimationItemFactory.fromPrize(source)
        }
        return AnimationFrame(items)
    }
}

