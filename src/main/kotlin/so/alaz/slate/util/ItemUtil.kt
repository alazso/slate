package so.alaz.slate.util

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.CustomModelData
import java.util.Base64
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import so.alaz.slate.crate.PrizeItem
import so.alaz.slate.item.ItemBuildRequest
import so.alaz.slate.item.ItemFactory
import so.alaz.slate.item.ItemOverlay

object ItemUtil {
    fun serializeItemStack(item: ItemStack): String? =
        runCatching {
            Base64.getEncoder().encodeToString(item.serializeAsBytes())
        }.getOrNull()

    fun deserializeItemStack(serialized: String): ItemStack? =
        runCatching {
            ItemStack.deserializeBytes(Base64.getDecoder().decode(serialized))
        }.getOrNull()

    fun <T : Any> setData(
        item: ItemStack,
        key: NamespacedKey,
        type: PersistentDataType<*, T>,
        value: T,
    ): ItemStack {
        item.editMeta { meta -> meta.persistentDataContainer.set(key, type, value) }
        return item
    }

    fun <T : Any> getData(
        item: ItemStack,
        key: NamespacedKey,
        type: PersistentDataType<*, T>,
    ): T? = item.itemMeta?.persistentDataContainer?.get(key, type)

    fun hasData(item: ItemStack, key: NamespacedKey): Boolean =
        item.itemMeta?.persistentDataContainer?.has(key) == true

    fun buildItem(material: Material, block: ItemMeta.() -> Unit): ItemStack {
        val stack = ItemStack(material)
        stack.editMeta(block)
        return stack
    }

    fun expandPrizeItem(item: PrizeItem): List<ItemStack>? {
        val baseStack = ItemFactory.build(item.toBuildRequest()) ?: return null
        val maxStack = baseStack.type.maxStackSize.coerceAtLeast(1)
        val stacks = mutableListOf<ItemStack>()
        var remaining = baseStack.amount
        while (remaining > 0) {
            val take = minOf(remaining, maxStack)
            val clone = baseStack.clone()
            clone.amount = take
            stacks += clone
            remaining -= take
        }
        return stacks
    }

    fun parsePrizeItem(item: PrizeItem): Pair<Material, Int>? {
        val raw = item.material.trim()
        if (raw.isEmpty()) return null

        val amountFromSuffix = parseAmountFromSuffix(raw)
        val materialToken = amountFromSuffix?.first ?: raw
        val amount = amountFromSuffix?.second ?: item.amount
        if (amount <= 0) return null

        val material = Material.matchMaterial(materialToken) ?: return null
        return material to amount
    }

    private fun parseAmountFromSuffix(raw: String): Pair<String, Int>? {
        val starOrX = Regex("""^\s*(.+?)\s*[*xX]\s*(\d+)\s*$""").matchEntire(raw)
        if (starOrX != null) {
            return starOrX.groupValues[1].trim() to starOrX.groupValues[2].toInt()
        }

        val colon = Regex("""^\s*(.+):(\d+)\s*$""").matchEntire(raw)
        if (colon != null) {
            return colon.groupValues[1].trim() to colon.groupValues[2].toInt()
        }

        return null
    }

    fun applyPrizeItemMetadata(item: ItemStack, prizeItem: PrizeItem): ItemStack {
        return applyItemOverlay(item, prizeItem.toOverlay())
    }

    fun applyItemOverlay(item: ItemStack, overlay: ItemOverlay): ItemStack {
        if (
            overlay.name != null ||
            overlay.lore != null ||
            overlay.itemModel != null ||
            overlay.glint != null ||
            overlay.pdc.isNotEmpty()
        ) {
            item.editMeta { meta ->
                overlay.name?.let { meta.displayName(ComponentUtil.parse(it)) }
                overlay.lore?.let { meta.lore(it.map(ComponentUtil::parse)) }
                overlay.itemModel?.let { setItemModel(meta, it) }
                overlay.glint?.let { setGlintOverride(meta, it) }
                overlay.pdc.forEach { (key, value) ->
                    val namespaced = NamespacedKey.fromString(key) ?: return@forEach
                    meta.persistentDataContainer.set(namespaced, PersistentDataType.STRING, value)
                }
            }
        }
        applyCustomModelData(item, overlay.customModelData)
        return item
    }

    fun applyCustomModelData(item: ItemStack, floats: List<Float>): ItemStack {
        if (floats.isEmpty()) return item
        runCatching {
            val modelData = CustomModelData.customModelData()
                .addFloats(floats)
                .build()
            item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, modelData)
        }.onFailure {
            item.editMeta { meta ->
                meta.setCustomModelData(floats.first().toInt())
            }
        }
        return item
    }

    fun applyCustomModelData(item: ItemStack, legacyInt: Int): ItemStack =
        applyCustomModelData(item, listOf(legacyInt.toFloat()))

    fun setItemModel(meta: ItemMeta, itemModel: String?) {
        val value = itemModel?.trim().orEmpty().ifBlank { null }
        runCatching {
            val method = meta.javaClass.methods.firstOrNull { it.name == "setItemModel" && it.parameterCount == 1 } ?: return
            if (value == null) {
                method.invoke(meta, null)
                return
            }
            method.invoke(meta, NamespacedKey.fromString(value))
        }
    }

    fun setGlintOverride(meta: ItemMeta, glowing: Boolean) {
        runCatching {
            meta.setEnchantmentGlintOverride(glowing)
        }.onFailure {
            if (glowing) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true)
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
            }
        }
    }

    fun PrizeItem.toBuildRequest(): ItemBuildRequest =
        ItemBuildRequest(
            provider = adapter,
            material = material,
            itemId = itemId,
            serialized = serialized,
            amount = amount,
            overlay = toOverlay(),
        )

    private fun PrizeItem.toOverlay(): ItemOverlay =
        ItemOverlay(
            customModelData = customModelData,
            pdc = pdc,
        )
}
