package so.alaz.slate.command.sub

import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.command.SlatePermissions
import so.alaz.slate.crate.Prize
import so.alaz.slate.message.MessageKeys
import so.alaz.slate.storage.PendingPrize
import so.alaz.slate.util.ItemUtil

object ClaimCommand {
    fun node(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("claim")
            .requires { it.sender.hasPermission(SlatePermissions.PLAYER_CLAIM) }
            .executes { context ->
                val player = context.source.sender as? Player
                if (player == null) {
                    CommandMessages.chat(context.source.sender, MessageKeys.COMMON_ONLY_PLAYERS, mapOf("command" to "/crates claim"))
                    return@executes 0
                }
                queueClaim(player)
                Command.SINGLE_SUCCESS
            }
            .build()

    private fun queueClaim(target: Player) {
        val scope = SlateAPI.pluginScope()
        if (scope == null) {
            CommandMessages.notInitialized(target)
            return
        }
        CommandMessages.chat(target, MessageKeys.CLAIM_PROCESSING)
        scope.launch {
            val summary = claimPending(target)
            withContext(checkNotNull(SlateAPI.plugin()).mainDispatcher) {
                CommandMessages.chat(
                    target,
                    MessageKeys.CLAIM_RESULT,
                    mapOf("total" to summary.total, "delivered" to summary.delivered, "remaining" to summary.remaining),
                )
            }
        }
    }

    private suspend fun claimPending(target: Player): ClaimSummary {
        val storage = checkNotNull(SlateAPI.storageService()) { "Storage service unavailable" }
        val crateManager = checkNotNull(SlateAPI.crateManager()) { "Crate manager unavailable" }

        val pending = storage.getPendingPrizes(target.uniqueId)
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

            val deliveredNow = runCatching { deliverPrize(target, prize) }.isSuccess
            if (deliveredNow) {
                delivered++
            } else {
                remaining += entry
            }
        }

        storage.clearPendingPrizes(target.uniqueId)
        for (entry in remaining) {
            storage.addPendingPrize(target.uniqueId, entry.crateId, entry.prizeId)
        }

        return ClaimSummary(total = pending.size, delivered = delivered, remaining = remaining.size)
    }

    private suspend fun deliverPrize(player: Player, prize: Prize) {
        runOnMain {
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

            for (message in prize.messages) {
                SlateAPI.messageService()?.chatRaw(player, message)
            }
        }
    }

    private suspend fun runOnMain(task: () -> Unit) = suspendCancellableCoroutine<Unit> { cont ->
        val plugin = SlateAPI.plugin()
        if (plugin == null) {
            cont.resumeWithException(IllegalStateException("Slate plugin unavailable"))
            return@suspendCancellableCoroutine
        }
        plugin.server.scheduler.runTask(
            plugin,
            Runnable {
                runCatching(task)
                    .onSuccess { cont.resume(Unit) }
                    .onFailure { cont.resumeWithException(it) }
            },
        )
    }

    private data class ClaimSummary(
        val total: Int,
        val delivered: Int,
        val remaining: Int,
    )
}
