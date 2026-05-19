package so.alaz.slate.key

import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import so.alaz.slate.SlateKeys
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.CratePlaceholderResolver
import so.alaz.slate.crate.KeyProvider
import so.alaz.slate.util.ComponentUtil
import so.alaz.slate.util.ItemUtil

object PhysicalKeyUtil {
    fun markAsKey(item: ItemStack, crateId: String): ItemStack {
        ItemUtil.setData(item, SlateKeys.KEY_IS_SLATE_KEY, PersistentDataType.BYTE, 1)
        ItemUtil.setData(item, SlateKeys.KEY_CRATE_ID, PersistentDataType.STRING, crateId)
        return item
    }

    fun markAsKey(item: ItemStack, crate: CrateDefinition): ItemStack {
        stampSlateIdentity(item, crate)
        return item
    }

    fun isSlateKey(item: ItemStack): Boolean =
        ItemUtil.getData(item, SlateKeys.KEY_IS_SLATE_KEY, PersistentDataType.BYTE)?.toInt() == 1

    fun crateId(item: ItemStack): String? =
        ItemUtil.getData(item, SlateKeys.KEY_CRATE_ID, PersistentDataType.STRING)

    fun matchesKeyDefinition(item: ItemStack, crate: CrateDefinition): Boolean {
        if (!isSlateKey(item)) return false
        if (crateId(item) != crate.id) return false
        if (providerType(item) != crate.keyDefinition.provider) return false
        if (providerItemId(item) != crate.keyDefinition.providerItemId?.trim().orEmpty().ifBlank { null }) return false

        val meta = item.itemMeta ?: return false

        if (crate.keyDefinition.provider == KeyProvider.Vanilla) {
            if (item.type != crate.keyDefinition.material) return false
            val expectedName = ComponentUtil.parse(CratePlaceholderResolver.render(crate.keyDefinition.name, crate))
            if (meta.displayName() != expectedName) return false

            val expectedLore = CratePlaceholderResolver.renderLore(crate.keyDefinition.lore, crate).map(ComponentUtil::parse)
            if ((meta.lore() ?: emptyList()) != expectedLore) return false

            val actualModelFloats = readCustomModelData(item)
            if (!matchesModelData(actualModelFloats, crate.keyDefinition.customModelData)) return false

            val expectedItemModel = crate.keyDefinition.customItemId?.trim().orEmpty().ifBlank { null }
            val actualItemModel = getCustomItemId(item, meta)
            if (actualItemModel != expectedItemModel) return false
        } else {
            val expectedItemModel = crate.keyDefinition.customItemId?.trim().orEmpty().ifBlank { null }
            if (expectedItemModel != null && getCustomItemId(item, meta) != expectedItemModel) return false
            if (crate.keyDefinition.customModelData.isNotEmpty() && !matchesModelData(readCustomModelData(item), crate.keyDefinition.customModelData)) return false
            if (
                crate.keyDefinition.name.isNotBlank() &&
                meta.displayName() != ComponentUtil.parse(CratePlaceholderResolver.render(crate.keyDefinition.name, crate))
            ) {
                return false
            }
            if (
                crate.keyDefinition.lore.isNotEmpty() &&
                (meta.lore() ?: emptyList()) != CratePlaceholderResolver.renderLore(crate.keyDefinition.lore, crate).map(ComponentUtil::parse)
            ) {
                return false
            }
        }

        return true
    }

    fun stampSlateIdentity(item: ItemStack, crate: CrateDefinition): ItemStack {
        item.editMeta { meta ->
            val pdc = meta.persistentDataContainer
            pdc.set(SlateKeys.KEY_IS_SLATE_KEY, PersistentDataType.BYTE, 1)
            pdc.set(SlateKeys.KEY_CRATE_ID, PersistentDataType.STRING, crate.id)
            pdc.set(SlateKeys.KEY_PROVIDER_TYPE, PersistentDataType.STRING, crate.keyDefinition.provider.providerId)
            val providerItemId = crate.keyDefinition.providerItemId?.trim().orEmpty().ifBlank { null }
            if (providerItemId != null) {
                pdc.set(SlateKeys.KEY_PROVIDER_ITEM_ID, PersistentDataType.STRING, providerItemId)
            } else {
                pdc.remove(SlateKeys.KEY_PROVIDER_ITEM_ID)
            }
            val customItemId = crate.keyDefinition.customItemId?.trim().orEmpty().ifBlank { null }
            if (crate.keyDefinition.provider == KeyProvider.Vanilla && customItemId != null) {
                pdc.set(SlateKeys.KEY_CUSTOM_ITEM_ID, PersistentDataType.STRING, customItemId)
            } else {
                pdc.remove(SlateKeys.KEY_CUSTOM_ITEM_ID)
            }
        }
        return item
    }

