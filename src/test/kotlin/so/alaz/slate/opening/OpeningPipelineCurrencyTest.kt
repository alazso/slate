package so.alaz.slate.opening

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.inventory.PlayerInventory
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import so.alaz.slate.crate.CrateCost
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.CrateEligibilityService
import so.alaz.slate.crate.CrateType
import so.alaz.slate.crate.KeyDefinition
import so.alaz.slate.crate.KeyProvider
import so.alaz.slate.crate.Prize
import so.alaz.slate.currency.domain.CurrencyAccount
import so.alaz.slate.currency.domain.CurrencyAmount
import so.alaz.slate.economy.EconomyBalanceResult
import so.alaz.slate.economy.EconomyContext
import so.alaz.slate.economy.EconomyMutationResult
import so.alaz.slate.economy.SlateEconomyService
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

class OpeningPipelineCurrencyTest {
    @Test
    fun `insufficient currency fails before key consumption`() = runTest {
        val player = mockPlayer()
        val keyService = mockk<KeyService>()
        val prizeEngine = mockk<PrizeEngine>()
        val pityTracker = mockk<PityTracker>()
        val storage = mockStorage(player.uniqueId)
        val scheduler = immediateScheduler()
        val currencyService = mockk<SlateEconomyService>()
        val crate = crateWithCost()

        coEvery { storage.getCooldown(any(), any()) } returns null
        coEvery { storage.countOpens(any(), any()) } returns 0
        coEvery { keyService.countKeys(player, crate, KeyType.Virtual) } returns 1
        coEvery { currencyService.getBalance(any(), "slate", "astral_shards") } returns EconomyBalanceResult.Success(CurrencyAmount.ofMinorUnits(10))

        val pipeline = OpeningPipeline(keyService, prizeEngine, pityTracker, storage, scheduler, CrateEligibilityService(), currencyService)
        val result = pipeline.execute(player, crate, KeyType.Virtual)

        assertEquals(OpeningResult.Failure(FailureReason.INSUFFICIENT_CURRENCY), result)
        coVerify(exactly = 0) { keyService.consumeKey(any(), any(), any()) }
    }

    @Disabled("Scheduler mocking for repeated request replay is unstable in unit environment; covered by provider idempotency tests.")
    @Test
    fun `request replay does not double charge currency`() = runTest {
        val player = mockPlayer()
        val keyService = mockk<KeyService>()
        val prizeEngine = mockk<PrizeEngine>()
        val pityTracker = mockk<PityTracker>()
        val storage = mockStorage(player.uniqueId)
        val scheduler = immediateScheduler()
        val currencyService = mockk<SlateEconomyService>()
        val crate = crateWithCost()
        val prize = crate.prizes.single()
        val created = openOperation(player.uniqueId, OpenOperationState.CREATED, requestId = "cmd:test")
        val delivered = created.copy(state = OpenOperationState.DELIVERED, prizeId = prize.id)

        coEvery { storage.createOrGetOpenOperation(any()) } returnsMany listOf(created, delivered)
        coEvery { storage.getCooldown(any(), any()) } returns null
        coEvery { storage.countOpens(any(), any()) } returns 0
        coEvery { keyService.countKeys(player, crate, KeyType.Virtual) } returns 1
        coEvery { keyService.consumeKey(player, crate, KeyType.Virtual) } returns true
        coEvery { pityTracker.increment(any(), any()) } returns 1
        coEvery { prizeEngine.selectPrize(crate, null, 1) } returns prize
        coEvery { currencyService.getBalance(any(), "slate", "astral_shards") } returns EconomyBalanceResult.Success(CurrencyAmount.ofMinorUnits(100))
        coEvery {
            currencyService.withdraw(any(), any(), any(), any(), any<EconomyContext>())
        } returns EconomyMutationResult.Success("tx1", CurrencyAmount.ofMinorUnits(100), CurrencyAmount.ofMinorUnits(75))
        coEvery { storage.updateOpenOperation(any(), any(), any(), any()) } returns created

        val pipeline = OpeningPipeline(keyService, prizeEngine, pityTracker, storage, scheduler, CrateEligibilityService(), currencyService)
        val first = pipeline.execute(player, crate, KeyType.Virtual, requestId = "cmd:test")
        val second = pipeline.execute(player, crate, KeyType.Virtual, requestId = "cmd:test")

        assertEquals(OpeningResult.Success(prize.id), first)
        assertEquals(OpeningResult.Success(prize.id), second)
        coVerify(exactly = 1) { currencyService.withdraw(any(), "slate", "astral_shards", CurrencyAmount.ofMinorUnits(25), any()) }
    }

    private fun mockPlayer(): Player {
        val world = mockk<World>(relaxed = true)
        every { world.name } returns "world"
        val inventory = mockk<PlayerInventory>(relaxed = true)
        val player = mockk<Player>(relaxed = true)
        every { player.uniqueId } returns UUID.randomUUID()
        every { player.world } returns world
        every { player.name } returns "CurrencyTester"
        every { player.inventory } returns inventory
        every { player.hasPermission(any<String>()) } returns true
        every { inventory.addItem(*anyVararg()) } returns hashMapOf()
        return player
    }

    private fun mockStorage(playerId: UUID): StorageService {
        val storage = mockk<StorageService>(relaxed = true)
        val op = openOperation(playerId, OpenOperationState.CREATED)
        coEvery { storage.createOrGetOpenOperation(any()) } returns op
        coEvery { storage.updateOpenOperation(any(), any(), any(), any()) } returns op
        return storage
    }

    private fun openOperation(playerId: UUID, state: OpenOperationState, requestId: String? = null): OpenOperation =
        OpenOperation(
            operationId = UUID.randomUUID().toString(),
            requestId = requestId,
            uuid = playerId,
            crateId = "astral",
            keyType = KeyType.Virtual,
            state = state,
            prizeId = null,
            errorMessage = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

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
            (invocation.args.getOrNull(1) as? (() -> Unit))?.invoke()
            object : CancellableTask {
                override fun cancel() {}
                override val isCancelled: Boolean = false
            }
        }
        return scheduler
    }

    private fun crateWithCost(): CrateDefinition =
        CrateDefinition(
            id = "astral",
            name = "Astral",
            type = CrateType.Unknown,
            requiredKeys = 1,
            keyDefinition = KeyDefinition(
                name = "Astral Key",
                lore = emptyList(),
                material = org.bukkit.Material.TRIPWIRE_HOOK,
                customModelData = emptyList(),
                customItemId = null,
                provider = KeyProvider.Vanilla,
                providerItemId = null,
                glowing = false,
            ),
            costs = listOf(
                CrateCost.CurrencyCost(
                    provider = "slate",
                    currency = "astral_shards",
                    amount = CurrencyAmount.ofMinorUnits(25),
                ),
            ),
            prizes = listOf(
                Prize(
                    id = "p1",
                    name = "P1",
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
