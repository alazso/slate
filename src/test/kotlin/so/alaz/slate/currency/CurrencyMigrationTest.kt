package so.alaz.slate.currency

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import so.alaz.slate.storage.Dialect
import so.alaz.slate.storage.MigrationRunner

class CurrencyMigrationTest {
    private val tempDir = Files.createTempDirectory("slate-currency-migration-test")
    private val dbPath = tempDir.resolve("slate-currency.db")

    @AfterEach
    fun cleanup() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `V007 creates currency tables`() = runTest {
        val dataSource = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = "jdbc:sqlite:${dbPath.toAbsolutePath()}"
                driverClassName = "org.sqlite.JDBC"
                maximumPoolSize = 1
                isAutoCommit = false
            },
        )
        dataSource.use { ds ->
            MigrationRunner.runMigrations(ds, Dialect.Sqlite)
            ds.connection.use { connection ->
                val tables = connection.metaData.getTables(null, null, "%", arrayOf("TABLE"))
                val names = mutableSetOf<String>()
                while (tables.next()) {
                    names += tables.getString("TABLE_NAME").lowercase()
                }
                assertTrue("slate_currencies" in names)
                assertTrue("slate_currency_balances" in names)
                assertTrue("slate_currency_transactions" in names)
            }
        }
    }
}
