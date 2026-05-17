package so.alaz.slate.command.sub

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import kotlinx.coroutines.launch
import org.bukkit.Bukkit
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.command.SlatePermissions
import so.alaz.slate.message.MessageKeys

object AdminPityCommand {
    fun node(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("pity")
            .requires { it.sender.hasPermission(SlatePermissions.ADMIN_PITY) }
            .then(
                Commands.literal("get")
                    .then(
                        Commands.argument("player", StringArgumentType.word())
                            .then(
                                Commands.argument("crate", StringArgumentType.word())
                                    .suggests { context, builder ->
                                        CommandSuggestions.suggestCrateIds(context.source, builder)
                                    }
                                    .executes { context ->
                                        val sender = context.source.sender
                                        val playerName = StringArgumentType.getString(context, "player")
                                        val crate = StringArgumentType.getString(context, "crate")
                                        val target = Bukkit.getOfflinePlayerIfCached(playerName)
                                        if (target == null) {
                                            CommandMessages.chat(sender, MessageKeys.ADMIN_PITY_UNKNOWN_PLAYER, mapOf("player" to playerName))
                                            return@executes 0
                                        }
                                        val storage = SlateAPI.storageService()
                                        val scope = SlateAPI.pluginScope()
                                        val plugin = SlateAPI.plugin()
                                        if (storage == null || scope == null || plugin == null) {
                                            CommandMessages.notInitialized(sender)
                                            return@executes 0
                                        }
                                        scope.launch {
                                            val count = storage.getPityCount(target.uniqueId, crate)
                                            plugin.server.scheduler.runTask(
                                                plugin,
                                                Runnable {
                                                    CommandMessages.chat(
                                                        sender,
                                                        MessageKeys.ADMIN_PITY_GET_RESULT,
                                                        mapOf("player" to (target.name ?: playerName), "crate" to crate, "count" to count),
                                                    )
                                                },
                                            )
                                        }
                                        Command.SINGLE_SUCCESS
                                    },
                            ),
                    ),
            )
            .then(
                Commands.literal("reset")
                    .then(
                        Commands.argument("player", StringArgumentType.word())
                            .then(
                                Commands.argument("crate", StringArgumentType.word())
                                    .suggests { context, builder ->
                                        CommandSuggestions.suggestCrateIds(context.source, builder)
                                    }
                                    .executes { context ->
                                        val sender = context.source.sender
                                        val playerName = StringArgumentType.getString(context, "player")
                                        val crate = StringArgumentType.getString(context, "crate")
                                        val target = Bukkit.getOfflinePlayerIfCached(playerName)
                                        if (target == null) {
                                            CommandMessages.chat(sender, MessageKeys.ADMIN_PITY_UNKNOWN_PLAYER, mapOf("player" to playerName))
                                            return@executes 0
                                        }
                                        val storage = SlateAPI.storageService()
                                        val scope = SlateAPI.pluginScope()
                                        val plugin = SlateAPI.plugin()
                                        if (storage == null || scope == null || plugin == null) {
                                            CommandMessages.notInitialized(sender)
                                            return@executes 0
                                        }
                                        scope.launch {
                                            storage.setPityCount(target.uniqueId, crate, 0)
                                            plugin.server.scheduler.runTask(
                                                plugin,
                                                Runnable {
                                                    CommandMessages.chat(
                                                        sender,
                                                        MessageKeys.ADMIN_PITY_RESET_RESULT,
                                                        mapOf("player" to (target.name ?: playerName), "crate" to crate),
                                                    )
                                                },
                                            )
                                        }
                                        Command.SINGLE_SUCCESS
                                    },
                            ),
                    ),
            )
            .build()
}
