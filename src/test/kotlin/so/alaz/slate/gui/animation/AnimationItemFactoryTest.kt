package so.alaz.slate.gui.animation

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import so.alaz.slate.crate.Prize
import so.alaz.slate.crate.PrizeItem
import so.alaz.slate.item.ItemFactory

class AnimationItemFactoryTest {
    @Test
    fun `animation icons use provider-backed visual item when available`() {
        val prize = Prize(
            id = "p1",
            name = "Prize One",
            tier = null,
            weight = 1,
            broadcast = false,
            broadcastMessage = "",
            items = listOf(
                PrizeItem(
                    adapter = "itemsadder",
                    itemId = "itemsadder:cosmetics/astral_hook",
                ),
            ),
            commands = emptyList(),
            messages = emptyList(),
        )

        val visualItem = mockk<org.bukkit.inventory.ItemStack>(relaxed = true)
        every { visualItem.type } returns Material.EMERALD_BLOCK
        mockkObject(ItemFactory)
        try {
            every { ItemFactory.buildVisual(any(), Material.CHEST) } returns visualItem

            val icon = AnimationItemFactory.fromPrize(prize)

            assertEquals(Material.EMERALD_BLOCK, icon.type)
        } finally {
            unmockkObject(ItemFactory)
        }
    }
}
