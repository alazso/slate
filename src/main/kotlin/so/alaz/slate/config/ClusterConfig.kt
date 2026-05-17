package so.alaz.slate.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class ClusterConfig(
    val redisEnabled: Boolean = false,
    val redisHost: String = "localhost",
    val redisPort: Int = 6379,
    val redisPassword: String = "",
    val redisDatabase: Int = 0,
    val redisLockTtl: Duration = 5.seconds,
    val redisAcquireTimeout: Duration = 5.seconds,
)
