package so.alaz.slate.command.sub

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import kotlinx.coroutines.launch
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.command.SlatePermissions
import so.alaz.slate.message.MessageKeys

object KeyCommand {
    fun node(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("key")
            .then(giveNode())
            .then(takeNode())
            .then(setNode())
            .then(clearNode())
            .then(balanceNode())
            .build()

    private fun giveNode(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("give")
            .requires { it.sender.hasPermission(SlatePermissions.ADMIN_KEY_GIVE) }
            .then(
                Commands.argument("player", StringArgumentType.word())
                    .suggests { context, builder ->
                        CommandSuggestions.suggestOnlinePlayers(context.source, builder)
                    }
                    .then(
                        Commands.argument("crate", StringArgumentType.word())
                            .suggests { context, builder ->
                                CommandSuggestions.suggestCrateIds(context.source, builder)
                            }
                            .then(
                                Commands.argument("amount", IntegerArgumentType.integer(1))
                                    .executes { context -> mutate(context, MutationType.GIVE, null) }
                                    .then(
                                        Commands.argument("flags", StringArgumentType.greedyString())
                                            .suggests { context, builder ->
                                                CommandSuggestions.suggestKeyMutationFlags(context.source, builder)
                                            }
                                            .executes { context -> mutate(context, MutationType.GIVE, StringArgumentType.getString(context, "flags")) },
                                    ),
                            ),
                    ),
            )
            .build()

    private fun takeNode(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("take")
            .requires { it.sender.hasPermission(SlatePermissions.ADMIN_KEY_TAKE) }
            .then(
                Commands.argument("player", StringArgumentType.word())
                    .suggests { context, builder ->
                        CommandSuggestions.suggestOnlinePlayers(context.source, builder)
                    }
                    .then(
                        Commands.argument("crate", StringArgumentType.word())
                            .suggests { context, builder ->
                                CommandSuggestions.suggestCrateIds(context.source, builder)
                            }
                            .then(
                                Commands.argument("amount", IntegerArgumentType.integer(1))
                                    .executes { context -> mutate(context, MutationType.TAKE, null) }
                                    .then(
                                        Commands.argument("flags", StringArgumentType.greedyString())
                                            .suggests { context, builder ->
                                                CommandSuggestions.suggestKeyMutationFlags(context.source, builder)
                                            }
                                            .executes { context -> mutate(context, MutationType.TAKE, StringArgumentType.getString(context, "flags")) },
                                    ),
                            ),
                    ),
            )
            .build()

    private fun setNode(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("set")
            .requires { it.sender.hasPermission(SlatePermissions.ADMIN_KEY_SET) }
            .then(
                Commands.argument("player", StringArgumentType.word())
                    .suggests { context, builder ->
                        CommandSuggestions.suggestOnlinePlayers(context.source, builder)
                    }
                    .then(
                        Commands.argument("crate", StringArgumentType.word())
                            .suggests { context, builder ->
                                CommandSuggestions.suggestCrateIds(context.source, builder)
                            }
                            .then(
                                Commands.argument("amount", IntegerArgumentType.integer(0))
                                    .executes { context -> mutate(context, MutationType.SET, null) }
                                    .then(
                                        Commands.argument("flags", StringArgumentType.greedyString())
                                            .suggests { context, builder ->
                                                CommandSuggestions.suggestKeyMutationFlags(context.source, builder)
                                            }
                                            .executes { context -> mutate(context, MutationType.SET, StringArgumentType.getString(context, "flags")) },
                                    ),
                            ),
                    ),
            )
            .build()

    private fun clearNode(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("clear")
            .requires { it.sender.hasPermission(SlatePermissions.ADMIN_KEY_CLEAR) }
            .then(
                Commands.argument("player", StringArgumentType.word())
                    .suggests { context, builder ->
                        CommandSuggestions.suggestOnlinePlayers(context.source, builder)
                    }
                    .executes { context -> clear(context, null, null) }
                    .then(
                        Commands.argument("crate", StringArgumentType.word())
                            .suggests { context, builder ->
                                CommandSuggestions.suggestCrateIds(context.source, builder)
                            }
                            .executes { context -> clear(context, StringArgumentType.getString(context, "crate"), null) }
                            .then(
                                Commands.argument("flags", StringArgumentType.greedyString())
                                    .suggests { context, builder ->
                                        CommandSuggestions.suggestKeyClearFlags(context.source, builder)
                                    }
                                    .executes { context ->
                                        clear(context, StringArgumentType.getString(context, "crate"), StringArgumentType.getString(context, "flags"))
                                    },
                            ),
                    )
                    .then(
                        Commands.argument("flags", StringArgumentType.greedyString())
                            .suggests { context, builder ->
                                CommandSuggestions.suggestKeyClearFlags(context.source, builder)
                            }
                            .executes { context -> clear(context, null, StringArgumentType.getString(context, "flags")) },
                    ),
            )
            .build()

    private fun balanceNode(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("balance")
            .requires { it.sender.hasPermission(SlatePermissions.ADMIN_KEY_BALANCE) }
            .then(
                Commands.argument("player", StringArgumentType.word())
                    .suggests { context, builder ->
                        CommandSuggestions.suggestOnlinePlayers(context.source, builder)
                    }
                    .executes { context -> balance(context, null) }
                    .then(
                        Commands.argument("crate", StringArgumentType.word())
                            .suggests { context, builder ->
                                CommandSuggestions.suggestCrateIds(context.source, builder)
                            }
                            .executes { context -> balance(context, StringArgumentType.getString(context, "crate")) },
                    ),
            )
            .build()

    private fun mutate(context: CommandContext<CommandSourceStack>, type: MutationType, rawFlags: String?): Int {
        val sender = context.source.sender
        val playerArg = StringArgumentType.getString(context, "player")
        val crateId = StringArgumentType.getString(context, "crate")
        val amount = IntegerArgumentType.getInteger(context, "amount")
        val flags = CommandFlagUtil.tokenize(rawFlags)
        val reason = CommandFlagUtil.extractReason(rawFlags)
        val silent = CommandFlagUtil.hasFlag(flags, "--silent")
        val usePhysical = CommandFlagUtil.hasFlag(flags, "--physical")
        val useVirtual = CommandFlagUtil.hasFlag(flags, "--virtual") || !usePhysical
        if (usePhysical && useVirtual && CommandFlagUtil.hasFlag(flags, "--virtual")) {
            CommandMessages.chat(sender, MessageKeys.KEYS_BAD_MODE)
            return 0
        }

        val targetUuid = CommandTargetResolver.resolveUuid(playerArg)
        if (targetUuid == null) {
            CommandMessages.chat(sender, MessageKeys.KEYS_PLAYER_NOT_FOUND)
            return 0
        }

        val crateManager = SlateAPI.crateManager()
        val crate = crateManager?.byId(crateId)
        if (crate == null) {
            CommandMessages.chat(sender, MessageKeys.COMMON_UNKNOWN_CRATE, mapOf("crate" to crateId))
            return 0
        }

        val scope = SlateAPI.pluginScope()
        val keyService = SlateAPI.keyService()
        if (scope == null || keyService == null) {
            CommandMessages.notInitialized(sender)
            return 0
        }

        scope.launch {
            val result = runCatching {
                if (usePhysical) {
                    val onlineTarget = Bukkit.getPlayer(targetUuid)
                        ?: error("Target must be online for physical key operations.")
                    when (type) {
                        MutationType.GIVE -> {
                            val given = keyService.givePhysicalKeys(onlineTarget, crate, amount)
                            "Granted $given/$amount physical key(s) for '$crateId' to ${onlineTarget.name}."
                        }

                        MutationType.TAKE -> {
                            val removed = keyService.takePhysicalKeys(onlineTarget, crate, amount)
                            "Removed $removed/$amount physical key(s) for '$crateId' from ${onlineTarget.name}."
                        }

                        MutationType.SET -> error("Physical set mode is not supported. Use take/give.")
                    }
                } else {
                    when (type) {
                        MutationType.GIVE -> {
                            keyService.giveVirtualKeys(targetUuid, crateId, amount, silent = silent)
                            "Granted $amount virtual key(s) for '$crateId' to $playerArg."
                        }

                        MutationType.TAKE -> {
                            keyService.takeVirtualKeys(targetUuid, crateId, amount)
                            "Removed up to $amount virtual key(s) for '$crateId' from $playerArg."
                        }

                        MutationType.SET -> {
                            keyService.setVirtualKeys(targetUuid, crateId, amount)
                            "Set virtual key(s) for '$crateId' on $playerArg to $amount."
                        }
                    }
                }
            }
            CommandMessages.dispatchForSender(sender) {
                result.onSuccess { message ->
                    val suffix = reason?.let { " <gray>reason=$it</gray>" }.orEmpty()
                    val key = when (type) {
                        MutationType.GIVE -> MessageKeys.KEYS_GIVE_SUCCESS
                        MutationType.TAKE -> MessageKeys.KEYS_TAKE_SUCCESS
                        MutationType.SET -> MessageKeys.KEYS_SET_SUCCESS
                    }
                    CommandMessages.chat(
                        sender,
                        key,
                        mapOf("message" to message, "suffix" to suffix),
                    )
                }.onFailure { error ->
                    CommandMessages.chat(
                        sender,
                        MessageKeys.KEYS_OPERATION_FAILED,
                        mapOf("operation" to type.name.lowercase(), "error" to (error.message ?: "unknown error")),
                    )
                }
            }
        }
        return Command.SINGLE_SUCCESS
    }

    private fun clear(context: CommandContext<CommandSourceStack>, crateId: String?, rawFlags: String?): Int {
        val sender = context.source.sender
        val playerArg = StringArgumentType.getString(context, "player")
        val flags = CommandFlagUtil.tokenize(rawFlags)
        if (!CommandFlagUtil.hasFlag(flags, "--confirm")) {
            CommandMessages.chat(sender, MessageKeys.KEYS_CLEAR_CONFIRM_REQUIRED)
            return 0
        }
        val reason = CommandFlagUtil.extractReason(rawFlags)
        val targetUuid = CommandTargetResolver.resolveUuid(playerArg)
        if (targetUuid == null) {
            CommandMessages.chat(sender, MessageKeys.KEYS_PLAYER_NOT_FOUND)
            return 0
        }

        val crateManager = SlateAPI.crateManager()
        val keyService = SlateAPI.keyService()
        val scope = SlateAPI.pluginScope()
        if (crateManager == null || keyService == null || scope == null) {
            CommandMessages.notInitialized(sender)
            return 0
        }

        val crates = if (crateId == null) crateManager.all().map { it.id } else listOf(crateId)
        if (crateId != null && crateManager.byId(crateId) == null) {
            CommandMessages.chat(sender, MessageKeys.COMMON_UNKNOWN_CRATE, mapOf("crate" to crateId))
            return 0
        }

        scope.launch {
            crates.forEach { id -> keyService.setVirtualKeys(targetUuid, id, 0) }
            CommandMessages.dispatchForSender(sender) {
                val suffix = reason?.let { " <gray>reason=$it</gray>" }.orEmpty()
                val target = crateId ?: "all crates"
                CommandMessages.chat(
                    sender,
                    MessageKeys.KEYS_CLEAR_SUCCESS,
                    mapOf("player" to playerArg, "target" to target, "suffix" to suffix),
                )
            }
        }
        return Command.SINGLE_SUCCESS
    }

    private fun balance(context: CommandContext<CommandSourceStack>, crateId: String?): Int {
        val sender = context.source.sender
        val playerArg = StringArgumentType.getString(context, "player")
        val targetUuid = CommandTargetResolver.resolveUuid(playerArg)
        if (targetUuid == null) {
            CommandMessages.chat(sender, MessageKeys.KEYS_PLAYER_NOT_FOUND)
            return 0
        }
        val scope = SlateAPI.pluginScope()
        val keyService = SlateAPI.keyService()
        val crateManager = SlateAPI.crateManager()
        if (scope == null || keyService == null || crateManager == null) {
            CommandMessages.notInitialized(sender)
            return 0
        }
        scope.launch {
            if (crateId != null) {
                val crate = crateManager.byId(crateId)
                if (crate == null) {
                    CommandMessages.dispatchForSender(sender) {
                        CommandMessages.chat(sender, MessageKeys.COMMON_UNKNOWN_CRATE, mapOf("crate" to crateId))
                    }
                    return@launch
                }
                val amount = keyService.getVirtualKeys(targetUuid, crate.id)
                CommandMessages.dispatchForSender(sender) {
                    CommandMessages.chat(sender, MessageKeys.KEYS_BALANCE_SINGLE, mapOf("player" to playerArg, "amount" to amount, "crate" to crate.id))
                }
                return@launch
            }

            var total = 0
            val parts = mutableListOf<String>()
            for (crate in crateManager.all().sortedBy { it.id }) {
                val amount = keyService.getVirtualKeys(targetUuid, crate.id)
                if (amount > 0) parts += "${crate.id}:$amount"
                total += amount
            }
            val breakdown = parts.joinToString(", ").ifBlank { "none" }
            CommandMessages.dispatchForSender(sender) {
                CommandMessages.chat(sender, MessageKeys.KEYS_BALANCE_TOTAL, mapOf("player" to playerArg, "total" to total, "breakdown" to breakdown))
            }
        }
        return Command.SINGLE_SUCCESS
    }

    private enum class MutationType {
        GIVE,
        TAKE,
        SET,
    }
}
