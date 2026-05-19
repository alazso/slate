package so.alaz.slate.gui.animation

import org.bukkit.inventory.ItemStack
import so.alaz.slate.crate.Prize

class CsgoAnimation(
    override val totalTicks: Int = 40,
    override val tickInterval: Int = 1,
    private val spinSpeedMultiplier: Double = 1.0,
    private val revealTailTicks: Int = 5,
) : AnimationTemplate {
    override val id: String = "csgo"

    override fun frame(tick: Int, prize: Prize, decoys: List<Prize>): AnimationFrame {
        val rowSlots = listOf(10, 11, 12, 13, 14, 15, 16)
        val items = mutableMapOf<Int, ItemStack>()
        val pool = if (decoys.isEmpty()) listOf(prize) else decoys
        val effectiveTick = (tick * spinSpeedMultiplier.coerceAtLeast(0.1)).toInt()
        val revealStartTick = (totalTicks - revealTailTicks.coerceAtLeast(1)).coerceAtLeast(1)
        rowSlots.forEachIndexed { index, slot ->
            val source = if (tick >= revealStartTick && index == rowSlots.lastIndex / 2) {
                prize
            } else {
                pool[(effectiveTick + index).mod(pool.size)]
            }
            items[slot] = AnimationItemFactory.fromPrize(source)
        }
        return AnimationFrame(items)
    }
}

