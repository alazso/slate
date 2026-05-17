package so.alaz.slate.storage.tables

import org.jetbrains.exposed.dao.id.LongIdTable

object OpenLogTable : LongIdTable("open_log") {
    val uuid = uuid("uuid")
    val crateId = varchar("crate_id", 128)
    val prizeId = varchar("prize_id", 128)
    val prizeName = varchar("prize_name", 255)
    val keyType = varchar("key_type", 32)
    val openedAtMillis = long("opened_at")

    init {
        index(false, uuid, crateId)
    }
}
