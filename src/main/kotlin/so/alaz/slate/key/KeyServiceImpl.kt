package so.alaz.slate.key

import java.util.UUID
import kotlinx.coroutines.withContext
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.CrateManager
import so.alaz.slate.key.providers.PhysicalKeyProviderRegistry
import so.alaz.slate.scheduler.BukkitMainDispatcher
import so.alaz.slate.storage.StorageService

class KeyServiceImpl(
    private val crateManager: CrateManager,
    private val storageService: StorageService,
    private val mainDispatcher: BukkitMainDispatcher,
) : KeyService {
    override suspend fun countKeys(player: Player, crate: CrateDefinition, type: KeyType): Int = when (type) {
        KeyType.Physical -> withContext(mainDispatcher) {
            player.inventory.contents
                .filterNotNull()
                .filter { getCrateForKey(it)?.id == crate.id }
                .sumOf { it.amount }
        }
        KeyType.Virtual -> getVirtualKeys(player.uniqueId, crate.id)
    }

    override suspend fun hasKey(player: Player, crate: CrateDefinition, type: KeyType): Boolean =
        countKeys(player, crate, type) >= crate.requiredKeys

    override suspend fun consumeKey(player: Player, crate: CrateDefinition, type: KeyType): Boolean = when (type) {
        KeyType.Physical -> withContext(mainDispatcher) {
            val content = player.inventory.contents
            val matchingTotal = content
                .filterNotNull()
                .filter { getCrateForKey(it)?.id == crate.id }
                .sumOf { it.amount }
            if (matchingTotal < crate.requiredKeys) {
                return@withContext false
            }
            var required = crate.requiredKeys
            for (index in content.indices) {
                val item = content[index] ?: continue
                if (getCrateForKey(item)?.id != crate.id) continue
                if (required <= 0) break
                val consume = minOf(required, item.amount)
                item.subtract(consume)
                required -= consume
            }
            required <= 0
        }

        KeyType.Virtual -> storageService.tryConsumeVirtualKeys(player.uniqueId, crate.id, crate.requiredKeys)
    }

    override suspend fun givePhysicalKeys(player: Player, crate: CrateDefinition, amount: Int): Int = withContext(mainDispatcher) {
        if (amount <= 0) return@withContext 0
        val provider = PhysicalKeyProviderRegistry.providerFor(crate)
        if (!provider.isAvailable()) {
            throw IllegalStateException("Key provider '${crate.keyDefinition.provider}' is not available")
        }
        val sample = provider.mint(crate, 1)
            ?: throw IllegalStateException("Provider '${crate.keyDefinition.provider}' cannot mint key for crate '${crate.id}'")
        val maxStack = sample.type.maxStackSize.coerceAtLeast(1)
        var remaining = amount
        val stacks = mutableListOf<ItemStack>()
        while (remaining > 0) {
            val take = minOf(remaining, maxStack)
            val minted = provider.mint(crate, take)
                ?: throw IllegalStateException(
                    "Provider '${crate.keyDefinition.provider}' cannot mint item '${crate.keyDefinition.providerItemId}' for crate '${crate.id}'",
                )
            stacks += PhysicalKeyUtil.markAsKey(minted, crate)
            remaining -= take
        }
        val overflow = player.inventory.addItem(*stacks.toTypedArray())
        val overflowAmount = overflow.values.sumOf { it.amount }
        amount - overflowAmount
    }

    override suspend fun takePhysicalKeys(player: Player, crate: CrateDefinition, amount: Int): Int = withContext(mainDispatcher) {
        if (amount <= 0) return@withContext 0
        var toRemove = amount
        val contents = player.inventory.contents
        for (index in contents.indices) {
            val item = contents[index] ?: continue
            if (getCrateForKey(item)?.id != crate.id) continue
            if (toRemove <= 0) break
            val remove = minOf(toRemove, item.amount)
            item.subtract(remove)
            toRemove -= remove
        }
        amount - toRemove
    }

    override suspend fun resetPhysicalKeys(player: Player, crate: CrateDefinition): Int = withContext(mainDispatcher) {
        var removed = 0
        val contents = player.inventory.contents
        for (index in contents.indices) {
            val item = contents[index] ?: continue
            if (getCrateForKey(item)?.id != crate.id) continue
            removed += item.amount
            player.inventory.setItem(index, null)
        }
        removed
    }

    override suspend fun getVirtualKeys(uuid: UUID, crateId: String): Int =
        storageService.getVirtualKeys(uuid, crateId)

    override suspend fun giveVirtualKeys(uuid: UUID, crateId: String, amount: Int, silent: Boolean) {
        storageService.addVirtualKeys(uuid, crateId, amount)
        // Service layer stays transport-agnostic; callers decide whether and how to notify players.
    }

    override suspend fun takeVirtualKeys(uuid: UUID, crateId: String, amount: Int) {
        val current = getVirtualKeys(uuid, crateId)
        storageService.setVirtualKeys(uuid, crateId, (current - amount).coerceAtLeast(0))
    }

    override suspend fun setVirtualKeys(uuid: UUID, crateId: String, amount: Int) {
        storageService.setVirtualKeys(uuid, crateId, amount.coerceAtLeast(0))
    }

    override fun isKey(item: ItemStack): Boolean = PhysicalKeyUtil.isSlateKey(item)

    override fun getCrateForKey(item: ItemStack): CrateDefinition? {
        val crateId = PhysicalKeyUtil.crateId(item) ?: return null
        val crate = crateManager.byId(crateId) ?: return null
        return crate.takeIf { PhysicalKeyUtil.matchesKeyDefinition(item, it) }
    }
}
