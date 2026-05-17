package so.alaz.slate.gui.animation

import org.bukkit.inventory.ItemStack
import so.alaz.slate.crate.Prize

class AgencyPickAnimation(
    override val totalTicks: Int = 60,
    override val tickInterval: Int = 1,
) : AnimationTemplate {
    override val id: String = "agency-pick"

    override fun frame(tick: Int, prize: Prize, decoys: List<Prize>): AnimationFrame {
        val pool = if (decoys.isEmpty()) listOf(prize) else decoys
        val options = listOf(11, 13, 15)
        val items = mutableMapOf<Int, ItemStack>()
        options.forEachIndexed { index, slot ->
            val source = if (tick >= totalTicks - 5 && index == 1) prize else pool[(tick + index).mod(pool.size)]
            items[slot] = AnimationItemFactory.fromPrize(source)
        }
        return AnimationFrame(items)
    }
}

