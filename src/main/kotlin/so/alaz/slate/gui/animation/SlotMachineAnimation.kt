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

    private val reels = listOf(11, 13, 15)
    private val reelStopTicks = listOf(
        (totalTicks * 60) / 100,
        (totalTicks * 75) / 100,
        (totalTicks * 90) / 100,
    )
    private val reelDecelCuts = listOf(5, 4, 3)
    private val reelDecelRenderTicks: List<Set<Int>> = reels.indices.map { reelIndex ->
        val stopTick = reelStopTicks[reelIndex]
        val decelStart = (stopTick * 70) / 100
        slowSpin(stopTick - 1, reelDecelCuts[reelIndex]).filter { it in decelStart until stopTick }.toSet()
    }
    private val revealTailEffective = revealTailTicks.coerceIn(1, (totalTicks / 2).coerceAtLeast(1))
    private val revealStart = (totalTicks - revealTailEffective).coerceAtLeast(0)

    override fun shouldRenderOnTick(tick: Int): Boolean {
        if (tick >= totalTicks) return false
        if (tick >= revealStart) return tick == totalTicks - 1
        return reels.indices.any { reelIndex ->
            val stopTick = reelStopTicks[reelIndex]
            when {
                tick >= stopTick -> false
                tick in reelDecelRenderTicks[reelIndex] -> true
                else -> fastPhaseRender(tick, tickInterval)
            }
        }
    }

    override fun frame(tick: Int, prize: Prize, decoys: List<Prize>): AnimationFrame {
        val pool = if (decoys.isEmpty()) listOf(prize) else decoys
        val items = mutableMapOf<Int, ItemStack>()
        var lockSound: String? = null
        reels.forEachIndexed { index, slot ->
            val stopTick = reelStopTicks[index]
            val isLocked = tick >= stopTick || tick >= totalTicks
            val alreadyShownLocked = (0 until tick).any { prior ->
                prior >= stopTick && shouldRenderOnTick(prior)
            }
            if (isLocked && !alreadyShownLocked && tick < totalTicks) {
                lockSound = LOCK_SOUND
            }
            val source = if (isLocked) {
                prize
            } else {
                val reelShift = (renderIndexAt(tick, ::shouldRenderOnTick) * spinSpeedMultiplier.coerceAtLeast(0.1)).toInt()
                pool[(reelShift + index * 2).mod(pool.size)]
            }
            items[slot] = AnimationItemFactory.fromPrize(source)
        }
        return AnimationFrame(items, sound = lockSound)
    }

    private companion object {
        const val LOCK_SOUND = "BLOCK_NOTE_BLOCK_PLING"
    }
}
