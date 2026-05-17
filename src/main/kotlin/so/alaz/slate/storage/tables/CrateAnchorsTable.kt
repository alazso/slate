package so.alaz.slate.storage.tables

import org.jetbrains.exposed.dao.id.LongIdTable

object CrateAnchorsTable : LongIdTable("crate_anchors") {
    val crateId = varchar("crate_id", 128)
    val worldKey = varchar("world_key", 128)
    val x = integer("x")
    val y = integer("y")
    val z = integer("z")
    val createdAtMillis = long("created_at")

    init {
        index(false, worldKey, x, y, z)
    }
}
