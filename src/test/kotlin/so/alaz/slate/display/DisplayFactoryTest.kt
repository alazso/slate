package so.alaz.slate.display

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.CrateType
import so.alaz.slate.crate.KeyDefinition
import so.alaz.slate.crate.KeyProvider
import so.alaz.slate.crate.Prize
import so.alaz.slate.item.ItemFactory

class DisplayFactoryTest {
    @Test
    fun `display item uses provider-backed key visual when available`() {
        val world = io.mockk.mockk<World>()
        val location = Location(world, 0.0, 64.0, 0.0)
        val crate = CrateDefinition(
            id = "visual",
            name = "Visual",
            type = CrateType.Unknown,
            keyDefinition = KeyDefinition(
                name = "Key",
                lore = emptyList(),
                material = Material.TRIPWIRE_HOOK,
                customModelData = emptyList(),
                customItemId = null,
                provider = KeyProvider.ItemsAdder,
                providerItemId = "itemsadder:keys/visual",
                glowing = false,
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
            display = CrateDisplayConfig(
                enabled = true,
                item = CrateItemDisplayConfig(material = Material.CHEST),
            ),
        )

        val visualItem = mockk<org.bukkit.inventory.ItemStack>(relaxed = true)
        every { visualItem.type } returns Material.NETHER_STAR
        mockkObject(ItemFactory)
        try {
            every { ItemFactory.buildVisual(any(), Material.CHEST) } returns visualItem

            val spec = DisplayFactory().crateDisplay(crate, location)

            assertNotNull(spec)
            val itemElement = spec!!.elements.first { it.type == DisplayElementType.ITEM }
            assertEquals(Material.NETHER_STAR, itemElement.itemStack?.type)
        } finally {
            unmockkObject(ItemFactory)
        }
    }
}
