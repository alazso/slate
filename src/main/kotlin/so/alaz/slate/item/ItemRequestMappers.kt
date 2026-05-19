package so.alaz.slate.item

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.KeyProvider
import so.alaz.slate.crate.PrizeItem

fun PrizeItem.toItemBuildRequest(): ItemBuildRequest =
    ItemBuildRequest(
        provider = adapter,
        material = material,
        itemId = itemId,
        serialized = serialized,
        amount = amount,
        overlay = ItemOverlay(
            customModelData = customModelData,
            pdc = pdc,
        ),
    )

fun CrateDefinition.toKeyVisualBuildRequest(): ItemBuildRequest {
    val key = keyDefinition
    val providerId = if (key.provider == KeyProvider.Vanilla) "vanilla" else key.provider.providerId
    return ItemBuildRequest(
        provider = providerId,
        material = key.material.name,
        itemId = key.providerItemId,
        amount = 1,
        overlay = ItemOverlay(
            name = key.name.takeIf { it.isNotBlank() },
            lore = key.lore.takeIf { it.isNotEmpty() },
            glint = key.glowing,
            customModelData = key.customModelData,
            itemModel = key.customItemId,
        ),
    )
}

fun CrateDefinition.toDisplayVisualBuildRequest(displayMaterial: Material): ItemBuildRequest {
    val key = keyDefinition
    return ItemBuildRequest(
        provider = "vanilla",
        material = displayMaterial.name,
        amount = 1,
        overlay = ItemOverlay(
            name = key.name.takeIf { it.isNotBlank() },
            lore = key.lore.takeIf { it.isNotEmpty() },
            glint = key.glowing,
            customModelData = key.customModelData,
            itemModel = key.customItemId,
        ),
    )
}

fun ItemFactory.visualStackForPrizeItem(
    item: PrizeItem,
    fallbackMaterial: Material = Material.CHEST,
): ItemStack = buildVisual(item.toItemBuildRequest(), fallbackMaterial)

fun ItemFactory.visualStackForCrateKey(
    crate: CrateDefinition,
    fallbackMaterial: Material = crate.keyDefinition.material,
): ItemStack = buildVisual(crate.toKeyVisualBuildRequest(), fallbackMaterial)

fun ItemFactory.visualStackForCrateDisplay(
    crate: CrateDefinition,
    displayMaterial: Material,
): ItemStack = buildVisual(
    crate.toDisplayVisualBuildRequest(displayMaterial),
    fallbackMaterial = displayMaterial,
)
