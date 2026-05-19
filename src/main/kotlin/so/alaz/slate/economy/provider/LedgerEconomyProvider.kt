package so.alaz.slate.economy.provider

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import kotlinx.coroutines.future.await
import org.bukkit.Server
import so.alaz.ledger.api.LedgerService
import so.alaz.ledger.api.model.AccountRef
import so.alaz.ledger.api.model.MoneyAmount
import so.alaz.ledger.api.model.TransactionContext
import so.alaz.ledger.api.result.LedgerFailureReason
import so.alaz.slate.currency.domain.CurrencyAccount
import so.alaz.slate.currency.domain.CurrencyAccountType
import so.alaz.slate.currency.domain.CurrencyAmount
import so.alaz.slate.economy.EconomyBalanceResult
import so.alaz.slate.economy.EconomyContext
import so.alaz.slate.economy.EconomyCurrency
import so.alaz.slate.economy.EconomyMutationResult
import so.alaz.slate.economy.EconomyProvider
import so.alaz.slate.economy.EconomyProviderCapabilities

class LedgerEconomyProvider(private val server: Server) : EconomyProvider {
    override val id: String = "ledger"
    override val displayName: String = "Ledger Economy"

    override fun isAvailable(): Boolean = ledgerService() != null

    override fun capabilities(): EconomyProviderCapabilities = EconomyProviderCapabilities(
        supportsMultipleCurrencies = true,
        supportsOfflinePlayers = true,
        supportsAtomicWithdraw = true,
        supportsRefund = true,
        supportsTransactionIds = true,
    )

    override fun currencies(): Collection<EconomyCurrency> {
        val service = ledgerService() ?: return emptyList()
        return service.currencies(LedgerService.PROVIDER_DEFAULT).map { definition ->
            EconomyCurrency(
                id = definition.id(),
                name = definition.name(),
                symbol = definition.symbol().orElse(null),
                decimals = definition.decimals(),
            )
        }
    }

    override suspend fun getBalance(account: CurrencyAccount, currencyId: String): EconomyBalanceResult {
        val service = ledgerService() ?: return EconomyBalanceResult.ProviderUnavailable(id, "Ledger service unavailable")
        val accountRef = account.toLedgerRef() ?: return EconomyBalanceResult.Failed(id, "unsupported account type")
        val result = service.getBalance(accountRef, LedgerService.PROVIDER_DEFAULT, currencyId).await()
        if (result.isSuccess) {
            val amount = result.amount().orElse(MoneyAmount.ZERO)
            return EconomyBalanceResult.Success(fromBigDecimal(amount.value()))
        }
        val error = result.error().orElse(null) ?: return EconomyBalanceResult.Failed(id, "ledger balance failed")
        return when (error.reason()) {
            LedgerFailureReason.UNKNOWN_CURRENCY,
            LedgerFailureReason.CURRENCY_DISABLED,
            -> EconomyBalanceResult.UnknownCurrency(id, currencyId)

            LedgerFailureReason.UNKNOWN_PROVIDER,
            LedgerFailureReason.PROVIDER_UNAVAILABLE,
            -> EconomyBalanceResult.ProviderUnavailable(id, error.message())

            else -> EconomyBalanceResult.Failed(id, error.message())
        }
    }

    override suspend fun deposit(
        account: CurrencyAccount,
        currencyId: String,
        amount: CurrencyAmount,
        context: EconomyContext,
    ): EconomyMutationResult = mutate(account, currencyId, amount, context, MutationType.DEPOSIT)

    override suspend fun withdraw(
        account: CurrencyAccount,
        currencyId: String,
        amount: CurrencyAmount,
        context: EconomyContext,
    ): EconomyMutationResult = mutate(account, currencyId, amount, context, MutationType.WITHDRAW)

    override suspend fun setBalance(
        account: CurrencyAccount,
        currencyId: String,
        amount: CurrencyAmount,
        context: EconomyContext,
    ): EconomyMutationResult = mutate(account, currencyId, amount, context, MutationType.SET)

