package so.alaz.slate.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

enum class DatabaseType {
    SQLITE,
    POSTGRES,
}

data class DatabaseConfig(
    val type: DatabaseType = DatabaseType.SQLITE,
    val host: String = "localhost",
    val port: Int = 5432,
    val name: String = "slate",
    val user: String = "slate",
    val password: String = "",
    val poolSize: Int = 10,
    val connectionTimeout: Duration = 30.seconds,
)
