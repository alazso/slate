package so.alaz.slate.key.providers

import org.bukkit.inventory.ItemStack
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.CratePlaceholderResolver
import so.alaz.slate.crate.KeyProvider
import so.alaz.slate.item.ItemBuildRequest
import so.alaz.slate.item.ItemFactory
import so.alaz.slate.item.ItemOverlay
import so.alaz.slate.item.ItemsAdderItemProvider

object ItemsAdderPhysicalKeyProvider : PhysicalKeyProvider {
    override val type: KeyProvider = KeyProvider.ItemsAdder

    override fun isAvailable(): Boolean = ItemsAdderItemProvider.isAvailable()

    override fun mint(crate: CrateDefinition, amount: Int): ItemStack? =
        ItemFactory.build(
            ItemBuildRequest(
                provider = "itemsadder",
                itemId = crate.keyDefinition.providerItemId,
                amount = amount,
                overlay = ItemOverlay(
                    name = CratePlaceholderResolver.render(crate.keyDefinition.name, crate).takeIf { it.isNotBlank() },
                    lore = CratePlaceholderResolver.renderLore(crate.keyDefinition.lore, crate).takeIf { it.isNotEmpty() },
                    glint = crate.keyDefinition.glowing,
                    customModelData = crate.keyDefinition.customModelData,
                    itemModel = crate.keyDefinition.customItemId,
                ),
            ),
        )
}