    private suspend fun mutate(
        account: CurrencyAccount,
        currencyId: String,
        amount: CurrencyAmount,
        context: EconomyContext,
        mutationType: MutationType,
    ): EconomyMutationResult {
        val service = ledgerService() ?: return EconomyMutationResult.ProviderUnavailable(id, "Ledger service unavailable")
        val accountRef = account.toLedgerRef() ?: return EconomyMutationResult.Failed(id, "unsupported account type")
        val ledgerAmount = MoneyAmount.of(amount.minorUnits.toBigDecimal())
        val txContext = TransactionContext.builder()
            .source("slate")
            .reason(context.reason)
            .apply {
                context.operationId?.let(::operationId)
                context.transactionKey?.let(::transactionKey)
                account.actorIdOrNull()?.let(::actorId)
                if (context.metadata.isNotEmpty()) {
                    metadata(context.metadata)
                }
            }
            .build()

        val result = when (mutationType) {
            MutationType.DEPOSIT -> service.deposit(accountRef, LedgerService.PROVIDER_DEFAULT, currencyId, ledgerAmount, txContext)
            MutationType.WITHDRAW -> service.withdraw(accountRef, LedgerService.PROVIDER_DEFAULT, currencyId, ledgerAmount, txContext)
            MutationType.SET -> service.setBalance(accountRef, LedgerService.PROVIDER_DEFAULT, currencyId, ledgerAmount, txContext)
        }.await()

        if (result.isSuccess) {
            val transaction = result.transaction().orElse(null)
            return EconomyMutationResult.Success(
                transactionId = transaction?.id()?.toString(),
                beforeBalance = transaction?.beforeBalance()?.orElse(null)?.value()?.let(::fromBigDecimal),
                afterBalance = transaction?.afterBalance()?.orElse(null)?.value()?.let(::fromBigDecimal),
                replay = result.isReplay,
            )
        }
        val error = result.error().orElse(null) ?: return EconomyMutationResult.Failed(id, "ledger mutation failed")
        return when (error.reason()) {
            LedgerFailureReason.INSUFFICIENT_FUNDS -> EconomyMutationResult.InsufficientFunds(
                currentBalance = null,
                attemptedAmount = amount,
            )

            LedgerFailureReason.UNKNOWN_CURRENCY,
            LedgerFailureReason.CURRENCY_DISABLED,
            -> EconomyMutationResult.UnknownCurrency(id, currencyId)

            LedgerFailureReason.UNKNOWN_PROVIDER,
            LedgerFailureReason.PROVIDER_UNAVAILABLE,
            -> EconomyMutationResult.ProviderUnavailable(id, error.message())

            LedgerFailureReason.UNSUPPORTED_OPERATION -> EconomyMutationResult.UnsupportedOperation(id, error.message())
            else -> EconomyMutationResult.Failed(id, error.message())
        }
    }

    private fun ledgerService(): LedgerService? = server.servicesManager.load(LedgerService::class.java)

    private fun CurrencyAccount.toLedgerRef(): AccountRef? = when (type) {
        CurrencyAccountType.PLAYER -> runCatching { UUID.fromString(id) }.map(AccountRef::player).getOrNull()
        CurrencyAccountType.SERVER -> AccountRef.server(id)
        CurrencyAccountType.GUILD -> AccountRef.guild(id)
        CurrencyAccountType.GLOBAL -> AccountRef.global()
    }

    private fun CurrencyAccount.actorIdOrNull(): UUID? =
        if (type == CurrencyAccountType.PLAYER) runCatching { UUID.fromString(id) }.getOrNull() else null

    private fun fromBigDecimal(value: BigDecimal): CurrencyAmount =
        CurrencyAmount.ofMinorUnits(value.setScale(0, RoundingMode.HALF_EVEN).toBigIntegerExact())

    private enum class MutationType { DEPOSIT, WITHDRAW, SET }
}

