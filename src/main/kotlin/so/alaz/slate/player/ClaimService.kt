package so.alaz.slate.player

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import so.alaz.slate.crate.CrateManager
import so.alaz.slate.crate.Prize
import so.alaz.slate.scheduler.PlatformScheduler
import so.alaz.slate.storage.PendingPrize
import so.alaz.slate.storage.StorageService
import so.alaz.slate.util.ItemUtil

class ClaimService(
    private val storage: StorageService,
    private val crateManager: CrateManager,
    private val scheduler: PlatformScheduler,
) {
    suspend fun claimPending(player: Player): ClaimSummary {
        val pending = storage.getPendingPrizes(player.uniqueId)
        if (pending.isEmpty()) {
            return ClaimSummary(total = 0, delivered = 0, remaining = 0)
        }

        val remaining = mutableListOf<PendingPrize>()
        var delivered = 0
        for (entry in pending) {
            val crate = crateManager.byId(entry.crateId)
            val prize = crate?.prizes?.firstOrNull { it.id == entry.prizeId }
            if (prize == null) {
                remaining += entry
                continue
            }
            val deliveredNow = runCatching { deliverPrize(player, prize) }.isSuccess
            if (deliveredNow) delivered++ else remaining += entry
        }

        storage.clearPendingPrizes(player.uniqueId)
        for (entry in remaining) {
            storage.addPendingPrize(player.uniqueId, entry.crateId, entry.prizeId)
        }
        return ClaimSummary(total = pending.size, delivered = delivered, remaining = remaining.size)
    }

    suspend fun pendingCount(player: Player): Int = storage.getPendingPrizes(player.uniqueId).size

    private suspend fun deliverPrize(player: Player, prize: Prize) {
        runForPlayer(player) {
            val stacks = prize.items.flatMap { item ->
                ItemUtil.expandPrizeItem(item)
                    ?: throw IllegalStateException("Invalid prize item '${item.material}' in prize '${prize.id}'")
            }
            if (stacks.isNotEmpty()) {
                val overflow = player.inventory.addItem(*stacks.toTypedArray())
                if (overflow.isNotEmpty()) {
                    throw IllegalStateException("Inventory full while delivering prize '${prize.id}'")
                }
            }

            for (command in prize.commands) {
                val prepared = command.replace("%player%", player.name)
                check(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), prepared)) {
                    "Command delivery failed for '$prepared'"
                }
            }
        }
    }

    private suspend fun runForPlayer(player: Player, task: () -> Unit) = suspendCancellableCoroutine<Unit> { cont ->
        scheduler.runForPlayerEntity(player) {
            runCatching(task)
                .onSuccess { cont.resume(Unit) }
                .onFailure { cont.resumeWithException(it) }
        }
    }
}

data class ClaimSummary(
    val total: Int,
    val delivered: Int,
    val remaining: Int,
)

