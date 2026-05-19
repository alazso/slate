package so.alaz.slate.storage.tables

import org.jetbrains.exposed.sql.Table

object SlateCurrencyTransactionsTable : Table("slate_currency_transactions") {
    val id = varchar("id", 64)
    val operationId = varchar("operation_id", 255).nullable()
    val transactionKey = varchar("transaction_key", 255).nullable()
    val accountType = varchar("account_type", 32)
    val accountId = varchar("account_id", 128)
    val providerId = varchar("provider_id", 64)
    val currencyId = varchar("currency_id", 64)
    val type = varchar("type", 64)
    val amount = text("amount")
    val beforeBalance = text("before_balance").nullable()
    val afterBalance = text("after_balance").nullable()
    val status = varchar("status", 64)
    val reason = varchar("reason", 255)
    val metadataJson = text("metadata_json").nullable()
    val createdAtMillis = long("created_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, accountType, accountId, createdAtMillis)
        index(false, operationId)
        index(false, providerId, currencyId, createdAtMillis)
        index(true, operationId, transactionKey)
    }
}
