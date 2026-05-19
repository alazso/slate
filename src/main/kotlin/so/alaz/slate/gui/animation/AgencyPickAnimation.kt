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

    private val options = listOf(11, 13, 15)
    private val fastEnd = (totalTicks * 60) / 100
    private val revealTailEffective = revealTailTicks.coerceIn(1, (totalTicks / 2).coerceAtLeast(1))
    private val revealStart = (totalTicks - revealTailEffective).coerceAtLeast(0)
    private val decelEnd = revealStart
    private val decelRenderTicks: Set<Int> = slowSpin(decelEnd - 1, 7).filter { it in fastEnd until decelEnd }.toSet()

    override fun shouldRenderOnTick(tick: Int): Boolean = when {
        tick >= revealStart -> tick == totalTicks - 1
        tick >= fastEnd -> tick in decelRenderTicks
        else -> fastPhaseRender(tick, tickInterval)
    }

    override fun frame(tick: Int, prize: Prize, decoys: List<Prize>): AnimationFrame {
        val pool = if (decoys.isEmpty()) listOf(prize) else decoys
        val items = mutableMapOf<Int, ItemStack>()
        val shuffleIndex = (renderIndexAt(tick, ::shouldRenderOnTick) * spinSpeedMultiplier.coerceAtLeast(0.1)).toInt()
        val inReveal = tick >= revealStart || tick >= totalTicks
        options.forEachIndexed { index, slot ->
            val source = when {
                inReveal && index == 1 -> prize
                inReveal && index == 0 -> decoys.getOrElse(0) { pool.first() }
                inReveal -> decoys.getOrElse(1) { pool.getOrElse(1) { pool.first() } }
                else -> pool[(shuffleIndex + index).mod(pool.size)]
            }
            items[slot] = AnimationItemFactory.fromPrize(source)
        }
        return AnimationFrame(items)
    }
}
