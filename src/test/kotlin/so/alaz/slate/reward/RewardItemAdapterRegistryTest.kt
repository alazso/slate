package so.alaz.slate.reward

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import so.alaz.slate.crate.PrizeItem

class RewardItemAdapterRegistryTest {
    @Test
    fun `reward registry resolves custom providers through shared ids`() {
        assertEquals("nexo", RewardItemAdapterRegistry.resolve(PrizeItem(itemId = "nexo:key")).id)
        assertEquals("craftengine", RewardItemAdapterRegistry.resolve(PrizeItem(itemId = "craftengine:key")).id)
        assertEquals("oraxen", RewardItemAdapterRegistry.resolve(PrizeItem(itemId = "oraxen:key")).id)
        assertEquals("itemsadder", RewardItemAdapterRegistry.resolve(PrizeItem(itemId = "itemsadder:key")).id)
        assertEquals("mmoitems", RewardItemAdapterRegistry.resolve(PrizeItem(itemId = "mmoitems:key")).id)
    }

    @Test
    fun `unknown reward adapter falls back to vanilla`() {
        assertEquals("vanilla", RewardItemAdapterRegistry.resolve(PrizeItem(adapter = "missing", material = "DIAMOND")).id)
    }
}
