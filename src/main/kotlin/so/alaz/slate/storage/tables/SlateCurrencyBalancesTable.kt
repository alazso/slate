package so.alaz.slate.storage.tables

import org.jetbrains.exposed.sql.Table

object SlateCurrencyBalancesTable : Table("slate_currency_balances") {
    val accountType = varchar("account_type", 32)
    val accountId = varchar("account_id", 128)
    val currencyId = varchar("currency_id", 64)
    val balance = text("balance")
    val updatedAtMillis = long("updated_at")

    override val primaryKey = PrimaryKey(accountType, accountId, currencyId)

    init {
        index(false, currencyId)
    }
}
