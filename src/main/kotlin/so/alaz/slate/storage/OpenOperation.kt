package so.alaz.slate.storage

import java.time.Instant
import java.util.UUID
import so.alaz.slate.key.KeyType

enum class OpenOperationState {
    CREATED,
    KEY_CONSUMED,
    PRIZE_SELECTED,
    DELIVERED,
    FAILED,
    RECOVERABLE,
}

data class OpenOperation(
    val operationId: String,
    val requestId: String?,
    val uuid: UUID,
    val crateId: String,
    val keyType: KeyType,
    val state: OpenOperationState,
    val prizeId: String?,
    val errorMessage: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class OpenOperationCreate(
    val operationId: String,
    val requestId: String?,
    val uuid: UUID,
    val crateId: String,
    val keyType: KeyType,
)

data class OpenOperationStep(
    val operationId: String,
    val state: OpenOperationState,
    val message: String?,
    val createdAt: Instant,
)

data class OpenHistoryEntry(
    val crateId: String,
    val prizeId: String,
    val prizeName: String,
    val keyType: KeyType,
    val openedAt: Instant,
)

data class GlobalOpenHistoryEntry(
    val uuid: UUID,
    val crateId: String,
    val prizeId: String,
    val prizeName: String,
    val keyType: KeyType,
    val openedAt: Instant,
)
