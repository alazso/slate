package so.alaz.slate.util

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.function.Consumer
import org.bukkit.NamespacedKey
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import so.alaz.slate.crate.PrizeItem

class ItemUtilTest {
    @Test
    fun `applyCustomModelData skips empty list without touching data components`() {
        val item = mockk<ItemStack>(relaxed = true)

        val result = ItemUtil.applyCustomModelData(item, emptyList())
        assertSame(item, result)
    }

    @Test
    fun `parsePrizeItem supports inline amount notation`() {
        val parsed = ItemUtil.parsePrizeItem(PrizeItem(material = "DIAMOND*3", amount = 1))
        assertEquals("DIAMOND", parsed?.first?.name)
        assertEquals(3, parsed?.second)
    }

    @Test
    fun `applyPrizeItemMetadata applies prize pdc overlay`() {
        val item = mockk<ItemStack>(relaxed = true)
        val meta = mockk<ItemMeta>(relaxed = true)
        val pdc = mockk<PersistentDataContainer>(relaxed = true)

        every { meta.persistentDataContainer } returns pdc
        every { item.editMeta(any<Consumer<ItemMeta>>()) } answers {
            firstArg<Consumer<ItemMeta>>().accept(meta)
            true
        }

        ItemUtil.applyPrizeItemMetadata(
            item,
            PrizeItem(pdc = mapOf("minecraft:reward_id" to "rare")),
        )

        verify {
            pdc.set(
                match<NamespacedKey> { it.namespace == "minecraft" && it.key == "reward_id" },
                PersistentDataType.STRING,
                "rare",
            )
        }
    }

    @Test
    fun `serialized itemstack round trips`() {
        val original = ItemStack(Material.DIAMOND, 3)
        val encoded = ItemUtil.serializeItemStack(original)
        assertNotNull(encoded)
        val decoded = ItemUtil.deserializeItemStack(encoded!!)
        assertNotNull(decoded)
        assertEquals(original.type, decoded!!.type)
        assertEquals(3, decoded.amount)
    }
}
