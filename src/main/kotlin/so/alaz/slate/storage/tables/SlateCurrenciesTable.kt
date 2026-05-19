package so.alaz.slate.storage.tables

import org.jetbrains.exposed.sql.Table

object SlateCurrenciesTable : Table("slate_currencies") {
    val id = varchar("id", 64)
    val name = varchar("name", 128)
    val symbol = varchar("symbol", 32).nullable()
    val decimals = integer("decimals").default(0)
    val defaultBalance = text("default_balance").default("0")
    val minBalance = text("min_balance").default("0")
    val maxBalance = text("max_balance").nullable()
    val transferable = bool("transferable").default(false)
    val enabled = bool("enabled").default(true)
    val createdAtMillis = long("created_at")
    val updatedAtMillis = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}
