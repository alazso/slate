package so.alaz.slate.storage.tables

import org.jetbrains.exposed.sql.Table

object PityTable : Table("pity") {
    val uuid = uuid("uuid")
    val crateId = varchar("crate_id", 128)
    val count = integer("count").default(0)

    override val primaryKey = PrimaryKey(uuid, crateId)
}
