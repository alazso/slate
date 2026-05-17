package so.alaz.slate.listener

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.junit.jupiter.api.Test
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.CrateManager
import so.alaz.slate.crate.CrateType
import so.alaz.slate.crate.KeyDefinition
import so.alaz.slate.crate.KeyProvider
import so.alaz.slate.crate.anchor.AnchorService
import so.alaz.slate.key.KeyService
import so.alaz.slate.opening.OpenBatchResult
import so.alaz.slate.opening.OpenService
import org.bukkit.inventory.ItemStack
import so.alaz.slate.crate.Prize
import org.bukkit.event.Event.Result.DENY
import so.alaz.slate.scheduler.CancellableTask
import so.alaz.slate.scheduler.PlatformScheduler

class CrateInteractListenerTest {
    @Test
    fun `non-admin cannot break anchored block`() = runTest {
        val keyService = mockk<KeyService>(relaxed = true)
        val openService = mockk<OpenService>(relaxed = true)
        val anchorService = mockk<AnchorService>(relaxed = true)
        val listener = CrateInteractListener(this, keyService, openService, anchorService, immediateScheduler())

        val world = mockk<World>()
        every { world.key } returns NamespacedKey.minecraft("world")
        val location = Location(world, 20.0, 64.0, 20.0)
        val block = mockk<Block>()
        every { block.location } returns location

        val player = mockk<Player>(relaxed = true)
        every { player.hasPermission("slate.admin.anchor.set") } returns false
        every { player.hasPermission("slate.admin.anchor.remove") } returns false
        val event = mockk<BlockBreakEvent>(relaxed = true)
        every { event.block } returns block
        every { event.player } returns player
        every { anchorService.crateIdAtCached(location) } returns "demo"

        listener.onBlockBreak(event)

        io.mockk.verify(exactly = 1) { event.isCancelled = true }
    }

    @Test
    fun `admin can break anchored block`() = runTest {
        val keyService = mockk<KeyService>(relaxed = true)
        val openService = mockk<OpenService>(relaxed = true)
        val anchorService = mockk<AnchorService>(relaxed = true)
        val listener = CrateInteractListener(this, keyService, openService, anchorService, immediateScheduler())

        val world = mockk<World>()
        every { world.key } returns NamespacedKey.minecraft("world")
        val location = Location(world, 20.0, 64.0, 20.0)
        val block = mockk<Block>()
        every { block.location } returns location

        val player = mockk<Player>(relaxed = true)
        every { player.hasPermission("slate.admin.anchor.set") } returns true
        every { player.hasPermission("slate.admin.anchor.remove") } returns false
        val event = mockk<BlockBreakEvent>(relaxed = true)
        every { event.block } returns block
        every { event.player } returns player
        every { anchorService.crateIdAtCached(location) } returns "demo"

        listener.onBlockBreak(event)

        io.mockk.verify(exactly = 0) { event.isCancelled = true }
    }

    @Test
    fun `anchor interaction uses open service even without key item`() = runTest {
        val keyService = mockk<KeyService>(relaxed = true)
        val openService = mockk<OpenService>(relaxed = true)
        val anchorService = mockk<AnchorService>(relaxed = true)
        val listener = CrateInteractListener(this, keyService, openService, anchorService, immediateScheduler())

        val world = mockk<World>()
        every { world.key } returns NamespacedKey.minecraft("world")
        val location = Location(world, 10.0, 64.0, 10.0)
        val block = mockk<Block>()
        every { block.location } returns location

        val player = mockk<Player>(relaxed = true)
        every { player.uniqueId } returns UUID.randomUUID()
        val event = mockk<PlayerInteractEvent>(relaxed = true)
        every { event.hand } returns EquipmentSlot.HAND
        every { event.action } returns Action.RIGHT_CLICK_BLOCK
        every { event.clickedBlock } returns block
        every { event.item } returns null
        every { event.player } returns player

        val crate = CrateDefinition(
            id = "demo",
            name = "Demo",
            type = CrateType.Unknown,
            keyDefinition = KeyDefinition("Key", emptyList(), org.bukkit.Material.TRIPWIRE_HOOK, emptyList(), null, KeyProvider.Vanilla, null, false),
            prizes = listOf(
                Prize(
                    id = "p1",
                    name = "Prize",
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
        )
        val crateManager = mockk<CrateManager>(relaxed = true)
        mockkObject(SlateAPI)
        every { SlateAPI.crateManager() } returns crateManager
        every { crateManager.byId("demo") } returns crate
        every { anchorService.crateIdAtCached(location) } returns "demo"
        coEvery { openService.openFromInteraction(player, crate, null) } returns so.alaz.slate.opening.OpeningResult.Success("p1")

        try {
            listener.onInteract(event)
            kotlinx.coroutines.yield()

            io.mockk.verify(exactly = 1) { event.isCancelled = true }
            io.mockk.verify(exactly = 1) { event.setUseInteractedBlock(DENY) }
            io.mockk.verify(exactly = 1) { event.setUseItemInHand(DENY) }
            coVerify(exactly = 1) { openService.openFromInteraction(player, crate, null) }
        } finally {
            unmockkObject(SlateAPI)
        }
    }

    @Test
    fun `non-anchor interaction still resolves key item`() = runTest {
        val keyService = mockk<KeyService>(relaxed = true)
        val openService = mockk<OpenService>(relaxed = true)
        val anchorService = mockk<AnchorService>(relaxed = true)
        val listener = CrateInteractListener(this, keyService, openService, anchorService, immediateScheduler())

        val world = mockk<World>()
        every { world.key } returns NamespacedKey.minecraft("world")
        val location = Location(world, 15.0, 64.0, 15.0)
        val block = mockk<Block>()
        every { block.location } returns location

        val player = mockk<Player>(relaxed = true)
        every { player.uniqueId } returns UUID.randomUUID()
        val item = mockk<ItemStack>(relaxed = true)
        val event = mockk<PlayerInteractEvent>(relaxed = true)
        every { event.hand } returns EquipmentSlot.HAND
        every { event.action } returns Action.RIGHT_CLICK_BLOCK
        every { event.clickedBlock } returns block
        every { event.item } returns item
        every { event.player } returns player

        val crate = CrateDefinition(
            id = "demo",
            name = "Demo",
            type = CrateType.Unknown,
            keyDefinition = KeyDefinition("Key", emptyList(), org.bukkit.Material.TRIPWIRE_HOOK, emptyList(), null, KeyProvider.Vanilla, null, false),
            prizes = listOf(
                Prize(
                    id = "p1",
                    name = "Prize",
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
        )

        every { anchorService.crateIdAtCached(location) } returns null
        every { keyService.getCrateForKey(item) } returns crate

        listener.onInteract(event)
        kotlinx.coroutines.yield()

        coVerify(exactly = 1) { openService.openFromInteraction(player, crate, item) }
    }
}

private fun immediateScheduler(): PlatformScheduler {
    val scheduler = mockk<PlatformScheduler>()
    every { scheduler.runForPlayerEntity(any(), any()) } answers {
        secondArg<() -> Unit>().invoke()
        object : CancellableTask {
            override fun cancel() {}
            override val isCancelled: Boolean = false
        }
    }
    return scheduler
}
