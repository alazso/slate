package so.alaz.slate.key.providers

import org.bukkit.inventory.ItemStack
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.KeyProvider
import so.alaz.slate.item.CraftEngineItemProvider
import so.alaz.slate.item.ItemBuildRequest
import so.alaz.slate.item.ItemFactory
import so.alaz.slate.item.ItemOverlay

object CraftEnginePhysicalKeyProvider : PhysicalKeyProvider {
    override val type: KeyProvider = KeyProvider.CraftEngine

    override fun isAvailable(): Boolean = CraftEngineItemProvider.isAvailable()

    override fun mint(crate: CrateDefinition, amount: Int): ItemStack? =
        ItemFactory.build(
            ItemBuildRequest(
                provider = "craftengine",
                itemId = crate.keyDefinition.providerItemId,
                amount = amount,
                overlay = ItemOverlay(
                    name = crate.keyDefinition.name.takeIf { it.isNotBlank() },
                    lore = crate.keyDefinition.lore.takeIf { it.isNotEmpty() },
                    glint = crate.keyDefinition.glowing,
                    customModelData = crate.keyDefinition.customModelData,
                    itemModel = crate.keyDefinition.customItemId,
                ),
            ),
        )
}