    private fun getCustomItemId(item: ItemStack, meta: ItemMeta): String? =
        getItemModel(meta) ?: ItemUtil.getData(item, SlateKeys.KEY_CUSTOM_ITEM_ID, PersistentDataType.STRING)

    fun providerType(item: ItemStack): KeyProvider? =
        KeyProvider.parse(ItemUtil.getData(item, SlateKeys.KEY_PROVIDER_TYPE, PersistentDataType.STRING))

    fun providerItemId(item: ItemStack): String? =
        ItemUtil.getData(item, SlateKeys.KEY_PROVIDER_ITEM_ID, PersistentDataType.STRING)?.trim().orEmpty().ifBlank { null }

    fun customModelData(item: ItemStack): List<Float> = readCustomModelData(item)

    private fun readCustomModelData(item: ItemStack): List<Float> {
        runCatching {
            val dataComponentTypes = Class.forName("io.papermc.paper.datacomponent.DataComponentTypes")
            val customModelDataType = dataComponentTypes.getField("CUSTOM_MODEL_DATA").get(null)
            val getDataMethod = item.javaClass.methods.firstOrNull { it.name == "getData" && it.parameterCount == 1 }
                ?: return@runCatching null
            val customModelData = getDataMethod.invoke(item, customModelDataType) ?: return@runCatching null
            @Suppress("UNCHECKED_CAST")
            return (customModelData.javaClass.getMethod("floats").invoke(customModelData) as? List<Float>) ?: emptyList()
        }.getOrNull()?.let { return it }

        val legacy = runCatching {
            val meta = item.itemMeta ?: return@runCatching null
            val hasMethod = meta.javaClass.methods.firstOrNull {
                it.name == "hasCustomModelData" && it.parameterCount == 0
            }
            if (hasMethod != null) {
                val has = hasMethod.invoke(meta) as? Boolean ?: false
                if (!has) return@runCatching null
            }
            val getMethod = meta.javaClass.methods.firstOrNull {
                it.name == "getCustomModelData" && it.parameterCount == 0
            } ?: return@runCatching null
            val value = getMethod.invoke(meta)
            when (value) {
                is Number -> value.toFloat()
                else -> null
            }
        }.getOrNull()
        return if (legacy != null) listOf(legacy.toFloat()) else emptyList()
    }

    private fun matchesModelData(
        actual: List<Float>,
        expected: List<Float>,
    ): Boolean {
        if (expected.isEmpty()) return actual.isEmpty()
        if (actual.isEmpty()) {
            // Some Paper versions/plugins do not expose custom model data through the API consistently.
            // We still accept the key when Slate identity + crate/provider checks already matched.
            return true
        }
        if (actual.size != expected.size) {
            // Accept single-value equivalence when representations differ (legacy/custom component conversion).
            if (actual.size == 1 && expected.size == 1) {
                return nearlyEqual(actual.first(), expected.first())
            }
            return false
        }
        return actual.zip(expected).all { (a, e) -> nearlyEqual(a, e) }
    }

    private fun nearlyEqual(a: Float, b: Float): Boolean =
        kotlin.math.abs(a - b) < 0.0001f

    private fun getItemModel(meta: ItemMeta): String? =
        runCatching {
            val method = meta.javaClass.methods.firstOrNull { it.name == "getItemModel" && it.parameterCount == 0 } ?: return@runCatching null
            val value = method.invoke(meta)
            when (value) {
                is NamespacedKey -> value.asString()
                else -> value?.toString()
            }
        }.getOrNull()?.trim().orEmpty().ifBlank { null }
}
