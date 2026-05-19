package so.alaz.slate.util

import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import so.alaz.slate.crate.PrizeItem
import so.alaz.slate.item.ItemFactory
import so.alaz.slate.item.toItemBuildRequest

object ItemUtil {
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

    fun expandPrizeItem(item: PrizeItem): List<ItemStack>? {
        val baseStack = ItemFactory.build(item.toItemBuildRequest()) ?: return null
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
}
