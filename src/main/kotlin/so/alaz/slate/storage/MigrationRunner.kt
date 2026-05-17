package so.alaz.slate.storage

import java.sql.Connection
import javax.sql.DataSource

/**
 * Migration resources are immutable once shipped and applied.
 * Any schema change must be introduced in a new V### migration file.
 */
object MigrationRunner {
    fun runMigrations(dataSource: DataSource, dialect: Dialect) {
        dataSource.connection.use { connection ->
            ensureVersionTable(connection)
            val current = currentVersion(connection)
            val migrations = when (dialect) {
                Dialect.Sqlite -> listOf(
                    1 to "storage/migrations/V001__init_sqlite.sql",
                    2 to "storage/migrations/V002__anchors_sqlite.sql",
                    3 to "storage/migrations/V003__missing_core_tables_sqlite.sql",
                    4 to "storage/migrations/V004__open_operations_sqlite.sql",
                    5 to "storage/migrations/V005__open_operation_id_lengths_sqlite.sql",
                )
                Dialect.Postgres -> listOf(
                    1 to "storage/migrations/V001__init_postgres.sql",
                    2 to "storage/migrations/V002__anchors_postgres.sql",
                    3 to "storage/migrations/V003__missing_core_tables_postgres.sql",
                    4 to "storage/migrations/V004__open_operations_postgres.sql",
                    5 to "storage/migrations/V005__open_operation_id_lengths_postgres.sql",
                )
            }
            migrations.filter { it.first > current }.forEach { migration ->
                val sql = readResource(migration.second)
                executeSqlScript(connection, sql)
                connection.prepareStatement(
                    "INSERT INTO slate_schema_version(version, applied_at) VALUES (?, CURRENT_TIMESTAMP)"
                ).use {
                    it.setInt(1, migration.first)
                    it.executeUpdate()
                }
            }
            connection.commit()
        }
    }

    private fun ensureVersionTable(connection: Connection) {
        connection.createStatement().use {
            it.execute(
                """
                CREATE TABLE IF NOT EXISTS slate_schema_version (
                    version INTEGER PRIMARY KEY,
                    applied_at TIMESTAMP NOT NULL
                )
                """.trimIndent()
            )
        }
        connection.commit()
    }

    private fun currentVersion(connection: Connection): Int {
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COALESCE(MAX(version), 0) FROM slate_schema_version").use {
                return if (it.next()) it.getInt(1) else 0
            }
        }
    }

    private fun readResource(path: String): String {
        return checkNotNull(javaClass.classLoader.getResourceAsStream(path)) {
            "Missing migration resource: $path"
        }.bufferedReader().readText()
    }

    private fun executeSqlScript(connection: Connection, script: String) {
        val statements = script
            .split(';')
            .map(String::trim)
            .filter(String::isNotBlank)
        connection.createStatement().use { statement ->
            for (sql in statements) {
                statement.execute(sql)
            }
        }
    }
}
