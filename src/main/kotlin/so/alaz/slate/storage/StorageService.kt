package so.alaz.slate.storage

import java.time.Instant
import java.util.UUID
import so.alaz.slate.crate.anchor.CrateAnchor
import so.alaz.slate.key.KeyType

interface StorageService {
    suspend fun getVirtualKeys(uuid: UUID, crateId: String): Int
    suspend fun setVirtualKeys(uuid: UUID, crateId: String, amount: Int)
    suspend fun addVirtualKeys(uuid: UUID, crateId: String, amount: Int)
    suspend fun tryConsumeVirtualKeys(uuid: UUID, crateId: String, amount: Int): Boolean
    suspend fun getPityCount(uuid: UUID, crateId: String): Int
    suspend fun setPityCount(uuid: UUID, crateId: String, count: Int)
    suspend fun logOpen(uuid: UUID, crateId: String, prizeId: String, prizeName: String, keyType: KeyType)
    suspend fun addPendingPrize(uuid: UUID, crateId: String, prizeId: String)
    suspend fun getPendingPrizes(uuid: UUID): List<PendingPrize>
    suspend fun clearPendingPrizes(uuid: UUID)
    suspend fun getCooldown(uuid: UUID, crateId: String): Instant?
    suspend fun setCooldown(uuid: UUID, crateId: String, at: Instant)
    suspend fun countOpens(uuid: UUID, crateId: String): Int
    suspend fun createOrGetOpenOperation(request: OpenOperationCreate): OpenOperation
    suspend fun findOpenOperationByRequest(requestId: String, uuid: UUID, crateId: String): OpenOperation?
    suspend fun updateOpenOperation(
        operationId: String,
        state: OpenOperationState,
        prizeId: String? = null,
        errorMessage: String? = null,
    ): OpenOperation
    suspend fun appendOpenOperationStep(operationId: String, state: OpenOperationState, message: String? = null)
    suspend fun listRecentOpenOperations(limit: Int): List<OpenOperation>
    suspend fun listOpenHistory(uuid: UUID, limit: Int): List<OpenHistoryEntry>
    suspend fun upsertCrateAnchor(crateId: String, worldKey: String, x: Int, y: Int, z: Int)
    suspend fun getCrateAnchor(worldKey: String, x: Int, y: Int, z: Int): CrateAnchor?
    suspend fun removeCrateAnchor(worldKey: String, x: Int, y: Int, z: Int): Boolean
    suspend fun listCrateAnchors(): List<CrateAnchor>
    suspend fun initialize()
    suspend fun shutdown()
}
