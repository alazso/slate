package so.alaz.slate.storage.tables

import org.jetbrains.exposed.sql.Table

object CooldownsTable : Table("cooldowns") {
    val uuid = uuid("uuid")
    val crateId = varchar("crate_id", 128)
    val lastOpenAtMillis = long("last_open_at")

    override val primaryKey = PrimaryKey(uuid, crateId)
}
