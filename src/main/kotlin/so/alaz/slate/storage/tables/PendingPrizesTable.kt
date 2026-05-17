package so.alaz.slate.storage.tables

import org.jetbrains.exposed.dao.id.LongIdTable

object PendingPrizesTable : LongIdTable("pending_prizes") {
    val uuid = uuid("uuid")
    val crateId = varchar("crate_id", 128)
    val prizeId = varchar("prize_id", 128)
    val createdAtMillis = long("created_at")
}
