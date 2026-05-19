package so.alaz.slate.economy

import so.alaz.slate.currency.domain.CurrencyAccount
import so.alaz.slate.currency.domain.CurrencyAmount

data class EconomyCurrency(
    val id: String,
    val name: String,
    val symbol: String? = null,
    val decimals: Int = 0,
)

data class EconomyProviderCapabilities(
    val supportsMultipleCurrencies: Boolean,
    val supportsOfflinePlayers: Boolean,
    val supportsAtomicWithdraw: Boolean,
    val supportsRefund: Boolean,
    val supportsTransactionIds: Boolean,
)

data class EconomyContext(
    val operationId: String? = null,
    val transactionKey: String? = null,
    val reason: String,
    val metadata: Map<String, String> = emptyMap(),
)

sealed interface EconomyBalanceResult {
    data class Success(val balance: CurrencyAmount) : EconomyBalanceResult
    data class UnknownCurrency(val providerId: String, val currencyId: String) : EconomyBalanceResult
    data class ProviderUnavailable(val providerId: String, val message: String? = null) : EconomyBalanceResult
    data class Failed(val providerId: String, val message: String) : EconomyBalanceResult
}

sealed interface EconomyMutationResult {
    data class Success(
        val transactionId: String?,
        val beforeBalance: CurrencyAmount?,
        val afterBalance: CurrencyAmount?,
        val replay: Boolean = false,
    ) : EconomyMutationResult

    data class InsufficientFunds(
        val currentBalance: CurrencyAmount?,
        val attemptedAmount: CurrencyAmount,
    ) : EconomyMutationResult

    data class UnknownCurrency(val providerId: String, val currencyId: String) : EconomyMutationResult
    data class ProviderUnavailable(val providerId: String, val message: String? = null) : EconomyMutationResult
    data class UnsupportedOperation(val providerId: String, val message: String) : EconomyMutationResult
    data class Failed(val providerId: String, val message: String) : EconomyMutationResult
}

internal fun EconomyBalanceResult.isInsufficientFor(amount: CurrencyAmount): Boolean =
    this is EconomyBalanceResult.Success && this.balance < amount

internal fun EconomyBalanceResult.asFailureOrNull(): EconomyMutationResult? = when (this) {
    is EconomyBalanceResult.Success -> null
    is EconomyBalanceResult.UnknownCurrency -> EconomyMutationResult.UnknownCurrency(providerId, currencyId)
    is EconomyBalanceResult.ProviderUnavailable -> EconomyMutationResult.ProviderUnavailable(providerId, message)
    is EconomyBalanceResult.Failed -> EconomyMutationResult.Failed(providerId, message)
}

