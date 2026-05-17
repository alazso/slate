package so.alaz.slate.command.sub

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bukkit.entity.Player
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.command.SlatePermissions
import so.alaz.slate.message.MessageKeys
import so.alaz.slate.opening.OpenBatchResult

object OpenCommand {
    fun node(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("open")
            .requires { it.sender.hasPermission(SlatePermissions.PLAYER_OPEN) }
            .then(
                Commands.argument("crate", StringArgumentType.word())
                    .suggests { context, builder ->
                        CommandSuggestions.suggestCrateIds(context.source, builder)
                    }
                    .executes { context ->
                        execute(context, 1)
                    }
                    .then(
                        Commands.argument("amount", IntegerArgumentType.integer(1))
                            .executes { context ->
                                execute(context, IntegerArgumentType.getInteger(context, "amount"))
                            },
                    ),
            )
            .build()

    private fun execute(context: CommandContext<CommandSourceStack>, amount: Int): Int {
        val sender = context.source.sender
        val player = sender as? Player
        if (player == null) {
            CommandMessages.chat(sender, MessageKeys.COMMON_ONLY_PLAYERS, mapOf("command" to "/crates open"))
            return 0
        }

        val crateId = StringArgumentType.getString(context, "crate")
        val scope = SlateAPI.pluginScope()
        val openService = SlateAPI.openService()
        val plugin = SlateAPI.plugin() ?: run {
            CommandMessages.notInitialized(sender)
            return 0
        }
        if (scope == null || openService == null) {
            CommandMessages.notInitialized(sender)
            return 0
        }
        if (!player.hasPermission("${SlatePermissions.OPEN_CRATE_PREFIX}$crateId")) {
            CommandMessages.chat(sender, MessageKeys.OPEN_FORBIDDEN, mapOf("crate" to crateId))
            return 0
        }

        val maxBulk = plugin.configManager.current.maxBulkOpenAmount
        if (amount > maxBulk) {
            CommandMessages.chat(sender, MessageKeys.OPEN_MAX_BULK, mapOf("max" to maxBulk))
            return 0
        }

        CommandMessages.chat(sender, MessageKeys.OPEN_ATTEMPT, mapOf("crate" to crateId, "amount" to amount))
        scope.launch {
            val result = openService.openById(player, crateId, amount)
            withContext(plugin.mainDispatcher) {
                when (result) {
                    is OpenBatchResult.MissingCrate -> {
                        CommandMessages.chat(sender, MessageKeys.COMMON_UNKNOWN_CRATE, mapOf("crate" to crateId))
                    }

                    is OpenBatchResult.Executed -> {
                        val suffix = result.failure?.let(CommandMessages::openStoppedSuffix).orEmpty()
                        CommandMessages.chat(
                            sender,
                            MessageKeys.OPEN_RESULT,
                            mapOf(
                                "crate" to result.crateId,
                                "opened" to result.opened,
                                "attempted" to result.attempted,
                                "suffix" to suffix,
                            ),
                        )
                    }
                }
            }
        }
        return Command.SINGLE_SUCCESS
    }
}
