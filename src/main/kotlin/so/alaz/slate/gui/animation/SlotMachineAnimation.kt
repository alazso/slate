package so.alaz.slate.gui.animation

import org.bukkit.inventory.ItemStack
import so.alaz.slate.crate.Prize

class SlotMachineAnimation(
    override val totalTicks: Int = 80,
    override val tickInterval: Int = 1,
) : AnimationTemplate {
    override val id: String = "slot-machine"

    override fun frame(tick: Int, prize: Prize, decoys: List<Prize>): AnimationFrame {
        val pool = if (decoys.isEmpty()) listOf(prize) else decoys
        val reels = listOf(11, 13, 15)
        val items = mutableMapOf<Int, ItemStack>()
        reels.forEachIndexed { index, slot ->
            val lockTick = totalTicks - ((index + 1) * 8)
            val source = if (tick >= lockTick) prize else pool[(tick + index * 2).mod(pool.size)]
            items[slot] = AnimationItemFactory.fromPrize(source)
        }
        return AnimationFrame(items)
    }
}

