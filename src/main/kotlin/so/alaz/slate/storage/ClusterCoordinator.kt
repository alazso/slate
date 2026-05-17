package so.alaz.slate.storage

interface ClusterCoordinator {
    suspend fun <T> withOpenLock(lockKey: String, action: suspend () -> T): T
}

object NoopClusterCoordinator : ClusterCoordinator {
    override suspend fun <T> withOpenLock(lockKey: String, action: suspend () -> T): T = action()
}
