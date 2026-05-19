package so.alaz.slate.opening

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.CrateManager
import so.alaz.slate.key.KeyService
import so.alaz.slate.key.KeyType

class OpenService(
    private val crateManager: CrateManager,
    private val keyService: KeyService,
    private val openingPipeline: OpeningPipeline,
    private val clusterCoordinator: so.alaz.slate.storage.ClusterCoordinator,
) {
    suspend fun preflightById(
        player: Player,
        crateId: String,
        amount: Int = 1,
    ): OpenPreflightResult? {
        val crate = crateManager.byId(crateId) ?: return null
        return openingPipeline.preflight(
            player = player,
            crate = crate,
            keyType = KeyType.Virtual,
            checkCooldown = true,
            amount = amount,
        )
    }

    suspend fun maxAffordable(player: Player, crateId: String, cap: Int): Int {
        val crate = crateManager.byId(crateId) ?: return 0
        return openingPipeline.maxAffordable(player, crate, cap)
    }

    suspend fun cooldownRemainingMillis(player: Player, crateId: String): Long {
        val crate = crateManager.byId(crateId) ?: return 0L
        return openingPipeline.cooldownRemainingMillis(player.uniqueId, crate)
    }

    suspend fun openById(player: Player, crateId: String, amount: Int): OpenBatchResult {
        val crate = crateManager.byId(crateId) ?: return OpenBatchResult.MissingCrate(crateId)
        return withOpenLock(player.uniqueId, crate.id) {
            if (openingPipeline.isCooldownActive(player.uniqueId, crate)) {
                return@withOpenLock OpenBatchResult.Executed(
                    crateId = crate.id,
                    opened = 0,
                    attempted = amount.coerceAtLeast(1),
                    failure = OpeningResult.Failure(FailureReason.ON_COOLDOWN),
                )
            }
            var opened = 0
            var failure: OpeningResult.Failure? = null
            val attempts = amount.coerceAtLeast(1)
            val batchId = UUID.randomUUID()
            for (i in 0 until attempts) {
                val requestId = "cmd:${player.uniqueId}:${crate.id}:$batchId:$i"
                val result = clusterCoordinator.withOpenLock(requestId) {
                    openingPipeline.execute(
                        player = player,
                        crate = crate,
                        keyType = KeyType.Virtual,
                        requestId = requestId,
                        checkCooldown = false,
                        writeCooldown = false,
                    )
                }
                when (result) {
                    is OpeningResult.Success -> opened++
                    is OpeningResult.Failure -> {
                        failure = result
                        break
                    }
                }
            }
            if (opened > 0) {
                openingPipeline.setCooldownNow(player.uniqueId, crate)
            }
            OpenBatchResult.Executed(crateId = crate.id, opened = opened, attempted = attempts, failure = failure)
        }
    }

    suspend fun openFromInteraction(player: Player, crate: CrateDefinition, heldItem: ItemStack?): OpeningResult {
        val keyType = if (heldItem != null && keyService.isKey(heldItem) && keyService.getCrateForKey(heldItem)?.id == crate.id) {
            KeyType.Physical
        } else {
            KeyType.Virtual
        }
        return withOpenLock(player.uniqueId, crate.id) {
            val requestId = "interact:${player.uniqueId}:${crate.id}:${System.nanoTime()}"
            clusterCoordinator.withOpenLock(requestId) {
                openingPipeline.execute(player, crate, keyType, requestId = requestId)
            }
        }
    }

    private suspend fun <T> withOpenLock(playerId: UUID, crateId: String, block: suspend () -> T): T {
        val key = OpenKey(playerId, crateId)
        val mutex = locks.computeIfAbsent(key) { Mutex() }
        return try {
            mutex.withLock { block() }
        } finally {
            if (!mutex.isLocked) {
                locks.remove(key, mutex)
            }
        }
    }

    private data class OpenKey(val playerId: UUID, val crateId: String)

    private val locks = ConcurrentHashMap<OpenKey, Mutex>()
}

sealed class OpenBatchResult {
    data class MissingCrate(val crateId: String) : OpenBatchResult()
    data class Executed(
        val crateId: String,
        val opened: Int,
        val attempted: Int,
        val failure: OpeningResult.Failure?,
    ) : OpenBatchResult() {
        val failedReason: FailureReason?
            get() = failure?.reason
    }
}
