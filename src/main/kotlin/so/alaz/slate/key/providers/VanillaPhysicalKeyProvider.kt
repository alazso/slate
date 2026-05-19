package so.alaz.slate.key.providers

import org.bukkit.inventory.ItemStack
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.CratePlaceholderResolver
import so.alaz.slate.crate.KeyProvider
import so.alaz.slate.item.ItemBuildRequest
import so.alaz.slate.item.ItemFactory
import so.alaz.slate.item.ItemOverlay

object VanillaPhysicalKeyProvider : PhysicalKeyProvider {
    override val type: KeyProvider = KeyProvider.Vanilla

    override fun isAvailable(): Boolean = true

    override fun mint(crate: CrateDefinition, amount: Int): ItemStack? =
        ItemFactory.build(
            ItemBuildRequest(
                provider = "vanilla",
                material = crate.keyDefinition.material.name,
                amount = amount,
                overlay = ItemOverlay(
                    name = CratePlaceholderResolver.render(crate.keyDefinition.name, crate),
                    lore = CratePlaceholderResolver.renderLore(crate.keyDefinition.lore, crate),
                    glint = crate.keyDefinition.glowing,
                    customModelData = crate.keyDefinition.customModelData,
                    itemModel = crate.keyDefinition.customItemId,
                ),
            ),
        )
}
