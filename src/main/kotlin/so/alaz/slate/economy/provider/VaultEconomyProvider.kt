package so.alaz.slate.economy.provider

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import net.milkbowl.vault.economy.Economy
import org.bukkit.Server
import so.alaz.slate.currency.domain.CurrencyAccount
import so.alaz.slate.currency.domain.CurrencyAccountType
import so.alaz.slate.currency.domain.CurrencyAmount
import so.alaz.slate.economy.EconomyBalanceResult
import so.alaz.slate.economy.EconomyContext
import so.alaz.slate.economy.EconomyCurrency
import so.alaz.slate.economy.EconomyMutationResult
import so.alaz.slate.economy.EconomyProvider
import so.alaz.slate.economy.EconomyProviderCapabilities

class VaultEconomyProvider(
    private val server: Server,
    private val currencyId: String,
    private val currencyName: String = "Money",
    private val currencySymbol: String? = "$",
    private val decimals: Int = 2,
) : EconomyProvider {
    override val id: String = "vault"
    override val displayName: String = "Vault Economy"

    override fun isAvailable(): Boolean = server.pluginManager.getPlugin("Vault") != null && vaultEconomy() != null

    override fun capabilities(): EconomyProviderCapabilities = EconomyProviderCapabilities(
        supportsMultipleCurrencies = false,
        supportsOfflinePlayers = true,
        supportsAtomicWithdraw = false,
        supportsRefund = false,
        supportsTransactionIds = false,
    )

    override fun currencies(): Collection<EconomyCurrency> = listOf(
        EconomyCurrency(
            id = currencyId,
            name = currencyName,
            symbol = currencySymbol,
            decimals = decimals,
        ),
    )

    override suspend fun getBalance(account: CurrencyAccount, currencyId: String): EconomyBalanceResult {
        if (currencyId != this.currencyId) {
            return EconomyBalanceResult.UnknownCurrency(id, currencyId)
        }
        val economy = vaultEconomy() ?: return EconomyBalanceResult.ProviderUnavailable(id, "Vault economy unavailable")
        val player = resolvePlayer(account) ?: return EconomyBalanceResult.Failed(id, "unsupported account type")
        return EconomyBalanceResult.Success(fromBigDecimal(BigDecimal.valueOf(economy.getBalance(player))))
    }

    override suspend fun deposit(
        account: CurrencyAccount,
        currencyId: String,
        amount: CurrencyAmount,
        context: EconomyContext,
    ): EconomyMutationResult {
        if (currencyId != this.currencyId) return EconomyMutationResult.UnknownCurrency(id, currencyId)
        val economy = vaultEconomy() ?: return EconomyMutationResult.ProviderUnavailable(id, "Vault economy unavailable")
        val player = resolvePlayer(account) ?: return EconomyMutationResult.Failed(id, "unsupported account type")
        val before = BigDecimal.valueOf(economy.getBalance(player))
        val response = economy.depositPlayer(player, toBigDecimal(amount).toDouble())
        if (!response.transactionSuccess()) {
            return EconomyMutationResult.Failed(id, response.errorMessage ?: "vault deposit failed")
        }
        val after = BigDecimal.valueOf(economy.getBalance(player))
        return EconomyMutationResult.Success(
            transactionId = context.operationId?.let { "$it:${context.transactionKey ?: "vault"}" },
            beforeBalance = fromBigDecimal(before),
            afterBalance = fromBigDecimal(after),
        )
    }

    override suspend fun withdraw(
        account: CurrencyAccount,
        currencyId: String,
        amount: CurrencyAmount,
        context: EconomyContext,
    ): EconomyMutationResult {
        if (currencyId != this.currencyId) return EconomyMutationResult.UnknownCurrency(id, currencyId)
        val economy = vaultEconomy() ?: return EconomyMutationResult.ProviderUnavailable(id, "Vault economy unavailable")
        val player = resolvePlayer(account) ?: return EconomyMutationResult.Failed(id, "unsupported account type")
        val before = BigDecimal.valueOf(economy.getBalance(player))
        val amountDecimal = toBigDecimal(amount)
        if (before < amountDecimal) {
            return EconomyMutationResult.InsufficientFunds(
                currentBalance = fromBigDecimal(before),
                attemptedAmount = amount,
            )
        }
        val response = economy.withdrawPlayer(player, amountDecimal.toDouble())
        if (!response.transactionSuccess()) {
            return EconomyMutationResult.Failed(id, response.errorMessage ?: "vault withdraw failed")
        }
        val after = BigDecimal.valueOf(economy.getBalance(player))
        return EconomyMutationResult.Success(
            transactionId = context.operationId?.let { "$it:${context.transactionKey ?: "vault"}" },
            beforeBalance = fromBigDecimal(before),
            afterBalance = fromBigDecimal(after),
        )
    }

    override suspend fun setBalance(
        account: CurrencyAccount,
        currencyId: String,
        amount: CurrencyAmount,
        context: EconomyContext,
    ): EconomyMutationResult {
        if (currencyId != this.currencyId) return EconomyMutationResult.UnknownCurrency(id, currencyId)
        val economy = vaultEconomy() ?: return EconomyMutationResult.ProviderUnavailable(id, "Vault economy unavailable")
        val player = resolvePlayer(account) ?: return EconomyMutationResult.Failed(id, "unsupported account type")
        val target = toBigDecimal(amount)
        val current = BigDecimal.valueOf(economy.getBalance(player))
        val delta = target.subtract(current)
        return when {
            delta.signum() > 0 -> deposit(account, currencyId, fromBigDecimal(delta), context)
            delta.signum() < 0 -> withdraw(account, currencyId, fromBigDecimal(delta.abs()), context)
            else -> EconomyMutationResult.Success(
                transactionId = context.operationId?.let { "$it:${context.transactionKey ?: "vault"}" },
                beforeBalance = fromBigDecimal(current),
                afterBalance = fromBigDecimal(current),
            )
        }
    }

    private fun vaultEconomy(): Economy? = server.servicesManager.getRegistration(Economy::class.java)?.provider

    private fun resolvePlayer(account: CurrencyAccount): org.bukkit.OfflinePlayer? {
        if (account.type != CurrencyAccountType.PLAYER) return null
        return runCatching { UUID.fromString(account.id) }
            .mapCatching { server.getOfflinePlayer(it) }
            .getOrNull()
    }

    private fun toBigDecimal(amount: CurrencyAmount): BigDecimal =
        amount.minorUnits.toBigDecimal().movePointLeft(decimals)

    private fun fromBigDecimal(value: BigDecimal): CurrencyAmount =
        CurrencyAmount.ofMinorUnits(
            value.movePointRight(decimals)
                .setScale(0, RoundingMode.HALF_UP)
                .toBigIntegerExact(),
        )
}

