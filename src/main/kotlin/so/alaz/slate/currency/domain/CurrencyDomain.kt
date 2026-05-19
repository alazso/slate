package so.alaz.slate.currency.domain

import java.time.Instant
import java.util.UUID

@JvmInline
value class CurrencyId(val value: String)

enum class CurrencyAccountType {
    PLAYER,
    SERVER,
    GUILD,
    GLOBAL,
}

data class CurrencyAccount(
    val type: CurrencyAccountType,
    val id: String,
) {
    companion object {
        fun player(uuid: UUID): CurrencyAccount = CurrencyAccount(CurrencyAccountType.PLAYER, uuid.toString())
    }
}

data class CurrencyDefinition(
    val id: CurrencyId,
    val name: String,
    val symbol: String? = null,
    val decimals: Int = 0,
    val defaultBalance: CurrencyAmount = CurrencyAmount.zero(),
    val minBalance: CurrencyAmount = CurrencyAmount.zero(),
    val maxBalance: CurrencyAmount? = null,
    val transferable: Boolean = false,
    val enabled: Boolean = true,
)

data class CurrencyProviderCapabilities(
    val supportsMultipleCurrencies: Boolean,
    val supportsOfflinePlayers: Boolean,
    val supportsAtomicWithdraw: Boolean,
    val supportsRefund: Boolean,
    val supportsTransactionIds: Boolean,
    val supportsNegativeBalances: Boolean,
    val isInternal: Boolean,
)

enum class CurrencyTransactionType {
    DEPOSIT,
    WITHDRAW,
    SET,
    REFUND,
    ADJUST,
    RESERVE,
    COMMIT_RESERVATION,
    CANCEL_RESERVATION,
    TRANSFER_IN,
    TRANSFER_OUT,
}

enum class CurrencyTransactionStatus {
    SUCCESS,
    FAILED,
    IDEMPOTENT_REPLAY,
    REFUNDED,
}

data class CurrencyTransactionContext(
    val operationId: String? = null,
    val transactionKey: String? = null,
    val reason: String,
    val metadata: Map<String, String> = emptyMap(),
)

data class CurrencyTransaction(
    val id: String,
    val operationId: String?,
    val transactionKey: String?,
    val account: CurrencyAccount,
    val providerId: String,
    val currencyId: CurrencyId,
    val type: CurrencyTransactionType,
    val amount: CurrencyAmount,
    val beforeBalance: CurrencyAmount?,
    val afterBalance: CurrencyAmount?,
    val status: CurrencyTransactionStatus,
    val reason: String,
    val metadataJson: String? = null,
    val createdAt: Instant,
)

sealed interface BalanceResult {
    data class Success(val balance: CurrencyAmount) : BalanceResult
    data class UnknownCurrency(val providerId: String, val currencyId: String) : BalanceResult
    data class ProviderUnavailable(val providerId: String) : BalanceResult
    data class Failed(val providerId: String, val message: String) : BalanceResult
}

sealed interface CurrencyMutationResult {
    data class Success(
        val transactionId: String?,
        val beforeBalance: CurrencyAmount?,
        val afterBalance: CurrencyAmount?,
    ) : CurrencyMutationResult

    data class InsufficientFunds(
        val currentBalance: CurrencyAmount,
        val attemptedAmount: CurrencyAmount,
    ) : CurrencyMutationResult

    data class IdempotentReplay(
        val transactionId: String?,
        val beforeBalance: CurrencyAmount?,
        val afterBalance: CurrencyAmount?,
    ) : CurrencyMutationResult

    data class UnknownCurrency(val providerId: String, val currencyId: String) : CurrencyMutationResult
    data class ProviderUnavailable(val providerId: String) : CurrencyMutationResult
    data class Failed(val providerId: String, val message: String) : CurrencyMutationResult
}
