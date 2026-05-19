package so.alaz.slate.display

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import so.alaz.slate.crate.CrateManager
import so.alaz.slate.crate.anchor.AnchorService
import so.alaz.slate.crate.anchor.CrateAnchor
import so.alaz.slate.scheduler.PlatformScheduler

class DisplayServiceReconcileTest {
    @AfterEach
    fun tearDown() {
        clearAllMocks()
        unmockkAll()
    }

    @Test
    fun `reconcile chunk skips unloaded anchor chunks`() = runTest {
        mockkStatic(Bukkit::class)
        val crateManager = mockk<CrateManager>()
        val anchorService = mockk<AnchorService>()
        val scheduler = mockk<PlatformScheduler>()
        val factory = mockk<DisplayFactory>()
        val world = mockk<World>()
        val worldKey = NamespacedKey.minecraft("world")
        every { world.key } returns worldKey
        every { world.isChunkLoaded(0, 0) } returns false
        every { Bukkit.getWorld(worldKey) } returns world
        coEvery { anchorService.allAnchors() } returns listOf(
            CrateAnchor(crateId = "astral", worldKey = worldKey.asString(), x = 0, y = 64, z = 0),
        )
        every { scheduler.runGlobal(any()) } answers {
            firstArg<() -> Unit>().invoke()
            mockk(relaxed = true)
        }
        val service = DisplayService(
            pluginScope = this,
            scheduler = scheduler,
            crateManager = crateManager,
            anchorService = anchorService,
            factory = factory,
        )

        service.reconcileChunk(world, 0, 0)
        testScheduler.advanceUntilIdle()

        io.mockk.verify(exactly = 0) { crateManager.byId(any()) }
    }

    @Test
    fun `reconcile chunk only checks anchors in target chunk`() = runTest {
        mockkStatic(Bukkit::class)
        val crateManager = mockk<CrateManager>()
        val anchorService = mockk<AnchorService>()
        val scheduler = mockk<PlatformScheduler>()
        val factory = mockk<DisplayFactory>()
        val world = mockk<World>()
        val worldKey = NamespacedKey.minecraft("world")
        every { world.key } returns worldKey
        every { world.isChunkLoaded(any(), any()) } returns true
        every { Bukkit.getWorld(worldKey) } returns world
        coEvery { anchorService.allAnchors() } returns listOf(
            CrateAnchor(crateId = "match", worldKey = worldKey.asString(), x = 0, y = 64, z = 0),
            CrateAnchor(crateId = "other-chunk", worldKey = worldKey.asString(), x = 64, y = 64, z = 64),
            CrateAnchor(crateId = "other-world", worldKey = NamespacedKey.minecraft("nether").asString(), x = 0, y = 64, z = 0),
        )
        every { crateManager.byId("match") } returns mockk(relaxed = true)
        every { factory.crateDisplay(any(), any()) } returns null
        every { scheduler.runGlobal(any()) } answers {
            firstArg<() -> Unit>().invoke()
            mockk(relaxed = true)
        }
        val service = DisplayService(
            pluginScope = this,
            scheduler = scheduler,
            crateManager = crateManager,
            anchorService = anchorService,
            factory = factory,
        )

        service.reconcileChunk(world, 0, 0)
        testScheduler.advanceUntilIdle()

        io.mockk.verify(exactly = 1) { crateManager.byId("match") }
        io.mockk.verify(exactly = 0) { crateManager.byId("other-chunk") }
    }
}
