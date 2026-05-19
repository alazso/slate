package so.alaz.slate.currency.provider

import java.time.Instant
import java.util.UUID
import so.alaz.slate.currency.domain.BalanceResult
import so.alaz.slate.currency.domain.CurrencyAccount
import so.alaz.slate.currency.domain.CurrencyAmount
import so.alaz.slate.currency.domain.CurrencyDefinition
import so.alaz.slate.currency.domain.CurrencyId
import so.alaz.slate.currency.domain.CurrencyMutationResult
import so.alaz.slate.currency.domain.CurrencyProviderCapabilities
import so.alaz.slate.currency.domain.CurrencyTransaction
import so.alaz.slate.currency.domain.CurrencyTransactionContext
import so.alaz.slate.currency.domain.CurrencyTransactionStatus
import so.alaz.slate.currency.domain.CurrencyTransactionType
import so.alaz.slate.currency.spi.SlateCurrencyProvider
import so.alaz.slate.currency.storage.CurrencyStorage

class InternalSlateCurrencyProvider(
    private val storage: CurrencyStorage,
    private val definitionsSupplier: () -> Collection<CurrencyDefinition>,
) : SlateCurrencyProvider {
    override val id: String = "slate"
    override val displayName: String = "Slate Internal Currency"

    override fun isAvailable(): Boolean = true

    override fun capabilities(): CurrencyProviderCapabilities = CurrencyProviderCapabilities(
        supportsMultipleCurrencies = true,
        supportsOfflinePlayers = true,
        supportsAtomicWithdraw = true,
        supportsRefund = true,
        supportsTransactionIds = true,
        supportsNegativeBalances = false,
        isInternal = true,
    )

    override fun currencies(): Collection<CurrencyDefinition> = definitionsSupplier()

    override suspend fun getBalance(account: CurrencyAccount, currencyId: String): BalanceResult {
        val definition = findDefinition(currencyId)
            ?: return BalanceResult.UnknownCurrency(providerId = id, currencyId = currencyId)
        if (!definition.enabled) {
            return BalanceResult.UnknownCurrency(providerId = id, currencyId = currencyId)
        }
        val balance = storage.getOrCreateBalance(account, definition.id, definition.defaultBalance)
        return BalanceResult.Success(balance)
    }

    override suspend fun deposit(
        account: CurrencyAccount,
        currencyId: String,
        amount: CurrencyAmount,
        context: CurrencyTransactionContext,
    ): CurrencyMutationResult = mutate(
        account = account,
        currencyId = currencyId,
        amount = amount,
        context = context,
        type = CurrencyTransactionType.DEPOSIT,
    ) { current, delta -> current + delta }

    override suspend fun withdraw(
        account: CurrencyAccount,
        currencyId: String,
        amount: CurrencyAmount,
        context: CurrencyTransactionContext,
    ): CurrencyMutationResult {
        val definition = findDefinition(currencyId)
            ?: return CurrencyMutationResult.UnknownCurrency(providerId = id, currencyId = currencyId)
        if (!definition.enabled) {
            return CurrencyMutationResult.UnknownCurrency(providerId = id, currencyId = currencyId)
        }
        if (amount.isNegative() || amount.isZero()) {
            return CurrencyMutationResult.Failed(providerId = id, message = "amount must be greater than zero")
        }

        val replay = findReplay(context)
        if (replay != null) return replay

        while (true) {
            val current = storage.getOrCreateBalance(account, definition.id, definition.defaultBalance)
            if (current < amount) {
                storage.insertTransaction(
                    transaction(
                        id = UUID.randomUUID().toString(),
                        context = context,
                        account = account,
                        currencyId = definition.id,
                        type = CurrencyTransactionType.WITHDRAW,
                        amount = amount,
                        before = current,
                        after = current,
                        status = CurrencyTransactionStatus.FAILED,
                    ),
                )
                return CurrencyMutationResult.InsufficientFunds(currentBalance = current, attemptedAmount = amount)
            }
            val updated = current - amount
            if (updated < definition.minBalance) {
                return CurrencyMutationResult.Failed(providerId = id, message = "withdraw would violate min balance")
            }
            if (storage.compareAndSetBalance(account, definition.id, current, updated)) {
                val txId = UUID.randomUUID().toString()
                storage.insertTransaction(
                    transaction(
                        id = txId,
                        context = context,
                        account = account,
                        currencyId = definition.id,
                        type = CurrencyTransactionType.WITHDRAW,
                        amount = amount,
                        before = current,
                        after = updated,
                        status = CurrencyTransactionStatus.SUCCESS,
                    ),
                )
                return CurrencyMutationResult.Success(txId, current, updated)
            }
        }
    }

    override suspend fun setBalance(
        account: CurrencyAccount,
        currencyId: String,
        amount: CurrencyAmount,
        context: CurrencyTransactionContext,
    ): CurrencyMutationResult = mutate(
        account = account,
        currencyId = currencyId,
        amount = amount,
        context = context,
        type = CurrencyTransactionType.SET,
    ) { _, new -> new }

    private suspend fun mutate(
        account: CurrencyAccount,
        currencyId: String,
        amount: CurrencyAmount,
        context: CurrencyTransactionContext,
        type: CurrencyTransactionType,
        updater: (CurrencyAmount, CurrencyAmount) -> CurrencyAmount,
    ): CurrencyMutationResult {
        val definition = findDefinition(currencyId)
            ?: return CurrencyMutationResult.UnknownCurrency(providerId = id, currencyId = currencyId)
        if (!definition.enabled) {
            return CurrencyMutationResult.UnknownCurrency(providerId = id, currencyId = currencyId)
        }
        if (amount.isNegative() || (amount.isZero() && type != CurrencyTransactionType.SET)) {
            return CurrencyMutationResult.Failed(providerId = id, message = "amount must be valid for $type")
        }

        val replay = findReplay(context)
        if (replay != null) return replay

        while (true) {
            val current = storage.getOrCreateBalance(account, definition.id, definition.defaultBalance)
            val updated = updater(current, amount)
            if (!capabilities().supportsNegativeBalances && updated.isNegative()) {
                return CurrencyMutationResult.Failed(providerId = id, message = "negative balances are not supported")
            }
            if (updated < definition.minBalance) {
                return CurrencyMutationResult.Failed(providerId = id, message = "balance cannot go below currency minimum")
            }
            if (definition.maxBalance != null && updated > definition.maxBalance) {
                return CurrencyMutationResult.Failed(providerId = id, message = "balance cannot exceed currency maximum")
            }
            if (storage.compareAndSetBalance(account, definition.id, current, updated)) {
                val txId = UUID.randomUUID().toString()
                storage.insertTransaction(
                    transaction(
                        id = txId,
                        context = context,
                        account = account,
                        currencyId = definition.id,
                        type = type,
                        amount = amount,
                        before = current,
                        after = updated,
                        status = CurrencyTransactionStatus.SUCCESS,
                    ),
                )
                return CurrencyMutationResult.Success(txId, current, updated)
            }
        }
    }

    private fun findDefinition(currencyId: String): CurrencyDefinition? =
        definitionsSupplier().firstOrNull { it.id.value == currencyId }

    private suspend fun findReplay(context: CurrencyTransactionContext): CurrencyMutationResult.IdempotentReplay? {
        val op = context.operationId
        val key = context.transactionKey
        if (op.isNullOrBlank() || key.isNullOrBlank()) return null
        val previous = storage.findTransactionByOperationKey(op, key) ?: return null
        return CurrencyMutationResult.IdempotentReplay(
            transactionId = previous.id,
            beforeBalance = previous.beforeBalance,
            afterBalance = previous.afterBalance,
        )
    }

    private fun transaction(
        id: String,
        context: CurrencyTransactionContext,
        account: CurrencyAccount,
        currencyId: CurrencyId,
        type: CurrencyTransactionType,
        amount: CurrencyAmount,
        before: CurrencyAmount?,
        after: CurrencyAmount?,
        status: CurrencyTransactionStatus,
    ): CurrencyTransaction = CurrencyTransaction(
        id = id,
        operationId = context.operationId,
        transactionKey = context.transactionKey,
        account = account,
        providerId = this.id,
        currencyId = currencyId,
        type = type,
        amount = amount,
        beforeBalance = before,
        afterBalance = after,
        status = status,
        reason = context.reason,
        metadataJson = context.metadata.takeIf { it.isNotEmpty() }?.entries?.joinToString(
            separator = ",",
            prefix = "{",
            postfix = "}",
        ) { (k, v) -> "\"$k\":\"$v\"" },
        createdAt = Instant.now(),
    )
}
