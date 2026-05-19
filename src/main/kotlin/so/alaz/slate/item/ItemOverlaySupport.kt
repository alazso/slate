package so.alaz.slate.item

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.CustomModelData
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import so.alaz.slate.util.ComponentUtil

object ItemOverlaySupport {
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
}
