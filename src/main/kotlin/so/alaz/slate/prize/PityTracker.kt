package so.alaz.slate.prize

import java.util.UUID
import so.alaz.slate.storage.StorageService

interface PityTracker {
    suspend fun current(uuid: UUID, crateId: String): Int
    suspend fun increment(uuid: UUID, crateId: String): Int
    suspend fun reset(uuid: UUID, crateId: String)
}

class StorageBackedPityTracker(
    private val storageService: StorageService,
) : PityTracker {
    override suspend fun current(uuid: UUID, crateId: String): Int =
        storageService.getPityCount(uuid, crateId)

    override suspend fun increment(uuid: UUID, crateId: String): Int {
        val next = current(uuid, crateId) + 1
        storageService.setPityCount(uuid, crateId, next)
        return next
    }

    override suspend fun reset(uuid: UUID, crateId: String) {
        storageService.setPityCount(uuid, crateId, 0)
    }
}
