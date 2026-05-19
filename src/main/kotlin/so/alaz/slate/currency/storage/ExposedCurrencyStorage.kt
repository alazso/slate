package so.alaz.slate.currency.storage

import com.zaxxer.hikari.HikariDataSource
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import so.alaz.slate.currency.domain.CurrencyAccount
import so.alaz.slate.currency.domain.CurrencyAccountType
import so.alaz.slate.currency.domain.CurrencyAmount
import so.alaz.slate.currency.domain.CurrencyDefinition
import so.alaz.slate.currency.domain.CurrencyId
import so.alaz.slate.currency.domain.CurrencyTransaction
import so.alaz.slate.currency.domain.CurrencyTransactionStatus
import so.alaz.slate.currency.domain.CurrencyTransactionType
import so.alaz.slate.storage.tables.SlateCurrenciesTable
import so.alaz.slate.storage.tables.SlateCurrencyBalancesTable
import so.alaz.slate.storage.tables.SlateCurrencyTransactionsTable

class ExposedCurrencyStorage(
    dataSource: HikariDataSource,
) : CurrencyStorage {
    private val database = Database.connect(dataSource)

    override suspend fun upsertCurrencyDefinition(definition: CurrencyDefinition) {
        tx {
            val now = Instant.now().toEpochMilli()
            SlateCurrenciesTable.insertIgnore {
                it[id] = definition.id.value
                it[name] = definition.name
                it[symbol] = definition.symbol
                it[decimals] = definition.decimals
                it[defaultBalance] = definition.defaultBalance.asPersistedString()
                it[minBalance] = definition.minBalance.asPersistedString()
                it[maxBalance] = definition.maxBalance?.asPersistedString()
                it[transferable] = definition.transferable
                it[enabled] = definition.enabled
                it[createdAtMillis] = now
                it[updatedAtMillis] = now
            }
            SlateCurrenciesTable.update({ SlateCurrenciesTable.id eq definition.id.value }) {
                it[name] = definition.name
                it[symbol] = definition.symbol
                it[decimals] = definition.decimals
                it[defaultBalance] = definition.defaultBalance.asPersistedString()
                it[minBalance] = definition.minBalance.asPersistedString()
                it[maxBalance] = definition.maxBalance?.asPersistedString()
                it[transferable] = definition.transferable
                it[enabled] = definition.enabled
                it[updatedAtMillis] = now
            }
        }
    }

    override suspend fun getCurrencyDefinition(currencyId: String): CurrencyDefinition? = tx {
        SlateCurrenciesTable.selectAll()
            .where { SlateCurrenciesTable.id eq currencyId }
            .singleOrNull()
            ?.toCurrencyDefinition()
    }

    override suspend fun listCurrencyDefinitions(): List<CurrencyDefinition> = tx {
        SlateCurrenciesTable.selectAll().map { it.toCurrencyDefinition() }
    }

    override suspend fun getOrCreateBalance(
        account: CurrencyAccount,
        currencyId: CurrencyId,
        defaultBalance: CurrencyAmount,
    ): CurrencyAmount = tx {
        val now = Instant.now().toEpochMilli()
        SlateCurrencyBalancesTable.insertIgnore {
            it[accountType] = account.type.name
            it[accountId] = account.id
            it[SlateCurrencyBalancesTable.currencyId] = currencyId.value
            it[balance] = defaultBalance.asPersistedString()
            it[updatedAtMillis] = now
        }
        SlateCurrencyBalancesTable.selectAll()
            .where {
                (SlateCurrencyBalancesTable.accountType eq account.type.name) and
                    (SlateCurrencyBalancesTable.accountId eq account.id) and
                    (SlateCurrencyBalancesTable.currencyId eq currencyId.value)
            }
            .single()
            .let { CurrencyAmount.fromPersistedString(it[SlateCurrencyBalancesTable.balance]) }
    }

    override suspend fun compareAndSetBalance(
        account: CurrencyAccount,
        currencyId: CurrencyId,
        expected: CurrencyAmount,
        next: CurrencyAmount,
    ): Boolean = tx {
        val rows = SlateCurrencyBalancesTable.update({
            (SlateCurrencyBalancesTable.accountType eq account.type.name) and
                (SlateCurrencyBalancesTable.accountId eq account.id) and
                (SlateCurrencyBalancesTable.currencyId eq currencyId.value) and
                (SlateCurrencyBalancesTable.balance eq expected.asPersistedString())
        }) {
            it[balance] = next.asPersistedString()
            it[updatedAtMillis] = Instant.now().toEpochMilli()
        }
        rows > 0
    }

    override suspend fun insertTransaction(transaction: CurrencyTransaction) {
        tx {
            SlateCurrencyTransactionsTable.insert {
                it[id] = transaction.id
                it[operationId] = transaction.operationId
                it[transactionKey] = transaction.transactionKey
                it[accountType] = transaction.account.type.name
                it[accountId] = transaction.account.id
                it[providerId] = transaction.providerId
                it[currencyId] = transaction.currencyId.value
                it[type] = transaction.type.name
                it[amount] = transaction.amount.asPersistedString()
                it[beforeBalance] = transaction.beforeBalance?.asPersistedString()
                it[afterBalance] = transaction.afterBalance?.asPersistedString()
                it[status] = transaction.status.name
                it[reason] = transaction.reason
                it[metadataJson] = transaction.metadataJson
                it[createdAtMillis] = transaction.createdAt.toEpochMilli()
            }
        }
    }

    override suspend fun findTransactionByOperationKey(operationId: String, transactionKey: String): CurrencyTransaction? = tx {
        SlateCurrencyTransactionsTable.selectAll()
            .where {
                (SlateCurrencyTransactionsTable.operationId eq operationId) and
                    (SlateCurrencyTransactionsTable.transactionKey eq transactionKey)
            }
            .singleOrNull()
            ?.toCurrencyTransaction()
    }

    override suspend fun listTransactions(
        account: CurrencyAccount,
        providerId: String?,
        currencyId: String?,
        limit: Int,
    ): List<CurrencyTransaction> = tx {
        SlateCurrencyTransactionsTable.selectAll()
            .where {
                var expr = (SlateCurrencyTransactionsTable.accountType eq account.type.name) and
                    (SlateCurrencyTransactionsTable.accountId eq account.id)
                if (!providerId.isNullOrBlank()) {
                    expr = expr and (SlateCurrencyTransactionsTable.providerId eq providerId)
                }
                if (!currencyId.isNullOrBlank()) {
                    expr = expr and (SlateCurrencyTransactionsTable.currencyId eq currencyId)
                }
                expr
            }
            .orderBy(SlateCurrencyTransactionsTable.createdAtMillis to SortOrder.DESC)
            .limit(limit.coerceIn(1, 200))
            .map { it.toCurrencyTransaction() }
    }

    private suspend fun <T> tx(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO, database) { block() }

    private fun ResultRow.toCurrencyDefinition(): CurrencyDefinition = CurrencyDefinition(
        id = CurrencyId(this[SlateCurrenciesTable.id]),
        name = this[SlateCurrenciesTable.name],
        symbol = this[SlateCurrenciesTable.symbol],
        decimals = this[SlateCurrenciesTable.decimals],
        defaultBalance = CurrencyAmount.fromPersistedString(this[SlateCurrenciesTable.defaultBalance]),
        minBalance = CurrencyAmount.fromPersistedString(this[SlateCurrenciesTable.minBalance]),
        maxBalance = this[SlateCurrenciesTable.maxBalance]?.let(CurrencyAmount::fromPersistedString),
        transferable = this[SlateCurrenciesTable.transferable],
        enabled = this[SlateCurrenciesTable.enabled],
    )

    private fun ResultRow.toCurrencyTransaction(): CurrencyTransaction = CurrencyTransaction(
        id = this[SlateCurrencyTransactionsTable.id],
        operationId = this[SlateCurrencyTransactionsTable.operationId],
        transactionKey = this[SlateCurrencyTransactionsTable.transactionKey],
        account = CurrencyAccount(
            type = CurrencyAccountType.valueOf(this[SlateCurrencyTransactionsTable.accountType]),
            id = this[SlateCurrencyTransactionsTable.accountId],
        ),
        providerId = this[SlateCurrencyTransactionsTable.providerId],
        currencyId = CurrencyId(this[SlateCurrencyTransactionsTable.currencyId]),
        type = CurrencyTransactionType.valueOf(this[SlateCurrencyTransactionsTable.type]),
        amount = CurrencyAmount.fromPersistedString(this[SlateCurrencyTransactionsTable.amount]),
        beforeBalance = this[SlateCurrencyTransactionsTable.beforeBalance]?.let(CurrencyAmount::fromPersistedString),
        afterBalance = this[SlateCurrencyTransactionsTable.afterBalance]?.let(CurrencyAmount::fromPersistedString),
        status = CurrencyTransactionStatus.valueOf(this[SlateCurrencyTransactionsTable.status]),
        reason = this[SlateCurrencyTransactionsTable.reason],
        metadataJson = this[SlateCurrencyTransactionsTable.metadataJson],
        createdAt = Instant.ofEpochMilli(this[SlateCurrencyTransactionsTable.createdAtMillis]),
    )
}
