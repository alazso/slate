package so.alaz.slate.storage

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.io.File
import so.alaz.slate.config.DatabaseConfig
import so.alaz.slate.config.DatabaseType

data class DatabaseFactoryResult(
    val dataSource: HikariDataSource,
    val dialect: Dialect,
)

object DatabaseFactory {
    fun create(dataFolder: File, config: DatabaseConfig): DatabaseFactoryResult {
        val hikari = HikariConfig().apply {
            maximumPoolSize = if (config.type == DatabaseType.SQLITE) 1 else config.poolSize
            connectionTimeout = config.connectionTimeout.inWholeMilliseconds
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        }

        val dialect = when (config.type) {
            DatabaseType.SQLITE -> {
                val dbFile = File(dataFolder, "slate.db")
                hikari.jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}"
                hikari.driverClassName = "org.sqlite.JDBC"
                Dialect.Sqlite
            }

            DatabaseType.POSTGRES -> {
                hikari.jdbcUrl = "jdbc:postgresql://${config.host}:${config.port}/${config.name}"
                hikari.driverClassName = "org.postgresql.Driver"
                hikari.username = config.user
                hikari.password = config.password
                Dialect.Postgres
            }
        }

        return DatabaseFactoryResult(HikariDataSource(hikari), dialect)
    }
}
