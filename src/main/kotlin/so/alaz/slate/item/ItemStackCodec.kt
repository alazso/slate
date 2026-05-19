package so.alaz.slate.item

import java.util.Base64
import org.bukkit.inventory.ItemStack

object ItemStackCodec {
    fun serialize(item: ItemStack): String? =
        runCatching {
            Base64.getEncoder().encodeToString(item.serializeAsBytes())
        }.getOrNull()

    fun deserialize(serialized: String): ItemStack? =
        runCatching {
            ItemStack.deserializeBytes(Base64.getDecoder().decode(serialized))
        }.getOrNull()
}
