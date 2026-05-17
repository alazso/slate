package so.alaz.slate.command.sub

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import kotlinx.coroutines.launch
import org.bukkit.entity.Player
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.command.SlatePermissions
import so.alaz.slate.message.MessageKeys

object PlayerKeysCommand {
    fun node(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("keys")
            .requires { it.sender.hasPermission(SlatePermissions.PLAYER_KEYS) }
            .executes { context ->
                execute(context, crateId = null)
            }
            .then(
                Commands.argument("crate", StringArgumentType.word())
                    .suggests { context, builder ->
                        CommandSuggestions.suggestCrateIds(context.source, builder)
                    }
                    .executes { context ->
                        execute(context, StringArgumentType.getString(context, "crate"))
                    },
            )
            .build()

    private fun execute(
        context: com.mojang.brigadier.context.CommandContext<CommandSourceStack>,
        crateId: String?,
    ): Int {
        val player = context.source.sender as? Player
        if (player == null) {
            CommandMessages.chat(context.source.sender, MessageKeys.COMMON_ONLY_PLAYERS, mapOf("command" to "/keys"))
            return 0
        }
        val scope = SlateAPI.pluginScope()
        val keyService = SlateAPI.keyService()
        val crateManager = SlateAPI.crateManager()
        val plugin = SlateAPI.plugin()
        if (scope == null || keyService == null || crateManager == null || plugin == null) {
            CommandMessages.notInitialized(player)
            return 0
        }

        scope.launch {
            if (crateId == null) {
                val crates = crateManager.all().sortedBy { it.id }
                var total = 0
                val parts = mutableListOf<String>()
                for (crate in crates) {
                    val amount = keyService.getVirtualKeys(player.uniqueId, crate.id)
                    if (amount > 0) {
                        parts += "${crate.id}:$amount"
                    }
                    total += amount
                }
                val body = parts.joinToString(", ").ifBlank { "none" }
                plugin.server.scheduler.runTask(
                    plugin,
                    Runnable {
                        CommandMessages.chat(player, MessageKeys.PLAYER_KEYS_TOTAL, mapOf("total" to total, "body" to body))
                    },
                )
                return@launch
            }

            val crate = crateManager.byId(crateId)
            if (crate == null) {
                plugin.server.scheduler.runTask(
                    plugin,
                    Runnable { CommandMessages.chat(player, MessageKeys.COMMON_UNKNOWN_CRATE, mapOf("crate" to crateId)) },
                )
                return@launch
            }
            val amount = keyService.getVirtualKeys(player.uniqueId, crate.id)
            plugin.server.scheduler.runTask(
                plugin,
                Runnable { CommandMessages.chat(player, MessageKeys.PLAYER_KEYS_SINGLE, mapOf("crate" to crate.id, "amount" to amount)) },
            )
        }
        return Command.SINGLE_SUCCESS
    }
}
