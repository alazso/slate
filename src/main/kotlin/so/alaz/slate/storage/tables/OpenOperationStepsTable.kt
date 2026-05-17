package so.alaz.slate.storage.tables

import org.jetbrains.exposed.dao.id.LongIdTable

object OpenOperationStepsTable : LongIdTable("open_operation_steps") {
    val operationId = varchar("operation_id", 255)
    val state = varchar("state", 32)
    val message = varchar("message", 512).nullable()
    val createdAtMillis = long("created_at")

    init {
        index(false, operationId, createdAtMillis)
    }
}
