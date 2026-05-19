package so.alaz.slate.currency

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import so.alaz.slate.currency.domain.CurrencyAccount
import so.alaz.slate.currency.domain.CurrencyAmount
import so.alaz.slate.currency.domain.CurrencyDefinition
import so.alaz.slate.currency.domain.CurrencyId
import so.alaz.slate.currency.domain.CurrencyMutationResult
import so.alaz.slate.currency.domain.CurrencyTransactionContext
import so.alaz.slate.currency.provider.InternalSlateCurrencyProvider
import so.alaz.slate.currency.storage.ExposedCurrencyStorage
import so.alaz.slate.storage.Dialect
import so.alaz.slate.storage.MigrationRunner

class InternalSlateCurrencyProviderTest {
    private val tempDir = Files.createTempDirectory("slate-currency-provider-test")
    private val dbPath = tempDir.resolve("slate-currency-provider.db")

    @AfterEach
    fun cleanup() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `deposit and withdraw update balance and idempotent key prevents double charge`() = runTest {
        val dataSource = dataSource()
        dataSource.use { ds ->
            MigrationRunner.runMigrations(ds, Dialect.Sqlite)
            val storage = ExposedCurrencyStorage(ds)
            val definition = CurrencyDefinition(
                id = CurrencyId("astral_shards"),
                name = "Astral Shards",
                defaultBalance = CurrencyAmount.ofMinorUnits(0),
                minBalance = CurrencyAmount.ofMinorUnits(0),
            )
            storage.upsertCurrencyDefinition(definition)
            val provider = InternalSlateCurrencyProvider(storage) { listOf(definition) }
            val account = CurrencyAccount.player(UUID.randomUUID())

            val deposit = provider.deposit(
                account,
                "astral_shards",
                CurrencyAmount.ofMinorUnits(100),
                CurrencyTransactionContext(reason = "test-deposit"),
            )
            assertTrue(deposit is CurrencyMutationResult.Success)

            val withdrawKey = CurrencyTransactionContext(
                operationId = "op-1",
                transactionKey = "cost:0",
                reason = "test-withdraw",
            )
            val firstWithdraw = provider.withdraw(account, "astral_shards", CurrencyAmount.ofMinorUnits(60), withdrawKey)
            assertTrue(firstWithdraw is CurrencyMutationResult.Success)
            val replayWithdraw = provider.withdraw(account, "astral_shards", CurrencyAmount.ofMinorUnits(60), withdrawKey)
            assertTrue(replayWithdraw is CurrencyMutationResult.IdempotentReplay)

            val balance = provider.getBalance(account, "astral_shards")
            val amount = (balance as so.alaz.slate.currency.domain.BalanceResult.Success).balance
            assertEquals(CurrencyAmount.ofMinorUnits(40), amount)
        }
    }

    @Test
    fun `withdraw insufficient funds does not mutate balance`() = runTest {
        val dataSource = dataSource()
        dataSource.use { ds ->
            MigrationRunner.runMigrations(ds, Dialect.Sqlite)
            val storage = ExposedCurrencyStorage(ds)
            val definition = CurrencyDefinition(
                id = CurrencyId("relic_dust"),
                name = "Relic Dust",
                defaultBalance = CurrencyAmount.ofMinorUnits(0),
                minBalance = CurrencyAmount.ofMinorUnits(0),
            )
            storage.upsertCurrencyDefinition(definition)
            val provider = InternalSlateCurrencyProvider(storage) { listOf(definition) }
            val account = CurrencyAccount.player(UUID.randomUUID())

            provider.deposit(account, "relic_dust", CurrencyAmount.ofMinorUnits(20), CurrencyTransactionContext(reason = "seed"))
            val result = provider.withdraw(
                account,
                "relic_dust",
                CurrencyAmount.ofMinorUnits(40),
                CurrencyTransactionContext(reason = "insufficient"),
            )
            assertTrue(result is CurrencyMutationResult.InsufficientFunds)

            val balance = provider.getBalance(account, "relic_dust")
            assertEquals(
                CurrencyAmount.ofMinorUnits(20),
                (balance as so.alaz.slate.currency.domain.BalanceResult.Success).balance,
            )
        }
    }

    private fun dataSource(): HikariDataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = "jdbc:sqlite:${dbPath.toAbsolutePath()}"
                driverClassName = "org.sqlite.JDBC"
                maximumPoolSize = 1
                isAutoCommit = false
            },
        )
}
