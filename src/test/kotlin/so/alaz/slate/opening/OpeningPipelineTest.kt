package so.alaz.slate.opening

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.bukkit.Bukkit
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.plugin.PluginManager
import org.bukkit.inventory.PlayerInventory
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.CrateEligibilityService
import so.alaz.slate.crate.CrateLifecycleConfig
import so.alaz.slate.crate.CratePermissionRequirements
import so.alaz.slate.crate.CrateRequirements
import so.alaz.slate.crate.CrateState
import so.alaz.slate.crate.KeyProvider
import so.alaz.slate.crate.CrateType
import so.alaz.slate.crate.KeyDefinition
import so.alaz.slate.crate.Prize
import so.alaz.slate.key.KeyService
import so.alaz.slate.key.KeyType
import so.alaz.slate.prize.PityTracker
import so.alaz.slate.prize.PrizeEngine
import so.alaz.slate.scheduler.CancellableTask
import so.alaz.slate.scheduler.PlatformScheduler
import so.alaz.slate.storage.OpenOperation
import so.alaz.slate.storage.OpenOperationCreate
import so.alaz.slate.storage.OpenOperationState
import so.alaz.slate.storage.StorageService
import so.alaz.slate.util.ItemUtil
import java.time.Instant

class OpeningPipelineTest {
    private lateinit var player: Player
    private lateinit var inventory: PlayerInventory
    private lateinit var pluginManager: PluginManager

