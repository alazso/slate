package so.alaz.slate.gui.animation

import org.bukkit.inventory.ItemStack
import so.alaz.slate.crate.Prize

class AgencyPickAnimation(
    override val totalTicks: Int = 60,
    override val tickInterval: Int = 1,
    private val spinSpeedMultiplier: Double = 1.0,
    private val revealTailTicks: Int = 5,
) : AnimationTemplate {
    override val id: String = "agency-pick"

    override fun frame(tick: Int, prize: Prize, decoys: List<Prize>): AnimationFrame {
        val pool = if (decoys.isEmpty()) listOf(prize) else decoys
        val options = listOf(11, 13, 15)
        val items = mutableMapOf<Int, ItemStack>()
        val effectiveTick = (tick * spinSpeedMultiplier.coerceAtLeast(0.1)).toInt()
        val revealStartTick = (totalTicks - revealTailTicks.coerceAtLeast(1)).coerceAtLeast(1)
        options.forEachIndexed { index, slot ->
            val source = if (tick >= revealStartTick && index == 1) {
                prize
            } else {
                pool[(effectiveTick + index).mod(pool.size)]
            }
            items[slot] = AnimationItemFactory.fromPrize(source)
        }
        return AnimationFrame(items)
    }
}

