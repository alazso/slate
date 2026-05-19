package so.alaz.slate.gui.preview

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.CrateType
import so.alaz.slate.crate.KeyDefinition
import so.alaz.slate.crate.KeyProvider
import so.alaz.slate.crate.Prize
import so.alaz.slate.crate.PrizeItem
import so.alaz.slate.item.ItemFactory

class PreviewItemBuilderTest {
    @Test
    fun `preview uses provider-backed visual item when available`() {
        val crate = crate()
        val prize = prize()
        val visualItem = mockk<org.bukkit.inventory.ItemStack>(relaxed = true)
        every { visualItem.type } returns Material.DIAMOND_BLOCK
        mockkObject(ItemFactory)
        try {
            every { ItemFactory.buildVisual(any(), Material.CHEST) } returns visualItem

            val icon = PreviewItemBuilder.build(crate, prize)

            assertEquals(Material.DIAMOND_BLOCK, icon.type)
        } finally {
            unmockkObject(ItemFactory)
        }
    }

    private fun crate(): CrateDefinition =
        CrateDefinition(
            id = "visual",
            name = "Visual",
            type = CrateType.Unknown,
            keyDefinition = KeyDefinition(
                name = "Key",
                lore = emptyList(),
                material = Material.TRIPWIRE_HOOK,
                customModelData = emptyList(),
                customItemId = null,
                provider = KeyProvider.Vanilla,
                providerItemId = null,
                glowing = false,
            ),
            prizes = listOf(prize()),
        )

    private fun prize(): Prize =
        Prize(
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
}
