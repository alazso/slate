package so.alaz.slate.reward

import org.bukkit.inventory.ItemStack

object ItemParityVerifier {
    fun equivalentPreviewAndDelivery(preview: ItemStack, delivered: ItemStack): Boolean {
        if (preview.type != delivered.type) return false
        val previewMeta = preview.itemMeta
        val deliveredMeta = delivered.itemMeta
        if (previewMeta?.displayName() != deliveredMeta?.displayName()) return false
        if (previewMeta?.lore() != deliveredMeta?.lore()) return false
        return previewMeta?.persistentDataContainer?.keys == deliveredMeta?.persistentDataContainer?.keys
    }
}
