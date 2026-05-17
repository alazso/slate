package so.alaz.slate.key

import java.util.UUID
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import so.alaz.slate.crate.CrateDefinition

interface KeyService {
    suspend fun countKeys(player: Player, crate: CrateDefinition, type: KeyType): Int
    suspend fun hasKey(player: Player, crate: CrateDefinition, type: KeyType): Boolean
    suspend fun consumeKey(player: Player, crate: CrateDefinition, type: KeyType): Boolean
    suspend fun givePhysicalKeys(player: Player, crate: CrateDefinition, amount: Int): Int
    suspend fun takePhysicalKeys(player: Player, crate: CrateDefinition, amount: Int): Int
    suspend fun resetPhysicalKeys(player: Player, crate: CrateDefinition): Int
    suspend fun getVirtualKeys(uuid: UUID, crateId: String): Int
    suspend fun giveVirtualKeys(uuid: UUID, crateId: String, amount: Int, silent: Boolean = false)
    suspend fun takeVirtualKeys(uuid: UUID, crateId: String, amount: Int)
    suspend fun setVirtualKeys(uuid: UUID, crateId: String, amount: Int)
    fun isKey(item: ItemStack): Boolean
    fun getCrateForKey(item: ItemStack): CrateDefinition?
}