    @BeforeEach
    fun setup() {
        mockkStatic(Bukkit::class)
        mockkObject(ItemUtil)

        val world = mockk<World>()
        every { world.name } returns "world"
        every { world.key } returns NamespacedKey.minecraft("overworld")
        every { world.environment } returns World.Environment.NORMAL

        inventory = mockk(relaxed = true)
        player = mockk(relaxed = true)
        every { player.uniqueId } returns UUID.randomUUID()
        every { player.world } returns world
        every { player.name } returns "TestPlayer"
        every { player.inventory } returns inventory
        every { player.hasPermission(any<String>()) } returns true

        pluginManager = mockk(relaxed = true)
        val server = mockk<Server>(relaxed = true)
        every { Bukkit.getPluginManager() } returns pluginManager
        every { Bukkit.getServer() } returns server
        every { server.pluginManager } returns pluginManager
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
    fun `no key returns failure before bukkit calls`() = runTest {
        val keyService = mockk<KeyService>()
        val prizeEngine = mockk<PrizeEngine>()
        val pityTracker = mockk<PityTracker>()
        val storage = mockk<StorageService>(relaxed = true)
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
        val crate = testCrate()
        coEvery { keyService.countKeys(player, crate, KeyType.Virtual) } returns 0
        coEvery { storage.getCooldown(any(), any()) } returns null
        coEvery { storage.countOpens(any(), any()) } returns 0
        stubCreatedOperation(storage, player.uniqueId, crate.id, KeyType.Virtual)

        val pipeline = OpeningPipeline(keyService, prizeEngine, pityTracker, storage, scheduler, CrateEligibilityService())
        val result = pipeline.execute(player, crate, KeyType.Virtual)

        assertEquals(OpeningResult.Failure(FailureReason.NO_KEY, requiredKeys = 1, availableKeys = 0), result)
    }

    @Test
    fun `insufficient keys returns amount-aware failure`() = runTest {
        val keyService = mockk<KeyService>()
        val prizeEngine = mockk<PrizeEngine>()
        val pityTracker = mockk<PityTracker>()
        val storage = mockk<StorageService>(relaxed = true)
        val scheduler = immediateScheduler()
        val crate = testCrate(allowedWorlds = emptySet()).copy(requiredKeys = 3)

        coEvery { keyService.countKeys(player, crate, KeyType.Virtual) } returns 1
        coEvery { storage.getCooldown(any(), any()) } returns null
        coEvery { storage.countOpens(any(), any()) } returns 0
        stubCreatedOperation(storage, player.uniqueId, crate.id, KeyType.Virtual)

        val pipeline = OpeningPipeline(keyService, prizeEngine, pityTracker, storage, scheduler, CrateEligibilityService())
        val result = pipeline.execute(player, crate, KeyType.Virtual)

        assertEquals(
            OpeningResult.Failure(FailureReason.INSUFFICIENT_KEYS, requiredKeys = 3, availableKeys = 1),
            result,
        )
    }

    @Test
    fun `item delivery success logs open and cooldown`() = runTest {
        val keyService = mockk<KeyService>()
        val prizeEngine = mockk<PrizeEngine>()
        val pityTracker = mockk<PityTracker>()
        val storage = mockk<StorageService>(relaxed = true)
        val scheduler = immediateScheduler()
        val crate = testCrate(
            prize = Prize(
                id = "p-item",
                name = "Item Prize",
                tier = null,
                weight = 1,
                broadcast = false,
                broadcastMessage = "",
                items = listOf(so.alaz.slate.crate.PrizeItem("DIAMOND", amount = 3)),
                commands = emptyList(),
                messages = emptyList(),
            ),
        )
        val prize = crate.prizes.single()
        val playerId = player.uniqueId

        coEvery { storage.getCooldown(any(), any()) } returns null
        coEvery { storage.countOpens(any(), any()) } returns 0
        coEvery { keyService.countKeys(player, crate, KeyType.Virtual) } returns crate.requiredKeys
        coEvery { keyService.consumeKey(player, crate, KeyType.Virtual) } returns true
        coEvery { pityTracker.increment(any(), any()) } returns 1
        coEvery { prizeEngine.selectPrize(crate, null, 1) } returns prize
        every { ItemUtil.expandPrizeItem(any()) } returns listOf(mockk<ItemStack>(relaxed = true))
        stubCreatedOperation(storage, player.uniqueId, crate.id, KeyType.Virtual)

        val pipeline = OpeningPipeline(keyService, prizeEngine, pityTracker, storage, scheduler, CrateEligibilityService())
        val result = pipeline.execute(player, crate, KeyType.Virtual)

        assertEquals(OpeningResult.Success(prize.id), result)
        coVerify(exactly = 1) { storage.logOpen(playerId, crate.id, prize.id, prize.name, KeyType.Virtual) }
        coVerify(exactly = 1) { storage.setCooldown(playerId, crate.id, any()) }
        coVerify(exactly = 0) { storage.addPendingPrize(any(), any(), any()) }
    }

    @Test
    fun `cooldown check can be skipped for batch follow-up attempts`() = runTest {
        val keyService = mockk<KeyService>()
        val prizeEngine = mockk<PrizeEngine>()
        val pityTracker = mockk<PityTracker>()
        val storage = mockk<StorageService>(relaxed = true)
        val scheduler = immediateScheduler()
        val crate = testCrate()
        val prize = crate.prizes.single()

        coEvery { storage.getCooldown(any(), any()) } returns Instant.now()
        coEvery { storage.countOpens(any(), any()) } returns 0
        coEvery { keyService.countKeys(player, crate, KeyType.Virtual) } returns crate.requiredKeys
        coEvery { keyService.consumeKey(player, crate, KeyType.Virtual) } returns true
        coEvery { pityTracker.increment(any(), any()) } returns 1
        coEvery { prizeEngine.selectPrize(crate, null, 1) } returns prize
        stubCreatedOperation(storage, player.uniqueId, crate.id, KeyType.Virtual)

        val pipeline = OpeningPipeline(keyService, prizeEngine, pityTracker, storage, scheduler, CrateEligibilityService())
        val result = pipeline.execute(player, crate, KeyType.Virtual, checkCooldown = false)

        assertEquals(OpeningResult.Success(prize.id), result)
    }

    @Test
    fun `cooldown write can be disabled for batch-managed command opens`() = runTest {
        val keyService = mockk<KeyService>()
        val prizeEngine = mockk<PrizeEngine>()
        val pityTracker = mockk<PityTracker>()
        val storage = mockk<StorageService>(relaxed = true)
        val scheduler = immediateScheduler()
        val crate = testCrate()
        val prize = crate.prizes.single()

        coEvery { storage.getCooldown(any(), any()) } returns null
        coEvery { storage.countOpens(any(), any()) } returns 0
        coEvery { keyService.countKeys(player, crate, KeyType.Virtual) } returns crate.requiredKeys
        coEvery { keyService.consumeKey(player, crate, KeyType.Virtual) } returns true
        coEvery { pityTracker.increment(any(), any()) } returns 1
        coEvery { prizeEngine.selectPrize(crate, null, 1) } returns prize
        coEvery { storage.setCooldown(any(), any(), any()) } throws AssertionError("setCooldown should not be called when writeCooldown=false")
        stubCreatedOperation(storage, player.uniqueId, crate.id, KeyType.Virtual)

        val pipeline = OpeningPipeline(keyService, prizeEngine, pityTracker, storage, scheduler, CrateEligibilityService())
        val result = pipeline.execute(player, crate, KeyType.Virtual, writeCooldown = false)

        assertTrue(result is OpeningResult.Success)
    }

    @Test
    fun `allowed worlds accepts configured world name when bukkit key is overworld`() = runTest {
        val keyService = mockk<KeyService>()
        val prizeEngine = mockk<PrizeEngine>()
        val pityTracker = mockk<PityTracker>()
        val storage = mockk<StorageService>(relaxed = true)
        val scheduler = immediateScheduler()
        val crate = testCrate(allowedWorlds = setOf("world"))
        val prize = crate.prizes.single()

        coEvery { storage.getCooldown(any(), any()) } returns null
        coEvery { storage.countOpens(any(), any()) } returns 0
        coEvery { keyService.countKeys(player, crate, KeyType.Virtual) } returns crate.requiredKeys
        coEvery { keyService.consumeKey(player, crate, KeyType.Virtual) } returns true
        coEvery { pityTracker.increment(any(), any()) } returns 1
        coEvery { prizeEngine.selectPrize(crate, null, 1) } returns prize
        stubCreatedOperation(storage, player.uniqueId, crate.id, KeyType.Virtual)

        val pipeline = OpeningPipeline(keyService, prizeEngine, pityTracker, storage, scheduler, CrateEligibilityService())
        val result = pipeline.execute(player, crate, KeyType.Virtual)

        assertEquals(OpeningResult.Success(prize.id), result)
    }

    @Test
    fun `allowed worlds accepts overworld alias for custom named normal world`() = runTest {
        val customWorld = mockk<World>()
        every { customWorld.name } returns "site"
        every { customWorld.key } returns NamespacedKey.minecraft("site")
        every { customWorld.environment } returns World.Environment.NORMAL
        every { player.world } returns customWorld

        val keyService = mockk<KeyService>()
        val prizeEngine = mockk<PrizeEngine>()
        val pityTracker = mockk<PityTracker>()
        val storage = mockk<StorageService>(relaxed = true)
        val scheduler = immediateScheduler()
        val crate = testCrate(allowedWorlds = setOf("minecraft:overworld"))
        val prize = crate.prizes.single()

        coEvery { storage.getCooldown(any(), any()) } returns null
        coEvery { storage.countOpens(any(), any()) } returns 0
        coEvery { keyService.countKeys(player, crate, KeyType.Virtual) } returns crate.requiredKeys
        coEvery { keyService.consumeKey(player, crate, KeyType.Virtual) } returns true
        coEvery { pityTracker.increment(any(), any()) } returns 1
        coEvery { prizeEngine.selectPrize(crate, null, 1) } returns prize
        stubCreatedOperation(storage, player.uniqueId, crate.id, KeyType.Virtual)

        val pipeline = OpeningPipeline(keyService, prizeEngine, pityTracker, storage, scheduler, CrateEligibilityService())
        val result = pipeline.execute(player, crate, KeyType.Virtual)

        assertEquals(OpeningResult.Success(prize.id), result)
    }

    @Test
    fun `command delivery failure writes pending prize`() = runTest {
        val keyService = mockk<KeyService>()
        val prizeEngine = mockk<PrizeEngine>()
        val pityTracker = mockk<PityTracker>()
        val storage = mockk<StorageService>(relaxed = true)
        val scheduler = immediateScheduler()
        val crate = testCrate(
            prize = Prize(
                id = "p-cmd",
                name = "Command Prize",
                tier = null,
                weight = 1,
                broadcast = false,
                broadcastMessage = "",
                items = emptyList(),
                commands = listOf("invalid %player%"),
                messages = emptyList(),
            ),
        )
        val prize = crate.prizes.single()
        val playerId = player.uniqueId

        every { Bukkit.dispatchCommand(any(), any()) } returns false
        coEvery { storage.getCooldown(any(), any()) } returns null
        coEvery { storage.countOpens(any(), any()) } returns 0
        coEvery { keyService.countKeys(player, crate, KeyType.Virtual) } returns crate.requiredKeys
        coEvery { keyService.consumeKey(player, crate, KeyType.Virtual) } returns true
        coEvery { pityTracker.increment(any(), any()) } returns 1
        coEvery { prizeEngine.selectPrize(crate, null, 1) } returns prize
        stubCreatedOperation(storage, player.uniqueId, crate.id, KeyType.Virtual)

        val pipeline = OpeningPipeline(keyService, prizeEngine, pityTracker, storage, scheduler, CrateEligibilityService())
        val result = pipeline.execute(player, crate, KeyType.Virtual)

        assertEquals(OpeningResult.Failure(FailureReason.DELIVERY_FAILED), result)
        coVerify(exactly = 1) { storage.addPendingPrize(playerId, crate.id, prize.id) }
        coVerify(exactly = 0) { storage.logOpen(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `disabled crate fails before key consumption`() = runTest {
        val keyService = mockk<KeyService>(relaxed = true)
        val prizeEngine = mockk<PrizeEngine>(relaxed = true)
        val pityTracker = mockk<PityTracker>(relaxed = true)
        val storage = mockk<StorageService>(relaxed = true)
        val scheduler = immediateScheduler()
        val crate = testCrate().copy(lifecycle = CrateLifecycleConfig(state = CrateState.DISABLED))
        stubCreatedOperation(storage, player.uniqueId, crate.id, KeyType.Virtual)

        val pipeline = OpeningPipeline(keyService, prizeEngine, pityTracker, storage, scheduler, CrateEligibilityService())
        val result = pipeline.execute(player, crate, KeyType.Virtual)

        assertEquals(OpeningResult.Failure(FailureReason.CRATE_DISABLED), result)
        coVerify(exactly = 0) { keyService.consumeKey(any(), any(), any()) }
    }

    @Test
    fun `missing additive open permission fails eligibility`() = runTest {
        val keyService = mockk<KeyService>(relaxed = true)
        val prizeEngine = mockk<PrizeEngine>(relaxed = true)
        val pityTracker = mockk<PityTracker>(relaxed = true)
        val storage = mockk<StorageService>(relaxed = true)
        val scheduler = immediateScheduler()
        val crate = testCrate().copy(
            requirements = CrateRequirements(
                permissions = CratePermissionRequirements(open = setOf("slate.crate.basic.special")),
            ),
        )
        every { player.hasPermission("slate.crate.basic.special") } returns false
        stubCreatedOperation(storage, player.uniqueId, crate.id, KeyType.Virtual)

        val pipeline = OpeningPipeline(keyService, prizeEngine, pityTracker, storage, scheduler, CrateEligibilityService())
        val result = pipeline.execute(player, crate, KeyType.Virtual)

        assertEquals(OpeningResult.Failure(FailureReason.MISSING_PERMISSION), result)
        coVerify(exactly = 0) { keyService.consumeKey(any(), any(), any()) }
    }

    @Test
    fun `item overflow keeps reward pending`() = runTest {
        val keyService = mockk<KeyService>()
        val prizeEngine = mockk<PrizeEngine>()
        val pityTracker = mockk<PityTracker>()
        val storage = mockk<StorageService>(relaxed = true)
        val scheduler = immediateScheduler()
        val crate = testCrate(
            prize = Prize(
                id = "p-overflow",
                name = "Overflow Prize",
                tier = null,
                weight = 1,
                broadcast = false,
                broadcastMessage = "",
                items = listOf(so.alaz.slate.crate.PrizeItem("DIAMOND", amount = 1)),
                commands = emptyList(),
                messages = emptyList(),
            ),
        )
        val prize = crate.prizes.single()
        val playerId = player.uniqueId

        every { ItemUtil.expandPrizeItem(any()) } returns listOf(mockk<ItemStack>(relaxed = true))
        every { inventory.addItem(*anyVararg()) } returns hashMapOf(0 to mockk<ItemStack>(relaxed = true))
        coEvery { storage.getCooldown(any(), any()) } returns null
        coEvery { storage.countOpens(any(), any()) } returns 0
        coEvery { keyService.countKeys(player, crate, KeyType.Virtual) } returns crate.requiredKeys
        coEvery { keyService.consumeKey(player, crate, KeyType.Virtual) } returns true
        coEvery { pityTracker.increment(any(), any()) } returns 1
        coEvery { prizeEngine.selectPrize(crate, null, 1) } returns prize
        stubCreatedOperation(storage, player.uniqueId, crate.id, KeyType.Virtual)

        val pipeline = OpeningPipeline(keyService, prizeEngine, pityTracker, storage, scheduler, CrateEligibilityService())
        val result = pipeline.execute(player, crate, KeyType.Virtual)

        assertEquals(OpeningResult.Failure(FailureReason.DELIVERY_FAILED), result)
        coVerify(exactly = 1) { storage.addPendingPrize(playerId, crate.id, prize.id) }
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

    private fun testCrate(
        prize: Prize = defaultPrize(),
        allowedWorlds: Set<String> = emptySet(),
    ) = CrateDefinition(
        id = "basic",
        name = "Basic",
        type = CrateType.Unknown,
        keyDefinition = KeyDefinition("Key", emptyList(), Material.TRIPWIRE_HOOK, emptyList(), null, KeyProvider.Vanilla, null, false),
        prizes = listOf(prize),
        allowedWorlds = allowedWorlds,
    )

    private fun defaultPrize() = Prize(
        id = "p1",
        name = "Prize",
        tier = null,
        weight = 1,
        broadcast = false,
        broadcastMessage = "",
        items = emptyList(),
        commands = emptyList(),
        messages = listOf("<green>win</green>"),
    )

    private fun stubCreatedOperation(storage: StorageService, playerId: UUID, crateId: String, keyType: KeyType) {
        val now = Instant.now()
        coEvery { storage.createOrGetOpenOperation(any()) } answers {
            val req = firstArg<OpenOperationCreate>()
            OpenOperation(
                operationId = req.operationId,
                requestId = req.requestId,
                uuid = playerId,
                crateId = crateId,
                keyType = keyType,
                state = OpenOperationState.CREATED,
                prizeId = null,
                errorMessage = null,
                createdAt = now,
                updatedAt = now,
            )
        }
        coEvery { storage.updateOpenOperation(any(), any(), any(), any()) } answers {
            val operationId = firstArg<String>()
            val state = secondArg<OpenOperationState>()
            OpenOperation(
                operationId = operationId,
                requestId = null,
                uuid = playerId,
                crateId = crateId,
                keyType = keyType,
                state = state,
                prizeId = thirdArg<String?>(),
                errorMessage = arg(3),
                createdAt = now,
                updatedAt = now,
            )
        }
        coEvery { storage.appendOpenOperationStep(any(), any(), any()) } returns Unit
    }
}
