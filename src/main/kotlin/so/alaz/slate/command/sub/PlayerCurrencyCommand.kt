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
import so.alaz.slate.currency.domain.CurrencyAccount
import so.alaz.slate.economy.EconomyBalanceResult
import so.alaz.slate.message.MessageKeys

object PlayerCurrencyCommand {
    fun node(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("currency")
            .requires { it.sender.hasPermission(SlatePermissions.PLAYER_CURRENCY_BALANCE) }
            .executes { context -> show(context.source, null) }
            .then(
                Commands.argument("currency", StringArgumentType.word())
                    .suggests { context, builder -> CommandSuggestions.suggestCurrencyIds(context.source, builder) }
                    .executes { context -> show(context.source, StringArgumentType.getString(context, "currency")) },
            )
            .build()

    private fun show(source: CommandSourceStack, currencyId: String?): Int {
        val player = source.sender as? Player
        if (player == null) {
            CommandMessages.chat(source.sender, MessageKeys.COMMON_ONLY_PLAYERS, mapOf("command" to "/currency"))
            return 0
        }
        val service = SlateAPI.economyService()
        val scope = SlateAPI.pluginScope()
        if (service == null || scope == null) {
            CommandMessages.notInitialized(source.sender)
            return 0
        }
        scope.launch {
            val result = service.getBalance(CurrencyAccount.player(player.uniqueId), "default", currencyId)
            CommandMessages.dispatchForSender(source.sender) {
                when (result) {
                    is EconomyBalanceResult.Success ->
                        CommandMessages.chat(
                            source.sender,
                            MessageKeys.CURRENCY_BALANCE_RESULT,
                            mapOf(
                                "player" to player.name,
                                "currency" to (currencyId ?: SlateAPI.configManager()?.current?.currency?.defaultCurrency.orEmpty()),
                                "balance" to service.formatAmount(result.balance, "default", currencyId),
                            ),
                        )
                    is EconomyBalanceResult.UnknownCurrency ->
                        CommandMessages.chat(source.sender, MessageKeys.CURRENCY_UNKNOWN_CURRENCY, mapOf("currency" to (currencyId ?: "default")))
                    is EconomyBalanceResult.ProviderUnavailable ->
                        CommandMessages.chat(source.sender, MessageKeys.CURRENCY_PROVIDER_UNAVAILABLE, mapOf("provider" to "default"))
                    is EconomyBalanceResult.Failed ->
                        CommandMessages.chat(source.sender, MessageKeys.CURRENCY_TRANSACTION_FAILED, mapOf("error" to result.message))
                }
            }
        }
        return Command.SINGLE_SUCCESS
    }
}
