package so.alaz.slate.storage

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.nio.file.Files
import java.sql.DriverManager
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import so.alaz.slate.key.KeyType
import so.alaz.slate.storage.OpenOperationCreate
import so.alaz.slate.storage.OpenOperationState

class ExposedStorageTest {
    private val tempDir = Files.createTempDirectory("slate-storage-test")
    private val dbPath = tempDir.resolve("slate-test.db")

    @AfterEach
    fun cleanup() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `storage persists state across restart`() = runTest {
        val playerId = UUID.randomUUID()
        val crateId = "legendary"
        val openAt = Instant.parse("2026-01-01T00:00:00Z")

        val first = createStorage()
        try {
            val storage = first
            storage.initialize()
            storage.setVirtualKeys(playerId, crateId, 9)
            storage.setPityCount(playerId, crateId, 3)
            storage.setCooldown(playerId, crateId, openAt)
            storage.addPendingPrize(playerId, crateId, "diamond_pack")
            storage.logOpen(playerId, crateId, "diamond_pack", "Diamond Pack", KeyType.Virtual)
            storage.upsertCrateAnchor(crateId = crateId, worldKey = "minecraft:world", x = 10, y = 64, z = 10)
        } finally {
            first.shutdown()
        }

        val second = createStorage()
        try {
            val storage = second
            storage.initialize()
            assertEquals(9, storage.getVirtualKeys(playerId, crateId))
            assertEquals(3, storage.getPityCount(playerId, crateId))
            assertEquals(openAt, storage.getCooldown(playerId, crateId))
            assertEquals(1, storage.countOpens(playerId, crateId))
            val pending = storage.getPendingPrizes(playerId)
            assertEquals(1, pending.size)
            assertEquals("diamond_pack", pending.single().prizeId)
            val anchor = storage.getCrateAnchor(worldKey = "minecraft:world", x = 10, y = 64, z = 10)
            assertNotNull(anchor)
            assertEquals(crateId, anchor!!.crateId)
        } finally {
            second.shutdown()
        }
    }

    @Test
    fun `add and clear pending prizes`() = runTest {
        val playerId = UUID.randomUUID()
        val storage = createStorage()
        try {
            storage.initialize()
            storage.addPendingPrize(playerId, "rare", "prize_a")
            storage.addPendingPrize(playerId, "rare", "prize_b")
            assertEquals(2, storage.getPendingPrizes(playerId).size)
            storage.clearPendingPrizes(playerId)
            assertTrue(storage.getPendingPrizes(playerId).isEmpty())
        } finally {
            storage.shutdown()
        }
    }

    @Test
    fun `upsert and remove crate anchors`() = runTest {
        val storage = createStorage()
        try {
            storage.initialize()
            storage.upsertCrateAnchor(crateId = "alpha", worldKey = "minecraft:world", x = 1, y = 2, z = 3)
            storage.upsertCrateAnchor(crateId = "beta", worldKey = "minecraft:world", x = 1, y = 2, z = 3)
            val anchor = storage.getCrateAnchor(worldKey = "minecraft:world", x = 1, y = 2, z = 3)
            assertNotNull(anchor)
            assertEquals("beta", anchor!!.crateId)
            assertEquals(1, storage.listCrateAnchors().size)
            assertTrue(storage.removeCrateAnchor(worldKey = "minecraft:world", x = 1, y = 2, z = 3))
            assertEquals(0, storage.listCrateAnchors().size)
        } finally {
            storage.shutdown()
        }
    }

    @Test
    fun `migration recovers when V001 ran without later tables`() = runTest {
        createPartiallyMigratedV1Database()
        val playerId = UUID.randomUUID()
        val crateId = "legendary"
        val openAt = Instant.parse("2026-01-01T00:00:00Z")

        val storage = createStorage()
        try {
            storage.initialize()
            assertEquals(0, storage.getPityCount(playerId, crateId))
            assertEquals(null, storage.getCooldown(playerId, crateId))

            storage.setPityCount(playerId, crateId, 2)
            storage.setCooldown(playerId, crateId, openAt)
            storage.addPendingPrize(playerId, crateId, "diamond_pack")
            storage.logOpen(playerId, crateId, "diamond_pack", "Diamond Pack", KeyType.Virtual)

            assertEquals(2, storage.getPityCount(playerId, crateId))
            assertEquals(openAt, storage.getCooldown(playerId, crateId))
            assertEquals(1, storage.getPendingPrizes(playerId).size)
            assertEquals(1, storage.countOpens(playerId, crateId))
        } finally {
            storage.shutdown()
        }
    }

