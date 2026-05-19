package so.alaz.slate.currency.spi

import so.alaz.slate.currency.domain.BalanceResult
import so.alaz.slate.currency.domain.CurrencyAccount
import so.alaz.slate.currency.domain.CurrencyAmount
import so.alaz.slate.currency.domain.CurrencyDefinition
import so.alaz.slate.currency.domain.CurrencyMutationResult
import so.alaz.slate.currency.domain.CurrencyProviderCapabilities
import so.alaz.slate.currency.domain.CurrencyTransactionContext

/**
 * Slate currency provider SPI.
 *
 * Future first-party adapter targets:
 * - CoinsEngine
 * - PlayerPoints
 * - EssentialsX
 */
interface SlateCurrencyProvider {
    val id: String
    val displayName: String

    fun isAvailable(): Boolean
    fun capabilities(): CurrencyProviderCapabilities
    fun currencies(): Collection<CurrencyDefinition>

    suspend fun getBalance(
        account: CurrencyAccount,
        currencyId: String,
    ): BalanceResult

    suspend fun deposit(
        account: CurrencyAccount,
        currencyId: String,
        amount: CurrencyAmount,
        context: CurrencyTransactionContext,
    ): CurrencyMutationResult

    suspend fun withdraw(
        account: CurrencyAccount,
        currencyId: String,
        amount: CurrencyAmount,
        context: CurrencyTransactionContext,
    ): CurrencyMutationResult

    suspend fun setBalance(
        account: CurrencyAccount,
        currencyId: String,
        amount: CurrencyAmount,
        context: CurrencyTransactionContext,
    ): CurrencyMutationResult
}
