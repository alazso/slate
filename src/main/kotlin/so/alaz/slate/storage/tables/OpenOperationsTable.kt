package so.alaz.slate.storage.tables

import org.jetbrains.exposed.sql.Table

object OpenOperationsTable : Table("open_operations") {
    val operationId = varchar("operation_id", 255)
    val requestId = varchar("request_id", 255).nullable()
    val uuid = uuid("uuid")
    val crateId = varchar("crate_id", 128)
    val keyType = varchar("key_type", 32)
    val state = varchar("state", 32)
    val prizeId = varchar("prize_id", 128).nullable()
    val errorMessage = varchar("error_message", 512).nullable()
    val createdAtMillis = long("created_at")
    val updatedAtMillis = long("updated_at")

    override val primaryKey = PrimaryKey(operationId)

    init {
        index(false, requestId, uuid, crateId)
        index(false, state, updatedAtMillis)
    }
}
