package so.alaz.slate.storage

import java.time.Instant

data class PendingPrize(
    val crateId: String,
    val prizeId: String,
    val createdAt: Instant,
)