    @Test
    fun `tryConsumeVirtualKeys consumes atomically when balance is sufficient`() = runTest {
        val playerId = UUID.randomUUID()
        val crateId = "legendary"
        val storage = createStorage()
        try {
            storage.initialize()
            storage.setVirtualKeys(playerId, crateId, 5)

            assertTrue(storage.tryConsumeVirtualKeys(playerId, crateId, 3))
            assertEquals(2, storage.getVirtualKeys(playerId, crateId))

            assertTrue(!storage.tryConsumeVirtualKeys(playerId, crateId, 3))
            assertEquals(2, storage.getVirtualKeys(playerId, crateId))
        } finally {
            storage.shutdown()
        }
    }

    @Test
    fun `createOrGetOpenOperation reuses request id for idempotency`() = runTest {
        val playerId = UUID.randomUUID()
        val crateId = "legendary"
        val storage = createStorage()
        try {
            storage.initialize()
            val first = storage.createOrGetOpenOperation(
                OpenOperationCreate(
                    operationId = "op-1",
                    requestId = "req-1",
                    uuid = playerId,
                    crateId = crateId,
                    keyType = KeyType.Virtual,
                ),
            )
            val second = storage.createOrGetOpenOperation(
                OpenOperationCreate(
                    operationId = "op-2",
                    requestId = "req-1",
                    uuid = playerId,
                    crateId = crateId,
                    keyType = KeyType.Virtual,
                ),
            )
            assertEquals(first.operationId, second.operationId)
            assertEquals(OpenOperationState.CREATED, second.state)
        } finally {
            storage.shutdown()
        }
    }

    @Test
    fun `createOrGetOpenOperation accepts interaction request ids longer than legacy operation id limit`() = runTest {
        val playerId = UUID.randomUUID()
        val crateId = "slate"
        val requestId = "interact:$playerId:$crateId:12345678901234567890"
        assertTrue(requestId.length > 64)
        val storage = createStorage()
        try {
            storage.initialize()
            val operation = storage.createOrGetOpenOperation(
                OpenOperationCreate(
                    operationId = requestId,
                    requestId = requestId,
                    uuid = playerId,
                    crateId = crateId,
                    keyType = KeyType.Physical,
                ),
            )
            storage.appendOpenOperationStep(operation.operationId, OpenOperationState.KEY_CONSUMED, "key consumed")

            assertEquals(requestId, operation.operationId)
            assertEquals(requestId, operation.requestId)
            assertEquals(operation.operationId, storage.findOpenOperationByRequest(requestId, playerId, crateId)?.operationId)
        } finally {
            storage.shutdown()
        }
    }

    @Test
    fun `open operation updates and audit list work`() = runTest {
        val playerId = UUID.randomUUID()
        val crateId = "legendary"
        val storage = createStorage()
        try {
            storage.initialize()
            val created = storage.createOrGetOpenOperation(
                OpenOperationCreate(
                    operationId = "op-10",
                    requestId = null,
                    uuid = playerId,
                    crateId = crateId,
                    keyType = KeyType.Virtual,
                ),
            )
            storage.updateOpenOperation(
                operationId = created.operationId,
                state = OpenOperationState.DELIVERED,
                prizeId = "p1",
                errorMessage = null,
            )
            storage.appendOpenOperationStep(created.operationId, OpenOperationState.DELIVERED, "done")
            val recent = storage.listRecentOpenOperations(5)
            assertTrue(recent.isNotEmpty())
            assertEquals("op-10", recent.first().operationId)
        } finally {
            storage.shutdown()
        }
    }

    private fun createPartiallyMigratedV1Database() {
        DriverManager.getConnection("jdbc:sqlite:${dbPath.toAbsolutePath()}").use { connection ->
            connection.autoCommit = false
            connection.createStatement().use {
                it.execute(
                    """
                    CREATE TABLE IF NOT EXISTS slate_schema_version (
                        version INTEGER PRIMARY KEY,
                        applied_at TIMESTAMP NOT NULL
                    )
                    """.trimIndent()
                )
                it.execute(
                    """
                    CREATE TABLE IF NOT EXISTS virtual_keys (
                        uuid TEXT NOT NULL,
                        crate_id TEXT NOT NULL,
                        amount INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (uuid, crate_id)
                    )
                    """.trimIndent()
                )
                it.execute("INSERT INTO slate_schema_version(version, applied_at) VALUES (1, CURRENT_TIMESTAMP)")
            }
            connection.commit()
        }
    }

    private fun createStorage(): ExposedStorage {
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:${dbPath.toAbsolutePath()}"
            driverClassName = "org.sqlite.JDBC"
            maximumPoolSize = 1
            isAutoCommit = false
        }
        val dataSource = HikariDataSource(config)
        assertNotNull(dataSource)
        return ExposedStorage(dataSource, Dialect.Sqlite)
    }
}
