package so.alaz.slate.command.sub

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import kotlinx.coroutines.launch
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.command.SlatePermissions
import so.alaz.slate.currency.domain.CurrencyAccount
import so.alaz.slate.currency.domain.CurrencyAmount
import so.alaz.slate.economy.EconomyBalanceResult
import so.alaz.slate.economy.EconomyContext
import so.alaz.slate.economy.EconomyMutationResult
import so.alaz.slate.message.MessageKeys

object CurrencyAdminCommand {
    fun node(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("currency")
            .requires { it.sender.hasPermission(SlatePermissions.ADMIN_CURRENCY) }
            .then(listNode())
            .then(providersNode())
            .then(balanceNode())
            .then(giveNode())
            .then(takeNode())
            .then(setNode())
            .then(historyNode())
            .build()

    private fun listNode(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("list")
            .requires { it.sender.hasPermission(SlatePermissions.ADMIN_CURRENCY_LIST) }
            .executes { context ->
                val service = SlateAPI.economyService()
                if (service == null) {
                    CommandMessages.notInitialized(context.source.sender)
                    return@executes 0
                }
                val values = service.currencies("default").joinToString(", ") { it.id }
                CommandMessages.chat(context.source.sender, MessageKeys.CURRENCY_LIST, mapOf("currencies" to values.ifBlank { "none" }))
                Command.SINGLE_SUCCESS
            }
            .build()

    private fun providersNode(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("providers")
            .requires { it.sender.hasPermission(SlatePermissions.ADMIN_CURRENCY_PROVIDERS) }
            .executes { context ->
                val service = SlateAPI.economyService()
                if (service == null) {
                    CommandMessages.notInitialized(context.source.sender)
                    return@executes 0
                }
                val values = service.providers().joinToString(", ") { "${it.id}:${if (it.isAvailable()) "ready" else "down"}" }
                CommandMessages.chat(context.source.sender, MessageKeys.CURRENCY_PROVIDER_LIST, mapOf("providers" to values.ifBlank { "none" }))
                Command.SINGLE_SUCCESS
            }
            .build()

    private fun balanceNode(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("balance")
            .requires { it.sender.hasPermission(SlatePermissions.ADMIN_CURRENCY_BALANCE) }
            .then(
                Commands.argument("player", StringArgumentType.word())
                    .suggests { context, builder -> CommandSuggestions.suggestOnlinePlayers(context.source, builder) }
                    .then(
                        Commands.argument("currency", StringArgumentType.word())
                            .suggests { context, builder -> CommandSuggestions.suggestCurrencyIds(context.source, builder) }
                            .executes { context -> balance(context, null) }
                            .then(
                                Commands.argument("provider", StringArgumentType.word())
                                    .suggests { context, builder -> CommandSuggestions.suggestCurrencyProviderIds(context.source, builder) }
                                    .executes { context -> balance(context, StringArgumentType.getString(context, "provider")) },
                            ),
                    ),
            )
            .build()

    private fun giveNode(): LiteralCommandNode<CommandSourceStack> = mutationNode("give", SlatePermissions.ADMIN_CURRENCY_GIVE)
    private fun takeNode(): LiteralCommandNode<CommandSourceStack> = mutationNode("take", SlatePermissions.ADMIN_CURRENCY_TAKE)
    private fun setNode(): LiteralCommandNode<CommandSourceStack> = mutationNode("set", SlatePermissions.ADMIN_CURRENCY_SET)

    private fun mutationNode(sub: String, permission: String): LiteralCommandNode<CommandSourceStack> =
        Commands.literal(sub)
            .requires { it.sender.hasPermission(permission) }
            .then(
                Commands.argument("player", StringArgumentType.word())
                    .suggests { context, builder -> CommandSuggestions.suggestOnlinePlayers(context.source, builder) }
                    .then(
                        Commands.argument("currency", StringArgumentType.word())
                            .suggests { context, builder -> CommandSuggestions.suggestCurrencyIds(context.source, builder) }
                            .then(
                                Commands.argument("amount", LongArgumentType.longArg(0L))
                                    .executes { context -> mutate(context, sub, null) }
                                    .then(
                                        Commands.argument("provider", StringArgumentType.word())
                                            .suggests { context, builder -> CommandSuggestions.suggestCurrencyProviderIds(context.source, builder) }
                                            .executes { context -> mutate(context, sub, StringArgumentType.getString(context, "provider")) },
                                    ),
                            ),
                    ),
            )
            .build()

    private fun historyNode(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("history")
            .requires { it.sender.hasPermission(SlatePermissions.ADMIN_CURRENCY_HISTORY) }
            .then(
                Commands.argument("player", StringArgumentType.word())
                    .suggests { context, builder -> CommandSuggestions.suggestOnlinePlayers(context.source, builder) }
                    .executes { context -> history(context, null, null, 10) }
                    .then(
                        Commands.argument("currency", StringArgumentType.word())
                            .suggests { context, builder -> CommandSuggestions.suggestCurrencyIds(context.source, builder) }
                            .executes { context -> history(context, StringArgumentType.getString(context, "currency"), null, 10) }
                            .then(
                                Commands.argument("provider", StringArgumentType.word())
                                    .suggests { context, builder -> CommandSuggestions.suggestCurrencyProviderIds(context.source, builder) }
                                    .executes { context -> history(context, StringArgumentType.getString(context, "currency"), StringArgumentType.getString(context, "provider"), 10) }
                                    .then(
                                        Commands.argument("limit", IntegerArgumentType.integer(1, 50))
                                            .executes { context ->
                                                history(
                                                    context,
                                                    StringArgumentType.getString(context, "currency"),
                                                    StringArgumentType.getString(context, "provider"),
                                                    IntegerArgumentType.getInteger(context, "limit"),
                                                )
                                            },
                                    ),
                            ),
                    ),
            )
            .build()

    private fun balance(context: com.mojang.brigadier.context.CommandContext<CommandSourceStack>, provider: String?): Int {
        val service = SlateAPI.economyService()
        val scope = SlateAPI.pluginScope()
        if (service == null || scope == null) {
            CommandMessages.notInitialized(context.source.sender)
            return 0
        }
        val targetUuid = CommandTargetResolver.resolveUuid(StringArgumentType.getString(context, "player"))
        if (targetUuid == null) {
            CommandMessages.chat(context.source.sender, MessageKeys.CURRENCY_INVALID_TARGET)
            return 0
        }
        val currency = StringArgumentType.getString(context, "currency")
        scope.launch {
            val result = service.getBalance(CurrencyAccount.player(targetUuid), provider, currency)
            CommandMessages.dispatchForSender(context.source.sender) {
                when (result) {
                    is EconomyBalanceResult.Success ->
                        CommandMessages.chat(
                            context.source.sender,
                            MessageKeys.CURRENCY_BALANCE_RESULT,
                            mapOf(
                                "player" to targetUuid,
                                "currency" to currency,
                                "balance" to service.formatAmount(result.balance, provider, currency),
                            ),
                        )
                    is EconomyBalanceResult.UnknownCurrency ->
                        CommandMessages.chat(context.source.sender, MessageKeys.CURRENCY_UNKNOWN_CURRENCY, mapOf("currency" to currency))
                    is EconomyBalanceResult.ProviderUnavailable ->
                        CommandMessages.chat(context.source.sender, MessageKeys.CURRENCY_PROVIDER_UNAVAILABLE, mapOf("provider" to (provider ?: "default")))
                    is EconomyBalanceResult.Failed ->
                        CommandMessages.chat(context.source.sender, MessageKeys.CURRENCY_TRANSACTION_FAILED, mapOf("error" to result.message))
                }
            }
        }
        return Command.SINGLE_SUCCESS
    }

    private fun mutate(context: com.mojang.brigadier.context.CommandContext<CommandSourceStack>, type: String, provider: String?): Int {
        val service = SlateAPI.economyService()
        val scope = SlateAPI.pluginScope()
        if (service == null || scope == null) {
            CommandMessages.notInitialized(context.source.sender)
            return 0
        }
        val targetUuid = CommandTargetResolver.resolveUuid(StringArgumentType.getString(context, "player"))
        if (targetUuid == null) {
            CommandMessages.chat(context.source.sender, MessageKeys.CURRENCY_INVALID_TARGET)
            return 0
        }
        val currency = StringArgumentType.getString(context, "currency")
        val amount = CurrencyAmount.ofMinorUnits(LongArgumentType.getLong(context, "amount"))
        scope.launch {
            val account = CurrencyAccount.player(targetUuid)
            val result = when (type) {
                "give" -> service.deposit(account, provider, currency, amount, EconomyContext(reason = "admin-give"))
                "take" -> service.withdraw(account, provider, currency, amount, EconomyContext(reason = "admin-take"))
                else -> service.setBalance(account, provider, currency, amount, EconomyContext(reason = "admin-set"))
            }
            CommandMessages.dispatchForSender(context.source.sender) {
                when (result) {
                    is EconomyMutationResult.Success -> {
                        val key = when (type) {
                            "give" -> MessageKeys.CURRENCY_GIVE_SUCCESS
                            "take" -> MessageKeys.CURRENCY_TAKE_SUCCESS
                            else -> MessageKeys.CURRENCY_SET_SUCCESS
                        }
                        CommandMessages.chat(
                            context.source.sender,
                            key,
                            mapOf(
                                "player" to targetUuid,
                                "currency" to currency,
                                "amount" to service.formatAmount(amount, provider, currency),
                            ),
                        )
                    }

                    is EconomyMutationResult.InsufficientFunds ->
                        CommandMessages.chat(context.source.sender, MessageKeys.CURRENCY_INSUFFICIENT_FUNDS, mapOf("currency" to currency))
                    is EconomyMutationResult.UnknownCurrency ->
                        CommandMessages.chat(context.source.sender, MessageKeys.CURRENCY_UNKNOWN_CURRENCY, mapOf("currency" to currency))
                    is EconomyMutationResult.ProviderUnavailable ->
                        CommandMessages.chat(context.source.sender, MessageKeys.CURRENCY_PROVIDER_UNAVAILABLE, mapOf("provider" to (provider ?: "default")))
                    is EconomyMutationResult.Failed ->
                        CommandMessages.chat(context.source.sender, MessageKeys.CURRENCY_TRANSACTION_FAILED, mapOf("error" to result.message))
                    is EconomyMutationResult.UnsupportedOperation ->
                        CommandMessages.chat(context.source.sender, MessageKeys.CURRENCY_TRANSACTION_FAILED, mapOf("error" to result.message))
                }
            }
        }
        return Command.SINGLE_SUCCESS
    }

    private fun history(
        context: com.mojang.brigadier.context.CommandContext<CommandSourceStack>,
        currency: String?,
        provider: String?,
        limit: Int,
    ): Int {
        val service = SlateAPI.economyService()
        val scope = SlateAPI.pluginScope()
        if (service == null || scope == null) {
            CommandMessages.notInitialized(context.source.sender)
            return 0
        }
        val targetUuid = CommandTargetResolver.resolveUuid(StringArgumentType.getString(context, "player"))
        if (targetUuid == null) {
            CommandMessages.chat(context.source.sender, MessageKeys.CURRENCY_INVALID_TARGET)
            return 0
        }
        scope.launch {
            CommandMessages.dispatchForSender(context.source.sender) {
                CommandMessages.chat(
                    context.source.sender,
                    MessageKeys.CURRENCY_TRANSACTION_FAILED,
                    mapOf("error" to "history unavailable for active provider"),
                )
            }
        }
        return Command.SINGLE_SUCCESS
    }
}
