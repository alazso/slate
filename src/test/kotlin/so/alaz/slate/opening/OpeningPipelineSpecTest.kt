package so.alaz.slate.opening

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.plugin.PluginManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import so.alaz.slate.crate.CrateCost
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.CrateEligibilityService
import so.alaz.slate.crate.KeyDefinition
import so.alaz.slate.crate.KeyProvider
import so.alaz.slate.crate.Prize
import so.alaz.slate.currency.domain.CurrencyAmount
import so.alaz.slate.economy.EconomyBalanceResult
import so.alaz.slate.economy.SlateEconomyService
import so.alaz.slate.key.KeyService
import so.alaz.slate.key.KeyType
import so.alaz.slate.prize.PityTracker
import so.alaz.slate.prize.PrizeEngine
import so.alaz.slate.scheduler.CancellableTask
import so.alaz.slate.scheduler.PlatformScheduler
import so.alaz.slate.storage.StorageService
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

class OpeningPipelineSpecTest {
    private lateinit var player: Player

    @BeforeEach
    fun setUp() {
        mockkStatic(Bukkit::class)
        val world = mockk<World>()
        every { world.name } returns "world"
        every { world.key } returns NamespacedKey.minecraft("overworld")
        every { world.environment } returns World.Environment.NORMAL

        player = mockk(relaxed = true)
        every { player.uniqueId } returns UUID.randomUUID()
        every { player.world } returns world
        every { player.name } returns "Tester"
        every { player.hasPermission(any<String>()) } returns true

        val server = mockk<Server>(relaxed = true)
        every { Bukkit.getServer() } returns server
        every { Bukkit.getPluginManager() } returns mockk<PluginManager>(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Bukkit::class)
    }

    @Test
    fun `preflight ready reports available keys and request amount`() = runTest {
        val storage = mockk<StorageService>(relaxed = true)
        val keyService = mockk<KeyService>(relaxed = true)
        coEvery { keyService.countKeys(player, any(), KeyType.Virtual) } returns 10
        coEvery { storage.getCooldown(any(), any()) } returns null

        val pipeline = pipeline(storage, keyService)
        val crate = baseCrate(requiredKeys = 2)
        val result = pipeline.preflight(player, crate, amount = 3)

        assertTrue(result is OpenPreflightResult.Ready, "expected Ready, got $result")
        result as OpenPreflightResult.Ready
        assertEquals(6, result.requiredKeys)
        assertEquals(10, result.availableKeys)
        assertEquals(3, result.requestedAmount)
    }

    @Test
    fun `preflight on cooldown surfaces remaining millis`() = runTest {
        val storage = mockk<StorageService>(relaxed = true)
        val keyService = mockk<KeyService>(relaxed = true)
        val now = System.currentTimeMillis()
        coEvery { keyService.countKeys(player, any(), KeyType.Virtual) } returns 1
        coEvery { storage.getCooldown(any(), any()) } returns Instant.ofEpochMilli(now)

        val pipeline = pipeline(storage, keyService)
        val crate = baseCrate(cooldownMinutes = 5)
        val result = pipeline.preflight(player, crate)

        assertTrue(result is OpenPreflightResult.Blocked)
        result as OpenPreflightResult.Blocked
        assertEquals(FailureReason.ON_COOLDOWN, result.reason)
        assertTrue(result.cooldownRemainingMillis in (4 * 60_000L)..(5 * 60_000L + 1_000L))
    }

    @Test
    fun `max affordable caps to available keys when no currency`() = runTest {
        val storage = mockk<StorageService>(relaxed = true)
        val keyService = mockk<KeyService>(relaxed = true)
        coEvery { keyService.countKeys(player, any(), KeyType.Virtual) } returns 7
        coEvery { storage.getCooldown(any(), any()) } returns null

        val pipeline = pipeline(storage, keyService)
        val crate = baseCrate(requiredKeys = 3)
        val max = pipeline.maxAffordable(player, crate, cap = 100)
        assertEquals(2, max)
    }

    @Test
    fun `max affordable caps to currency balance when keys are abundant`() = runTest {
        val storage = mockk<StorageService>(relaxed = true)
        val keyService = mockk<KeyService>(relaxed = true)
        val economy = mockk<SlateEconomyService>()
        coEvery { keyService.countKeys(player, any(), KeyType.Virtual) } returns 100
        coEvery { storage.getCooldown(any(), any()) } returns null
        coEvery { economy.getBalance(any(), "slate", "coins") } returns EconomyBalanceResult.Success(
            CurrencyAmount.ofMinorUnits(450),
        )

        val pipeline = pipeline(storage, keyService, economy = economy)
        val crate = baseCrate(requiredKeys = 1).copy(
            costs = listOf(
                CrateCost.CurrencyCost(provider = "slate", currency = "coins", amount = CurrencyAmount.ofMinorUnits(100)),
            ),
        )
        val max = pipeline.maxAffordable(player, crate, cap = 100)
        assertEquals(4, max)
    }

    @Test
    fun `cooldown remaining is zero when crate has no cooldown`() = runTest {
        val storage = mockk<StorageService>(relaxed = true)
        coEvery { storage.getCooldown(any(), any()) } returns Instant.now()
        val pipeline = pipeline(storage, mockk(relaxed = true))
        val crate = baseCrate(cooldownMinutes = 0)
        assertEquals(0L, pipeline.cooldownRemainingMillis(player.uniqueId, crate))
    }

    private fun pipeline(
        storage: StorageService,
        keyService: KeyService,
        economy: SlateEconomyService? = null,
    ): OpeningPipeline {
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
        return OpeningPipeline(
            keyService = keyService,
            prizeEngine = mockk<PrizeEngine>(relaxed = true),
            pityTracker = mockk<PityTracker>(relaxed = true),
            storageService = storage,
            scheduler = scheduler,
            crateEligibilityService = CrateEligibilityService(),
            economyService = economy,
        )
    }

    private fun baseCrate(requiredKeys: Int = 1, cooldownMinutes: Long = 0): CrateDefinition = CrateDefinition(
        id = "spec",
        name = "Spec",
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
                id = "reward",
                name = "Reward",
                tier = null,
                weight = 1,
                chance = null,
                broadcast = false,
                broadcastMessage = "",
                items = emptyList(),
                commands = emptyList(),
                messages = emptyList(),
            ),
        ),
        requiredKeys = requiredKeys,
        cooldown = cooldownMinutes.minutes,
    )
}
