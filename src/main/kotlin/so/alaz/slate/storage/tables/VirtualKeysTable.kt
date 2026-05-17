package so.alaz.slate.storage.tables

import org.jetbrains.exposed.sql.Table

object VirtualKeysTable : Table("virtual_keys") {
    val uuid = uuid("uuid")
    val crateId = varchar("crate_id", 128)
    val amount = integer("amount").default(0)

    override val primaryKey = PrimaryKey(uuid, crateId)
}
