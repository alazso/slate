package so.alaz.slate.command.sub

import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bukkit.entity.Player
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.command.SlatePermissions
import so.alaz.slate.message.MessageKeys

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
        val claimService = SlateAPI.claimService()
        if (scope == null) {
            CommandMessages.notInitialized(target)
            return
        }
        if (claimService == null) {
            CommandMessages.notInitialized(target)
            return
        }
        CommandMessages.chat(target, MessageKeys.CLAIM_PROCESSING)
        scope.launch {
            val summary = claimService.claimPending(target)
            withContext(checkNotNull(SlateAPI.plugin()).mainDispatcher) {
                CommandMessages.chat(
                    target,
                    MessageKeys.CLAIM_RESULT,
                    mapOf("total" to summary.total, "delivered" to summary.delivered, "remaining" to summary.remaining),
                )
            }
        }
    }
}
