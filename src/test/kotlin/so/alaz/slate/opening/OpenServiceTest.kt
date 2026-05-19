package so.alaz.slate.opening

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.bukkit.Material
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.KeyProvider
import so.alaz.slate.crate.CrateManager
import so.alaz.slate.crate.CrateType
import so.alaz.slate.crate.KeyDefinition
import so.alaz.slate.crate.Prize
import so.alaz.slate.key.KeyService
import so.alaz.slate.key.KeyType
import so.alaz.slate.storage.NoopClusterCoordinator

class OpenServiceTest {
    @Test
    fun `openById serializes concurrent opens per player and crate`() = runTest {
        val crateManager = mockk<CrateManager>()
        val keyService = mockk<KeyService>()
        val openingPipeline = mockk<OpeningPipeline>()
        val service = OpenService(crateManager, keyService, openingPipeline, NoopClusterCoordinator)
        val player = mockk<Player>(relaxed = true)
        val crate = testCrate()
        val playerId = UUID.randomUUID()

        val inFlight = AtomicInteger(0)
        val maxInFlight = AtomicInteger(0)

        io.mockk.every { player.uniqueId } returns playerId
        io.mockk.every { crateManager.byId(crate.id) } returns crate
        coEvery { openingPipeline.isCooldownActive(playerId, crate, any()) } returns false
        coEvery { openingPipeline.setCooldownNow(playerId, crate) } returns Unit
        coEvery { openingPipeline.execute(player, crate, any(), any(), any(), any()) } coAnswers {
            val now = inFlight.incrementAndGet()
            maxInFlight.updateAndGet { current -> maxOf(current, now) }
            delay(50)
            inFlight.decrementAndGet()
            OpeningResult.Success("p1")
        }

        val results = listOf(
            async { service.openById(player, crate.id, 1) },
            async { service.openById(player, crate.id, 1) },
        ).awaitAll()

        assertEquals(1, maxInFlight.get())
        assertTrue(results.all { it is OpenBatchResult.Executed && it.opened == 1 })
    }

    @Test
    fun `openById uses a fresh request id batch for each command invocation`() = runTest {
        val crateManager = mockk<CrateManager>()
        val keyService = mockk<KeyService>()
        val openingPipeline = mockk<OpeningPipeline>()
        val service = OpenService(crateManager, keyService, openingPipeline, NoopClusterCoordinator)
        val player = mockk<Player>(relaxed = true)
        val crate = testCrate()
        val requestIds = mutableListOf<String>()
        val playerId = UUID.randomUUID()

        io.mockk.every { player.uniqueId } returns playerId
        io.mockk.every { crateManager.byId(crate.id) } returns crate
        coEvery { openingPipeline.isCooldownActive(playerId, crate, any()) } returns false
        coEvery { openingPipeline.setCooldownNow(playerId, crate) } returns Unit
        coEvery { openingPipeline.execute(player, crate, any(), any(), any(), any()) } coAnswers {
            requestIds += arg<String>(3)
            OpeningResult.Success("p1")
        }

        service.openById(player, crate.id, 2)
        service.openById(player, crate.id, 2)

        assertEquals(4, requestIds.size)
        assertEquals(4, requestIds.toSet().size)
        assertNotEquals(commandBatchId(requestIds[0]), commandBatchId(requestIds[2]))
        coVerify(exactly = 2) { openingPipeline.setCooldownNow(playerId, crate) }
    }

    @Test
    fun `openById checks cooldown once and skips per-attempt cooldown writes`() = runTest {
        val crateManager = mockk<CrateManager>()
        val keyService = mockk<KeyService>()
        val openingPipeline = mockk<OpeningPipeline>()
        val service = OpenService(crateManager, keyService, openingPipeline, NoopClusterCoordinator)
        val player = mockk<Player>(relaxed = true)
        val crate = testCrate()
        val playerId = UUID.randomUUID()

        io.mockk.every { player.uniqueId } returns playerId
        io.mockk.every { crateManager.byId(crate.id) } returns crate
        coEvery { openingPipeline.isCooldownActive(playerId, crate, any()) } returns false
        coEvery { openingPipeline.setCooldownNow(playerId, crate) } returns Unit
        coEvery { openingPipeline.execute(player, crate, KeyType.Virtual, any(), false, false) } returns OpeningResult.Success("p1")

        val result = service.openById(player, crate.id, 2)

        assertTrue(result is OpenBatchResult.Executed && result.opened == 2)
        coVerify(exactly = 1) { openingPipeline.isCooldownActive(playerId, crate, any()) }
        coVerify(exactly = 2) { openingPipeline.execute(player, crate, KeyType.Virtual, any(), false, false) }
        coVerify(exactly = 1) { openingPipeline.setCooldownNow(playerId, crate) }
    }

    @Test
    fun `openById returns cooldown failure before any attempt when preflight blocked`() = runTest {
        val crateManager = mockk<CrateManager>()
        val keyService = mockk<KeyService>()
        val openingPipeline = mockk<OpeningPipeline>()
        val service = OpenService(crateManager, keyService, openingPipeline, NoopClusterCoordinator)
        val player = mockk<Player>(relaxed = true)
        val crate = testCrate()
        val playerId = UUID.randomUUID()

        io.mockk.every { player.uniqueId } returns playerId
        io.mockk.every { crateManager.byId(crate.id) } returns crate
        coEvery { openingPipeline.isCooldownActive(playerId, crate, any()) } returns true

        val result = service.openById(player, crate.id, 2)
        val executed = result as OpenBatchResult.Executed

        assertEquals(0, executed.opened)
        assertEquals(FailureReason.ON_COOLDOWN, executed.failedReason)
        coVerify(exactly = 0) { openingPipeline.execute(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { openingPipeline.setCooldownNow(any(), any()) }
    }

    private fun commandBatchId(requestId: String): String = requestId.split(":")[3]

    private fun testCrate() = CrateDefinition(
        id = "basic",
        name = "Basic",
        type = CrateType.Unknown,
        keyDefinition = KeyDefinition("Key", emptyList(), Material.TRIPWIRE_HOOK, emptyList(), null, KeyProvider.Vanilla, null, false),
        prizes = listOf(
            Prize(
                id = "p1",
                name = "Prize",
                tier = null,
                weight = 1,
                broadcast = false,
                broadcastMessage = "",
                items = emptyList(),
                commands = emptyList(),
                messages = emptyList(),
            ),
        ),
    )
}
