package so.alaz.slate.key.providers

import org.bukkit.inventory.ItemStack
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.KeyProvider

interface PhysicalKeyProvider {
    val type: KeyProvider

    fun isAvailable(): Boolean

    fun mint(crate: CrateDefinition, amount: Int): ItemStack?
}
