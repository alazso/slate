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

    private val rowSlots = listOf(10, 11, 12, 13, 14, 15, 16)
    private val centerIndex = rowSlots.size / 2
    private val fastEnd = totalTicks / 2
    private val revealTailEffective = revealTailTicks.coerceIn(1, (totalTicks / 2).coerceAtLeast(1))
    private val revealStart = (totalTicks - revealTailEffective).coerceAtLeast(0)
    private val decelEnd = revealStart
    private val decelRenderTicks: Set<Int> = slowSpin(decelEnd - 1, 9).filter { it in fastEnd until decelEnd }.toSet()

    override fun shouldRenderOnTick(tick: Int): Boolean = when {
        tick >= revealStart -> tick == totalTicks - 1
        tick >= fastEnd -> tick in decelRenderTicks
        else -> fastPhaseRender(tick, tickInterval)
    }

    override fun frame(tick: Int, prize: Prize, decoys: List<Prize>): AnimationFrame {
        val items = mutableMapOf<Int, ItemStack>()
        val pool = if (decoys.isEmpty()) listOf(prize) else decoys
        val shiftOffset = (renderIndexAt(tick, ::shouldRenderOnTick) * spinSpeedMultiplier.coerceAtLeast(0.1)).toInt()
        val inReveal = tick >= revealStart || tick >= totalTicks
        rowSlots.forEachIndexed { index, slot ->
            val source = if (inReveal && index == centerIndex) {
                prize
            } else {
                pool[(shiftOffset + index).mod(pool.size)]
            }
            items[slot] = AnimationItemFactory.fromPrize(source)
        }
        return AnimationFrame(items)
    }
}
