package so.alaz.slate.economy.provider

import so.alaz.slate.currency.domain.CurrencyAccount
import so.alaz.slate.currency.domain.CurrencyAmount
import so.alaz.slate.economy.EconomyBalanceResult
import so.alaz.slate.economy.EconomyContext
import so.alaz.slate.economy.EconomyCurrency
import so.alaz.slate.economy.EconomyMutationResult
import so.alaz.slate.economy.EconomyProvider
import so.alaz.slate.economy.EconomyProviderCapabilities

class NoOpEconomyProvider : EconomyProvider {
    override val id: String = "noop"
    override val displayName: String = "No Economy Provider"

    override fun isAvailable(): Boolean = false

    override fun capabilities(): EconomyProviderCapabilities = EconomyProviderCapabilities(
        supportsMultipleCurrencies = false,
        supportsOfflinePlayers = true,
        supportsAtomicWithdraw = false,
        supportsRefund = false,
        supportsTransactionIds = false,
    )

    override fun currencies(): Collection<EconomyCurrency> = emptyList()

    override suspend fun getBalance(account: CurrencyAccount, currencyId: String): EconomyBalanceResult =
        EconomyBalanceResult.ProviderUnavailable(id, "no economy backend available")

    override suspend fun deposit(
        account: CurrencyAccount,
        currencyId: String,
        amount: CurrencyAmount,
        context: EconomyContext,
    ): EconomyMutationResult = EconomyMutationResult.ProviderUnavailable(id, "no economy backend available")

    override suspend fun withdraw(
        account: CurrencyAccount,
        currencyId: String,
        amount: CurrencyAmount,
        context: EconomyContext,
    ): EconomyMutationResult = EconomyMutationResult.ProviderUnavailable(id, "no economy backend available")

    override suspend fun setBalance(
        account: CurrencyAccount,
        currencyId: String,
        amount: CurrencyAmount,
        context: EconomyContext,
    ): EconomyMutationResult = EconomyMutationResult.ProviderUnavailable(id, "no economy backend available")
}

