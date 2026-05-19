package so.alaz.slate.currency.storage

import so.alaz.slate.currency.domain.CurrencyAccount
import so.alaz.slate.currency.domain.CurrencyAmount
import so.alaz.slate.currency.domain.CurrencyDefinition
import so.alaz.slate.currency.domain.CurrencyId
import so.alaz.slate.currency.domain.CurrencyTransaction

interface CurrencyStorage {
    suspend fun upsertCurrencyDefinition(definition: CurrencyDefinition)
    suspend fun getCurrencyDefinition(currencyId: String): CurrencyDefinition?
    suspend fun listCurrencyDefinitions(): List<CurrencyDefinition>

    suspend fun getOrCreateBalance(
        account: CurrencyAccount,
        currencyId: CurrencyId,
        defaultBalance: CurrencyAmount,
    ): CurrencyAmount

    suspend fun compareAndSetBalance(
        account: CurrencyAccount,
        currencyId: CurrencyId,
        expected: CurrencyAmount,
        next: CurrencyAmount,
    ): Boolean

    suspend fun insertTransaction(transaction: CurrencyTransaction)
    suspend fun findTransactionByOperationKey(operationId: String, transactionKey: String): CurrencyTransaction?
    suspend fun listTransactions(
        account: CurrencyAccount,
        providerId: String?,
        currencyId: String?,
        limit: Int,
    ): List<CurrencyTransaction>
}
