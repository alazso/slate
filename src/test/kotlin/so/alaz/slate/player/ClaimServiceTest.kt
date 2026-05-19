package so.alaz.slate.player

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.CrateManager
import so.alaz.slate.crate.KeyDefinition
import so.alaz.slate.crate.KeyProvider
import so.alaz.slate.crate.Prize
import so.alaz.slate.crate.PrizeItem
import so.alaz.slate.scheduler.CancellableTask
import so.alaz.slate.scheduler.PlatformScheduler
import so.alaz.slate.storage.PendingPrize
import so.alaz.slate.storage.StorageService
import so.alaz.slate.util.ItemUtil

class ClaimServiceTest {
    private lateinit var player: Player
    private lateinit var inventory: PlayerInventory
    private val playerId: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        mockkStatic(Bukkit::class)
        mockkObject(ItemUtil)
        player = mockk(relaxed = true)
        inventory = mockk(relaxed = true)
        every { player.uniqueId } returns playerId
        every { player.name } returns "Tester"
        every { player.inventory } returns inventory
        every { Bukkit.getConsoleSender() } returns mockk<ConsoleCommandSender>(relaxed = true)
        every { Bukkit.dispatchCommand(any(), any()) } returns true
        every { inventory.addItem(*anyVararg()) } returns hashMapOf()
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Bukkit::class)
        unmockkObject(ItemUtil)
    }

    @Test
    fun `empty pending returns zero summary`() = runTest {
        val storage = mockk<StorageService>(relaxed = true)
        coEvery { storage.getPendingPrizes(playerId) } returns emptyList()

        val service = ClaimService(storage, mockk<CrateManager>(relaxed = true), immediateScheduler())
        val summary = service.claimPending(player)

        assertEquals(ClaimSummary(0, 0, 0), summary)
        coVerify(exactly = 0) { storage.clearPendingPrizes(any()) }
    }

    @Test
    fun `successful delivery clears pending storage and reports delivered count`() = runTest {
        val storage = mockk<StorageService>(relaxed = true)
        val crateManager = mockk<CrateManager>(relaxed = true)
        val crate = stubCrate("vault", "reward")
        every { crateManager.byId("vault") } returns crate
        coEvery { storage.getPendingPrizes(playerId) } returns listOf(PendingPrize("vault", "reward", java.time.Instant.now()))
        every { ItemUtil.expandPrizeItem(any()) } returns listOf(mockk<ItemStack>(relaxed = true))

        val service = ClaimService(storage, crateManager, immediateScheduler())
        val summary = service.claimPending(player)

        assertEquals(ClaimSummary(total = 1, delivered = 1, remaining = 0), summary)
        coVerify(exactly = 1) { storage.clearPendingPrizes(playerId) }
        coVerify(exactly = 0) { storage.addPendingPrize(playerId, any(), any()) }
    }

    @Test
    fun `inventory overflow keeps prize pending`() = runTest {
        val storage = mockk<StorageService>(relaxed = true)
        val crateManager = mockk<CrateManager>(relaxed = true)
        val crate = stubCrate("vault", "reward")
        every { crateManager.byId("vault") } returns crate
        coEvery { storage.getPendingPrizes(playerId) } returns listOf(PendingPrize("vault", "reward", java.time.Instant.now()))
        every { ItemUtil.expandPrizeItem(any()) } returns listOf(mockk<ItemStack>(relaxed = true))
        every { inventory.addItem(*anyVararg()) } returns hashMapOf(0 to mockk<ItemStack>(relaxed = true))

        val service = ClaimService(storage, crateManager, immediateScheduler())
        val summary = service.claimPending(player)

        assertEquals(ClaimSummary(total = 1, delivered = 0, remaining = 1), summary)
        coVerify(exactly = 1) { storage.clearPendingPrizes(playerId) }
        coVerify(exactly = 1) { storage.addPendingPrize(playerId, "vault", "reward") }
    }

    @Test
    fun `missing crate keeps prize pending without invoking delivery`() = runTest {
        val storage = mockk<StorageService>(relaxed = true)
        val crateManager = mockk<CrateManager>(relaxed = true)
        every { crateManager.byId("missing") } returns null
        coEvery { storage.getPendingPrizes(playerId) } returns listOf(PendingPrize("missing", "p", java.time.Instant.now()))

        val service = ClaimService(storage, crateManager, immediateScheduler())
        val summary = service.claimPending(player)

        assertEquals(ClaimSummary(total = 1, delivered = 0, remaining = 1), summary)
        coVerify(exactly = 1) { storage.addPendingPrize(playerId, "missing", "p") }
    }

    private fun immediateScheduler(): PlatformScheduler {
        val scheduler = mockk<PlatformScheduler>()
        every { scheduler.runGlobal(any()) } answers {
            firstArg<() -> Unit>().invoke()
            object : CancellableTask {
                override fun cancel() {}
                override val isCancelled: Boolean = false
            }
        }
        every { scheduler.runForPlayerEntity(any(), any()) } answers {
            secondArg<() -> Unit>().invoke()
            object : CancellableTask {
                override fun cancel() {}
                override val isCancelled: Boolean = false
            }
        }
        return scheduler
    }

    private fun stubCrate(crateId: String, prizeId: String): CrateDefinition = CrateDefinition(
        id = crateId,
        name = crateId,
        keyDefinition = KeyDefinition(
            name = "Key",
            lore = emptyList(),
            material = Material.TRIPWIRE_HOOK,
            customModelData = emptyList(),
            customItemId = null,
            provider = KeyProvider.Vanilla,
            providerItemId = null,
            glowing = false,
        ),
        prizes = listOf(
            Prize(
                id = prizeId,
                name = "Reward",
                tier = null,
                weight = 1,
                chance = null,
                broadcast = false,
                broadcastMessage = "",
                items = listOf(PrizeItem(material = "DIAMOND")),
                commands = emptyList(),
                messages = emptyList(),
            ),
        ),
    )
}
