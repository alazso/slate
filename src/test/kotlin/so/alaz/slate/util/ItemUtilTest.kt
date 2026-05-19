package so.alaz.slate.util

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.function.Consumer
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import so.alaz.slate.item.ItemOverlay
import so.alaz.slate.item.ItemOverlaySupport
import so.alaz.slate.item.ItemStackCodec

class ItemUtilTest {
    @Test
    fun `applyCustomModelData skips empty list without touching data components`() {
        val item = mockk<ItemStack>(relaxed = true)

        val result = ItemOverlaySupport.applyCustomModelData(item, emptyList())
        assertSame(item, result)
    }

    @Test
    fun `applyItemOverlay applies pdc metadata`() {
        val item = mockk<ItemStack>(relaxed = true)
        val meta = mockk<ItemMeta>(relaxed = true)
        val pdc = mockk<PersistentDataContainer>(relaxed = true)

        every { meta.persistentDataContainer } returns pdc
        every { item.editMeta(any<Consumer<ItemMeta>>()) } answers {
            firstArg<Consumer<ItemMeta>>().accept(meta)
            true
        }

        ItemOverlaySupport.applyItemOverlay(
            item,
            ItemOverlay(pdc = mapOf("minecraft:reward_id" to "rare")),
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
    fun `serialize itemstack encodes bytes`() {
        val original = mockk<ItemStack>()
        every { original.serializeAsBytes() } returns byteArrayOf(1, 2, 3)
        val encoded = ItemStackCodec.serialize(original)
        assertNotNull(encoded)
    }

    @Test
    fun `deserialize itemstack returns null for invalid payload`() {
        val decoded = ItemStackCodec.deserialize("not-valid-base64")
        assertEquals(null, decoded)
    }
}
