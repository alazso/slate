package so.alaz.slate.gui.animation

import org.bukkit.inventory.ItemStack
import so.alaz.slate.crate.Prize

class SlotMachineAnimation(
    override val totalTicks: Int = 80,
    override val tickInterval: Int = 1,
    private val spinSpeedMultiplier: Double = 1.0,
    private val revealTailTicks: Int = 8,
) : AnimationTemplate {
    override val id: String = "slot-machine"

    override fun frame(tick: Int, prize: Prize, decoys: List<Prize>): AnimationFrame {
        val pool = if (decoys.isEmpty()) listOf(prize) else decoys
        val reels = listOf(11, 13, 15)
        val items = mutableMapOf<Int, ItemStack>()
        val effectiveTick = (tick * spinSpeedMultiplier.coerceAtLeast(0.1)).toInt()
        val revealTail = revealTailTicks.coerceAtLeast(1)
        val revealStartTick = (totalTicks - revealTail).coerceAtLeast(1)
        val elapsedIntoReveal = (tick - revealStartTick).coerceAtLeast(0)
        val lockCount = when {
            tick >= totalTicks -> reels.size
            elapsedIntoReveal <= 0 -> 0
            else -> ((elapsedIntoReveal * reels.size) / revealTail).coerceIn(0, reels.size - 1) + 1
        }
        reels.forEachIndexed { index, slot ->
            val source = if (index < lockCount) prize else pool[(effectiveTick + index * 2).mod(pool.size)]
            items[slot] = AnimationItemFactory.fromPrize(source)
        }
        return AnimationFrame(items)
    }
}

