package so.alaz.slate.currency

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.nio.file.Files
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import so.alaz.slate.currency.domain.CurrencyAccount
import so.alaz.slate.currency.domain.CurrencyAmount
import so.alaz.slate.currency.domain.CurrencyId
import so.alaz.slate.currency.domain.CurrencyTransaction
import so.alaz.slate.currency.domain.CurrencyTransactionStatus
import so.alaz.slate.currency.domain.CurrencyTransactionType
import so.alaz.slate.currency.storage.ExposedCurrencyStorage
import so.alaz.slate.storage.Dialect
import so.alaz.slate.storage.MigrationRunner

class CurrencyHistoryTest {
    private val tempDir = Files.createTempDirectory("slate-currency-history-test")
    private val dbPath = tempDir.resolve("slate-currency-history.db")

    @AfterEach
    fun cleanup() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `transaction history returns newest first`() = runTest {
        val ds = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = "jdbc:sqlite:${dbPath.toAbsolutePath()}"
                driverClassName = "org.sqlite.JDBC"
                maximumPoolSize = 1
                isAutoCommit = false
            },
        )
        ds.use { dataSource ->
            MigrationRunner.runMigrations(dataSource, Dialect.Sqlite)
            val storage = ExposedCurrencyStorage(dataSource)
            val account = CurrencyAccount.player(UUID.randomUUID())
            val currencyId = CurrencyId("astral_shards")
            storage.insertTransaction(
                CurrencyTransaction(
                    id = "tx-old",
                    operationId = "op1",
                    transactionKey = "k1",
                    account = account,
                    providerId = "slate",
                    currencyId = currencyId,
                    type = CurrencyTransactionType.DEPOSIT,
                    amount = CurrencyAmount.ofMinorUnits(1),
                    beforeBalance = CurrencyAmount.ofMinorUnits(0),
                    afterBalance = CurrencyAmount.ofMinorUnits(1),
                    status = CurrencyTransactionStatus.SUCCESS,
                    reason = "old",
                    createdAt = Instant.ofEpochMilli(1000),
                ),
            )
            storage.insertTransaction(
                CurrencyTransaction(
                    id = "tx-new",
                    operationId = "op2",
                    transactionKey = "k2",
                    account = account,
                    providerId = "slate",
                    currencyId = currencyId,
                    type = CurrencyTransactionType.DEPOSIT,
                    amount = CurrencyAmount.ofMinorUnits(2),
                    beforeBalance = CurrencyAmount.ofMinorUnits(1),
                    afterBalance = CurrencyAmount.ofMinorUnits(3),
                    status = CurrencyTransactionStatus.SUCCESS,
                    reason = "new",
                    createdAt = Instant.ofEpochMilli(2000),
                ),
            )

            val history = storage.listTransactions(account, "slate", "astral_shards", 10)
            assertEquals(listOf("tx-new", "tx-old"), history.map { it.id })
        }
    }
}
