package so.alaz.slate.storage

import java.time.Duration
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import redis.clients.jedis.JedisPooled

class RedisClusterCoordinator(
    private val jedis: JedisPooled,
    private val lockTtl: Duration,
    private val acquireTimeout: Duration,
) : ClusterCoordinator {
    override suspend fun <T> withOpenLock(lockKey: String, action: suspend () -> T): T {
        val redisKey = "slate:lock:$lockKey"
        val token = UUID.randomUUID().toString()
        val deadline = System.currentTimeMillis() + acquireTimeout.toMillis()
        while (true) {
            val acquired = withContext(Dispatchers.IO) {
                jedis.set(redisKey, token, redis.clients.jedis.params.SetParams.setParams().nx().px(lockTtl.toMillis())) == "OK"
            }
            if (acquired) break
            if (System.currentTimeMillis() >= deadline) {
                throw IllegalStateException("Failed to acquire cluster lock for $lockKey")
            }
            delay(25)
        }
        try {
            return action()
        } finally {
            withContext(Dispatchers.IO) {
                val current = jedis.get(redisKey)
                if (current == token) {
                    jedis.del(redisKey)
                }
            }
        }
    }
}
