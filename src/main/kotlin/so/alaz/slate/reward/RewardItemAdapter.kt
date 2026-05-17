package so.alaz.slate.reward

import org.bukkit.inventory.ItemStack
import so.alaz.slate.crate.PrizeItem
import so.alaz.slate.item.ItemBuildRequest
import so.alaz.slate.item.ItemFactory
import so.alaz.slate.item.ItemOverlay
import so.alaz.slate.item.ItemProviderRegistry

interface RewardItemAdapter {
    val id: String
    fun isAvailable(): Boolean
    fun build(item: PrizeItem): ItemStack?
}

object RewardItemAdapterRegistry {
    private val adapters: Map<String, RewardItemAdapter> =
        listOf("vanilla", "nexo", "craftengine", "oraxen", "itemsadder", "mmoitems")
            .associateWith(::FactoryRewardItemAdapter)

    fun resolve(item: PrizeItem): RewardItemAdapter {
        val explicit = item.adapter?.lowercase()
        if (explicit != null) {
            return adapters[explicit] ?: adapters.getValue("vanilla")
        }
        return when {
            !item.itemId.isNullOrBlank() && item.itemId.startsWith("nexo:", true) -> adapters.getValue("nexo")
            !item.itemId.isNullOrBlank() && item.itemId.startsWith("craftengine:", true) -> adapters.getValue("craftengine")
            !item.itemId.isNullOrBlank() && item.itemId.startsWith("oraxen:", true) -> adapters.getValue("oraxen")
            !item.itemId.isNullOrBlank() && item.itemId.startsWith("itemsadder:", true) -> adapters.getValue("itemsadder")
            !item.itemId.isNullOrBlank() && item.itemId.startsWith("mmoitems:", true) -> adapters.getValue("mmoitems")
            else -> adapters.getValue("vanilla")
        }
    }
}

private class FactoryRewardItemAdapter(
    override val id: String,
) : RewardItemAdapter {
    override fun isAvailable(): Boolean =
        ItemProviderRegistry.provider(id)?.isAvailable() == true

    override fun build(item: PrizeItem): ItemStack? =
        ItemFactory.build(
            ItemBuildRequest(
                provider = id,
                material = item.material,
                itemId = item.itemId,
                amount = item.amount,
                overlay = ItemOverlay(
                    customModelData = item.customModelData,
                    pdc = item.pdc,
                ),
            ),
        )
}
