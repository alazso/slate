package so.alaz.slate.storage

import com.zaxxer.hikari.HikariDataSource
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import so.alaz.slate.crate.anchor.CrateAnchor
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import so.alaz.slate.storage.tables.CrateAnchorsTable
import so.alaz.slate.key.KeyType
import so.alaz.slate.storage.tables.CooldownsTable
import so.alaz.slate.storage.tables.OpenLogTable
import so.alaz.slate.storage.tables.OpenOperationStepsTable
import so.alaz.slate.storage.tables.OpenOperationsTable
import so.alaz.slate.storage.tables.PendingPrizesTable
import so.alaz.slate.storage.tables.PityTable
import so.alaz.slate.storage.tables.VirtualKeysTable

class ExposedStorage(
    private val dataSource: HikariDataSource,
    private val dialect: Dialect,
) : StorageService {
    private lateinit var database: Database

    override suspend fun initialize() {
        database = Database.connect(dataSource)
        MigrationRunner.runMigrations(dataSource, dialect)
    }

    override suspend fun shutdown() {
        dataSource.close()
    }

    override suspend fun getVirtualKeys(uuid: UUID, crateId: String): Int = tx {
        VirtualKeysTable
            .selectAll()
            .where { (VirtualKeysTable.uuid eq uuid) and (VirtualKeysTable.crateId eq crateId) }
            .singleOrNull()
            ?.get(VirtualKeysTable.amount)
            ?: 0
    }

    override suspend fun setVirtualKeys(uuid: UUID, crateId: String, amount: Int) {
        tx {
            VirtualKeysTable.insertIgnore {
                it[VirtualKeysTable.uuid] = uuid
                it[VirtualKeysTable.crateId] = crateId
                it[VirtualKeysTable.amount] = amount.coerceAtLeast(0)
            }
            VirtualKeysTable.update({ (VirtualKeysTable.uuid eq uuid) and (VirtualKeysTable.crateId eq crateId) }) {
                it[VirtualKeysTable.amount] = amount.coerceAtLeast(0)
            }
        }
    }

    override suspend fun addVirtualKeys(uuid: UUID, crateId: String, amount: Int) {
        tx {
            val current = VirtualKeysTable
                .selectAll()
                .where { (VirtualKeysTable.uuid eq uuid) and (VirtualKeysTable.crateId eq crateId) }
                .singleOrNull()
                ?.get(VirtualKeysTable.amount)
                ?: 0
            val updated = (current + amount).coerceAtLeast(0)
            VirtualKeysTable.insertIgnore {
                it[VirtualKeysTable.uuid] = uuid
                it[VirtualKeysTable.crateId] = crateId
                it[VirtualKeysTable.amount] = updated
            }
            VirtualKeysTable.update({ (VirtualKeysTable.uuid eq uuid) and (VirtualKeysTable.crateId eq crateId) }) {
                it[VirtualKeysTable.amount] = updated
            }
        }
    }

    override suspend fun tryConsumeVirtualKeys(uuid: UUID, crateId: String, amount: Int): Boolean = tx {
        if (amount <= 0) return@tx true
        VirtualKeysTable.insertIgnore {
            it[VirtualKeysTable.uuid] = uuid
            it[VirtualKeysTable.crateId] = crateId
            it[VirtualKeysTable.amount] = 0
        }
        val updatedRows = VirtualKeysTable.update({
            (VirtualKeysTable.uuid eq uuid) and
                (VirtualKeysTable.crateId eq crateId) and
                (VirtualKeysTable.amount greaterEq amount)
        }) {
            with(org.jetbrains.exposed.sql.SqlExpressionBuilder) {
                it.update(VirtualKeysTable.amount, VirtualKeysTable.amount - amount)
            }
        }
        updatedRows > 0
    }

    override suspend fun getPityCount(uuid: UUID, crateId: String): Int = tx {
        PityTable
            .selectAll()
            .where { (PityTable.uuid eq uuid) and (PityTable.crateId eq crateId) }
            .singleOrNull()
            ?.get(PityTable.count)
            ?: 0
    }

    override suspend fun setPityCount(uuid: UUID, crateId: String, count: Int) {
        tx {
            PityTable.insertIgnore {
                it[PityTable.uuid] = uuid
                it[PityTable.crateId] = crateId
                it[PityTable.count] = count.coerceAtLeast(0)
            }
            PityTable.update({ (PityTable.uuid eq uuid) and (PityTable.crateId eq crateId) }) {
                it[PityTable.count] = count.coerceAtLeast(0)
            }
        }
    }

    override suspend fun logOpen(uuid: UUID, crateId: String, prizeId: String, prizeName: String, keyType: KeyType) {
        tx {
            OpenLogTable.insert {
                it[OpenLogTable.uuid] = uuid
                it[OpenLogTable.crateId] = crateId
                it[OpenLogTable.prizeId] = prizeId
                it[OpenLogTable.prizeName] = prizeName
                it[OpenLogTable.keyType] = keyType.serialized
                it[OpenLogTable.openedAtMillis] = Instant.now().toEpochMilli()
            }
        }
    }

    override suspend fun addPendingPrize(uuid: UUID, crateId: String, prizeId: String) {
        tx {
            PendingPrizesTable.insert {
                it[PendingPrizesTable.uuid] = uuid
                it[PendingPrizesTable.crateId] = crateId
                it[PendingPrizesTable.prizeId] = prizeId
                it[PendingPrizesTable.createdAtMillis] = Instant.now().toEpochMilli()
            }
        }
    }

    override suspend fun getPendingPrizes(uuid: UUID): List<PendingPrize> = tx {
        PendingPrizesTable
            .selectAll()
            .where { PendingPrizesTable.uuid eq uuid }
            .orderBy(PendingPrizesTable.createdAtMillis to SortOrder.ASC)
            .map { it.toPendingPrize() }
    }

    override suspend fun clearPendingPrizes(uuid: UUID) {
        tx {
            PendingPrizesTable.deleteWhere { PendingPrizesTable.uuid eq uuid }
        }
    }

    override suspend fun getCooldown(uuid: UUID, crateId: String): Instant? = tx {
        CooldownsTable
            .selectAll()
            .where { (CooldownsTable.uuid eq uuid) and (CooldownsTable.crateId eq crateId) }
            .singleOrNull()
            ?.get(CooldownsTable.lastOpenAtMillis)
            ?.let(Instant::ofEpochMilli)
    }

    override suspend fun setCooldown(uuid: UUID, crateId: String, at: Instant) {
        tx {
            CooldownsTable.insertIgnore {
                it[CooldownsTable.uuid] = uuid
                it[CooldownsTable.crateId] = crateId
                it[CooldownsTable.lastOpenAtMillis] = at.toEpochMilli()
            }
            CooldownsTable.update({ (CooldownsTable.uuid eq uuid) and (CooldownsTable.crateId eq crateId) }) {
                it[CooldownsTable.lastOpenAtMillis] = at.toEpochMilli()
            }
        }
    }

    override suspend fun countOpens(uuid: UUID, crateId: String): Int = tx {
        OpenLogTable
            .selectAll()
            .where { (OpenLogTable.uuid eq uuid) and (OpenLogTable.crateId eq crateId) }
            .count()
            .toInt()
    }

    override suspend fun createOrGetOpenOperation(request: OpenOperationCreate): OpenOperation = tx {
        val now = Instant.now().toEpochMilli()
        val existing = request.requestId?.let { requestId ->
            OpenOperationsTable
                .selectAll()
                .where {
                    (OpenOperationsTable.requestId eq requestId) and
                        (OpenOperationsTable.uuid eq request.uuid) and
                        (OpenOperationsTable.crateId eq request.crateId)
                }
                .orderBy(OpenOperationsTable.updatedAtMillis to SortOrder.DESC)
                .limit(1)
                .singleOrNull()
                ?.toOpenOperation()
        }
        if (existing != null) {
            return@tx existing
        }
        OpenOperationsTable.insert {
            it[operationId] = request.operationId
            it[requestId] = request.requestId
            it[uuid] = request.uuid
            it[crateId] = request.crateId
            it[keyType] = request.keyType.serialized
            it[state] = OpenOperationState.CREATED.name
            it[prizeId] = null
            it[errorMessage] = null
            it[createdAtMillis] = now
            it[updatedAtMillis] = now
        }
        OpenOperationStepsTable.insert {
            it[OpenOperationStepsTable.operationId] = request.operationId
            it[OpenOperationStepsTable.state] = OpenOperationState.CREATED.name
            it[OpenOperationStepsTable.message] = "operation created"
            it[OpenOperationStepsTable.createdAtMillis] = now
        }
        OpenOperationsTable
            .selectAll()
            .where { OpenOperationsTable.operationId eq request.operationId }
            .single()
            .toOpenOperation()
    }

    override suspend fun findOpenOperationByRequest(requestId: String, uuid: UUID, crateId: String): OpenOperation? = tx {
        OpenOperationsTable
            .selectAll()
            .where {
                (OpenOperationsTable.requestId eq requestId) and
                    (OpenOperationsTable.uuid eq uuid) and
                    (OpenOperationsTable.crateId eq crateId)
            }
            .orderBy(OpenOperationsTable.updatedAtMillis to SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.toOpenOperation()
    }

    override suspend fun updateOpenOperation(
        operationId: String,
        state: OpenOperationState,
        prizeId: String?,
        errorMessage: String?,
    ): OpenOperation = tx {
        val now = Instant.now().toEpochMilli()
        OpenOperationsTable.update({ OpenOperationsTable.operationId eq operationId }) {
            it[OpenOperationsTable.state] = state.name
            it[OpenOperationsTable.prizeId] = prizeId
            it[OpenOperationsTable.errorMessage] = errorMessage
            it[OpenOperationsTable.updatedAtMillis] = now
        }
        OpenOperationsTable
            .selectAll()
            .where { OpenOperationsTable.operationId eq operationId }
            .single()
            .toOpenOperation()
    }

    override suspend fun appendOpenOperationStep(operationId: String, state: OpenOperationState, message: String?) {
        tx {
            OpenOperationStepsTable.insert {
                it[OpenOperationStepsTable.operationId] = operationId
                it[OpenOperationStepsTable.state] = state.name
                it[OpenOperationStepsTable.message] = message
                it[OpenOperationStepsTable.createdAtMillis] = Instant.now().toEpochMilli()
            }
        }
    }

    override suspend fun listRecentOpenOperations(limit: Int): List<OpenOperation> = tx {
        OpenOperationsTable
            .selectAll()
            .orderBy(OpenOperationsTable.updatedAtMillis to SortOrder.DESC)
            .limit(limit.coerceIn(1, 500))
            .map { it.toOpenOperation() }
    }

    override suspend fun listOpenHistory(uuid: UUID, limit: Int): List<OpenHistoryEntry> = tx {
        OpenLogTable
            .selectAll()
            .where { OpenLogTable.uuid eq uuid }
            .orderBy(OpenLogTable.openedAtMillis to SortOrder.DESC)
            .limit(limit.coerceIn(1, 200))
            .map {
                OpenHistoryEntry(
                    crateId = it[OpenLogTable.crateId],
                    prizeId = it[OpenLogTable.prizeId],
                    prizeName = it[OpenLogTable.prizeName],
                    keyType = it[OpenLogTable.keyType].toKeyType(),
                    openedAt = Instant.ofEpochMilli(it[OpenLogTable.openedAtMillis]),
                )
            }
    }

    override suspend fun upsertCrateAnchor(crateId: String, worldKey: String, x: Int, y: Int, z: Int) {
        tx {
            CrateAnchorsTable
                .selectAll()
                .where { (CrateAnchorsTable.worldKey eq worldKey) and (CrateAnchorsTable.x eq x) and (CrateAnchorsTable.y eq y) and (CrateAnchorsTable.z eq z) }
                .singleOrNull()
                ?.get(CrateAnchorsTable.id)
                ?.let { rowId ->
                    CrateAnchorsTable.update({ CrateAnchorsTable.id eq rowId }) {
                        it[CrateAnchorsTable.crateId] = crateId
                    }
                }
                ?: CrateAnchorsTable.insert {
                    it[CrateAnchorsTable.crateId] = crateId
                    it[CrateAnchorsTable.worldKey] = worldKey
                    it[CrateAnchorsTable.x] = x
                    it[CrateAnchorsTable.y] = y
                    it[CrateAnchorsTable.z] = z
                    it[CrateAnchorsTable.createdAtMillis] = Instant.now().toEpochMilli()
                }
        }
    }

    override suspend fun getCrateAnchor(worldKey: String, x: Int, y: Int, z: Int): CrateAnchor? = tx {
        CrateAnchorsTable
            .selectAll()
            .where { (CrateAnchorsTable.worldKey eq worldKey) and (CrateAnchorsTable.x eq x) and (CrateAnchorsTable.y eq y) and (CrateAnchorsTable.z eq z) }
            .singleOrNull()
            ?.toCrateAnchor()
    }

    override suspend fun removeCrateAnchor(worldKey: String, x: Int, y: Int, z: Int): Boolean = tx {
        CrateAnchorsTable.deleteWhere {
            (CrateAnchorsTable.worldKey eq worldKey) and
                (CrateAnchorsTable.x eq x) and
                (CrateAnchorsTable.y eq y) and
                (CrateAnchorsTable.z eq z)
        } > 0
    }

    override suspend fun listCrateAnchors(): List<CrateAnchor> = tx {
        CrateAnchorsTable
            .selectAll()
            .orderBy(CrateAnchorsTable.createdAtMillis to SortOrder.ASC)
            .map { it.toCrateAnchor() }
    }

    private suspend fun <T> tx(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO, database) { block() }

    private fun ResultRow.toPendingPrize() = PendingPrize(
        crateId = this[PendingPrizesTable.crateId],
        prizeId = this[PendingPrizesTable.prizeId],
        createdAt = Instant.ofEpochMilli(this[PendingPrizesTable.createdAtMillis]),
    )

    private fun ResultRow.toCrateAnchor() = CrateAnchor(
        crateId = this[CrateAnchorsTable.crateId],
        worldKey = this[CrateAnchorsTable.worldKey],
        x = this[CrateAnchorsTable.x],
        y = this[CrateAnchorsTable.y],
        z = this[CrateAnchorsTable.z],
    )

    private fun ResultRow.toOpenOperation() = OpenOperation(
        operationId = this[OpenOperationsTable.operationId],
        requestId = this[OpenOperationsTable.requestId],
        uuid = this[OpenOperationsTable.uuid],
        crateId = this[OpenOperationsTable.crateId],
        keyType = this[OpenOperationsTable.keyType].toKeyType(),
        state = OpenOperationState.valueOf(this[OpenOperationsTable.state]),
        prizeId = this[OpenOperationsTable.prizeId],
        errorMessage = this[OpenOperationsTable.errorMessage],
        createdAt = Instant.ofEpochMilli(this[OpenOperationsTable.createdAtMillis]),
        updatedAt = Instant.ofEpochMilli(this[OpenOperationsTable.updatedAtMillis]),
    )

    private val KeyType.serialized: String
        get() = when (this) {
            KeyType.Physical -> "PHYSICAL"
            KeyType.Virtual -> "VIRTUAL"
        }

    private fun String.toKeyType(): KeyType = when (uppercase()) {
        "PHYSICAL" -> KeyType.Physical
        else -> KeyType.Virtual
    }
}
