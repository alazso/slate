package so.alaz.slate.util

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
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
import so.alaz.slate.item.ItemBuildRequest
import so.alaz.slate.item.ItemFactory
import so.alaz.slate.item.visualStackForCrateKey
import so.alaz.slate.item.visualStackForPrizeItem

class ItemVisualResolutionTest {
    @Test
    fun `visual prize resolution uses provider aware request`() {
        val request = slot<ItemBuildRequest>()
        val visualItem = mockk<org.bukkit.inventory.ItemStack>(relaxed = true)
        every { visualItem.type } returns Material.DIAMOND
        mockkObject(ItemFactory)
        try {
            every { ItemFactory.buildVisual(capture(request), Material.CHEST) } returns visualItem
            val stack = ItemFactory.visualStackForPrizeItem(
                PrizeItem(
                    adapter = "itemsadder",
                    itemId = "itemsadder:cosmetics/astral_hook",
                    amount = 2,
                ),
            )

            assertEquals(Material.DIAMOND, stack.type)
            assertEquals("itemsadder", request.captured.provider)
            assertEquals("itemsadder:cosmetics/astral_hook", request.captured.itemId)
        } finally {
            unmockkObject(ItemFactory)
        }
    }

    @Test
    fun `visual key resolution uses key provider id`() {
        val request = slot<ItemBuildRequest>()
        val visualItem = mockk<org.bukkit.inventory.ItemStack>(relaxed = true)
        every { visualItem.type } returns Material.EMERALD
        mockkObject(ItemFactory)
        try {
            every { ItemFactory.buildVisual(capture(request), Material.CHEST) } returns visualItem
            val stack = ItemFactory.visualStackForCrateKey(crateWithProvider(KeyProvider.ItemsAdder), Material.CHEST)

            assertEquals(Material.EMERALD, stack.type)
            assertEquals("itemsadder", request.captured.provider)
            assertEquals("itemsadder:keys/visual", request.captured.itemId)
        } finally {
            unmockkObject(ItemFactory)
        }
    }

    private fun crateWithProvider(provider: KeyProvider): CrateDefinition =
        CrateDefinition(
            id = "visual",
            name = "Visual",
            type = CrateType.Unknown,
            keyDefinition = KeyDefinition(
                name = "<yellow>Visual Key</yellow>",
                lore = listOf("<gray>Visual</gray>"),
                material = Material.TRIPWIRE_HOOK,
                customModelData = emptyList(),
                customItemId = null,
                provider = provider,
                providerItemId = "itemsadder:keys/visual",
                glowing = true,
            ),
            prizes = listOf(
                Prize(
                    id = "p1",
                    name = "Prize",
                    tier = null,
                    weight = 1,
                    broadcast = false,
                    broadcastMessage = "",
                    items = emptyList(),
                    commands = emptyList(),
                    messages = emptyList(),
                ),
            ),
        )
}
