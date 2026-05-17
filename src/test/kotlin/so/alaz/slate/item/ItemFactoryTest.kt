package so.alaz.slate.item

import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ItemFactoryTest {
    @Test
    fun `registry resolves vanilla provider by default`() {
        assertSame(VanillaItemProvider, ItemProviderRegistry.resolve(ItemBuildRequest(material = "DIAMOND*3")))
    }

    @Test
    fun `registry resolves custom providers from item id prefixes`() {
        assertSame(NexoItemProvider, ItemProviderRegistry.resolve(ItemBuildRequest(itemId = "nexo:key")))
        assertSame(CraftEngineItemProvider, ItemProviderRegistry.resolve(ItemBuildRequest(itemId = "craftengine:key")))
        assertSame(OraxenItemProvider, ItemProviderRegistry.resolve(ItemBuildRequest(itemId = "oraxen:key")))
        assertSame(ItemsAdderItemProvider, ItemProviderRegistry.resolve(ItemBuildRequest(itemId = "itemsadder:key")))
        assertSame(MMOItemsItemProvider, ItemProviderRegistry.resolve(ItemBuildRequest(itemId = "mmoitems:key")))
    }
}
