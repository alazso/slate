package so.alaz.slate.economy

import so.alaz.slate.currency.domain.CurrencyAccount
import so.alaz.slate.currency.domain.CurrencyAmount

interface EconomyProvider {
    val id: String
    val displayName: String

    fun isAvailable(): Boolean
    fun capabilities(): EconomyProviderCapabilities
    fun currencies(): Collection<EconomyCurrency>

    suspend fun getBalance(
        account: CurrencyAccount,
        currencyId: String,
    ): EconomyBalanceResult

    suspend fun deposit(
        account: CurrencyAccount,
        currencyId: String,
        amount: CurrencyAmount,
        context: EconomyContext,
    ): EconomyMutationResult

    suspend fun withdraw(
        account: CurrencyAccount,
        currencyId: String,
        amount: CurrencyAmount,
        context: EconomyContext,
    ): EconomyMutationResult

    suspend fun setBalance(
        account: CurrencyAccount,
        currencyId: String,
        amount: CurrencyAmount,
        context: EconomyContext,
    ): EconomyMutationResult
}

