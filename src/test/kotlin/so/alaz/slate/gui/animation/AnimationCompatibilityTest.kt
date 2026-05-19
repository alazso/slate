package so.alaz.slate.gui.animation

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import so.alaz.slate.crate.Prize
import so.alaz.slate.gui.GuiSession
import so.alaz.slate.scheduler.CancellableTask
import so.alaz.slate.scheduler.PlatformScheduler
import java.util.UUID

class AnimationCompatibilityTest {
    @AfterEach
    fun tearDown() {
        clearAllMocks()
        unmockkAll()
    }

    @Test
    fun `csgo locks center slot to winning prize in reveal tail`() {
        mockkObject(AnimationItemFactory)
        val (prize, decoys) = prizeSet()
        val prizeStack = mockk<ItemStack>(relaxed = true)
        val decoyStack = mockk<ItemStack>(relaxed = true)
        every { AnimationItemFactory.fromPrize(match { it.id == prize.id }) } returns prizeStack
        every { AnimationItemFactory.fromPrize(match { it.id != prize.id }) } returns decoyStack

        val animation = CsgoAnimation(totalTicks = 40, tickInterval = 1, spinSpeedMultiplier = 1.0, revealTailTicks = 5)
        val frame = animation.frame(tick = 39, prize = prize, decoys = decoys)

        assertEquals(prizeStack, frame.slotItems[13])
        assertEquals(7, frame.slotItems.size)
    }

    @Test
    fun `agency pick does not pin center immediately on short durations`() {
        mockkObject(AnimationItemFactory)
        val (prize, decoys) = prizeSet()
        val prizeStack = mockk<ItemStack>(relaxed = true)
        val decoyStack = mockk<ItemStack>(relaxed = true)
        every { AnimationItemFactory.fromPrize(match { it.id == prize.id }) } returns prizeStack
        every { AnimationItemFactory.fromPrize(match { it.id != prize.id }) } returns decoyStack

        val animation = AgencyPickAnimation(totalTicks = 4, tickInterval = 1, spinSpeedMultiplier = 1.0, revealTailTicks = 5)
        val frameAtStart = animation.frame(tick = 0, prize = prize, decoys = decoys)
        val frameAtEnd = animation.frame(tick = 4, prize = prize, decoys = decoys)

        assertNotEquals(prizeStack, frameAtStart.slotItems[13])
        assertEquals(prizeStack, frameAtEnd.slotItems[13])
    }

    @Test
    fun `slot machine locks all reels at completion`() {
        mockkObject(AnimationItemFactory)
        val (prize, decoys) = prizeSet()
        val prizeStack = mockk<ItemStack>(relaxed = true)
        val decoyStack = mockk<ItemStack>(relaxed = true)
        every { AnimationItemFactory.fromPrize(match { it.id == prize.id }) } returns prizeStack
        every { AnimationItemFactory.fromPrize(match { it.id != prize.id }) } returns decoyStack

        val animation = SlotMachineAnimation(totalTicks = 30, tickInterval = 1, spinSpeedMultiplier = 1.0, revealTailTicks = 9)
        val frame = animation.frame(tick = 30, prize = prize, decoys = decoys)

        assertEquals(prizeStack, frame.slotItems[11])
        assertEquals(prizeStack, frame.slotItems[13])
        assertEquals(prizeStack, frame.slotItems[15])
    }

    @Test
    fun `spin speed multiplier changes sampled decoy progression`() {
        mockkObject(AnimationItemFactory)
        val prize = prize("win")
        val d1 = prize("d1")
        val d2 = prize("d2")
        val d3 = prize("d3")
        val s1 = mockk<ItemStack>(relaxed = true)
        val s2 = mockk<ItemStack>(relaxed = true)
        val s3 = mockk<ItemStack>(relaxed = true)
        val sw = mockk<ItemStack>(relaxed = true)
        every { AnimationItemFactory.fromPrize(match { it.id == "d1" }) } returns s1
        every { AnimationItemFactory.fromPrize(match { it.id == "d2" }) } returns s2
        every { AnimationItemFactory.fromPrize(match { it.id == "d3" }) } returns s3
        every { AnimationItemFactory.fromPrize(match { it.id == "win" }) } returns sw

        val normal = AgencyPickAnimation(totalTicks = 40, tickInterval = 1, spinSpeedMultiplier = 1.0, revealTailTicks = 5)
        val faster = AgencyPickAnimation(totalTicks = 40, tickInterval = 1, spinSpeedMultiplier = 2.0, revealTailTicks = 5)
        val normalSlot = normal.frame(tick = 1, prize = prize, decoys = listOf(d1, d2, d3)).slotItems[11]
        val fasterSlot = faster.frame(tick = 1, prize = prize, decoys = listOf(d1, d2, d3)).slotItems[11]

        assertNotEquals(normalSlot, fasterSlot)
    }

    @Test
    fun `animation gui renders deterministic final frame when interval skips total`() = runTest {
        val template = RecordingTemplate(totalTicks = 10, tickInterval = 4)
        val scheduler = immediateScheduler()
        val player = mockk<Player>(relaxed = true)
        every { player.isOnline } returns true
        val inventory = mockk<Inventory>(relaxed = true)
        val session = GuiSession(
            sessionId = UUID.randomUUID(),
            playerId = UUID.randomUUID(),
            gui = mockk(relaxed = true),
            inventory = inventory,
        )
        val gui = AnimationGui(template = template, prize = prize("win"), decoys = emptyList())
        gui.onAfterOpen(player, session)

        gui.play(player, session, scheduler)

        assertTrue(template.ticks.contains(10))
        assertEquals(10, template.ticks.last())
    }

    @Test
    fun `animation gui force completion renders final frame`() = runTest {
        val template = RecordingTemplate(totalTicks = 10, tickInterval = 2)
        val scheduler = immediateScheduler()
        val player = mockk<Player>(relaxed = true)
        every { player.isOnline } returns true
        val inventory = mockk<Inventory>(relaxed = true)
        val sessionId = UUID.randomUUID()
        val session = GuiSession(
            sessionId = sessionId,
            playerId = UUID.randomUUID(),
            gui = mockk(relaxed = true),
            inventory = inventory,
        )
        val gui = AnimationGui(template = template, prize = prize("win"), decoys = emptyList())
        gui.onAfterOpen(player, session)
        gui.requestImmediateCompletion(sessionId)

        gui.play(player, session, scheduler)

        assertEquals(listOf(10), template.ticks)
    }

    private fun prize(id: String): Prize = Prize(
        id = id,
        name = id,
        tier = null,
        weight = 1,
        broadcast = false,
        broadcastMessage = "",
        items = emptyList(),
        commands = emptyList(),
        messages = emptyList(),
    )

    private fun prizeSet(): Pair<Prize, List<Prize>> {
        val prize = prize("win")
        return prize to listOf(prize("d1"), prize("d2"), prize("d3"))
    }

    private class RecordingTemplate(
        override val totalTicks: Int,
        override val tickInterval: Int,
    ) : AnimationTemplate {
        override val id: String = "recording"
        val ticks = mutableListOf<Int>()
        override fun shouldRenderOnTick(tick: Int): Boolean = tick % tickInterval == 0
        override fun frame(tick: Int, prize: Prize, decoys: List<Prize>): AnimationFrame {
            ticks += tick
            return AnimationFrame(emptyMap())
        }
    }

    private fun immediateScheduler(): PlatformScheduler {
        val scheduler = mockk<PlatformScheduler>()
        every {
            scheduler.runRepeatingForEntity(
                entity = any(),
                initialDelayTicks = any(),
                periodTicks = any(),
                retiredCallback = any(),
                task = any(),
            )
        } answers {
            val taskBlock = arg<() -> Unit>(4)
            var cancelled = false
            var runs = 0
            val handle = object : CancellableTask {
                override fun cancel() {
                    cancelled = true
                }

                override val isCancelled: Boolean
                    get() = cancelled
            }
            while (runs < 256 && !cancelled) {
                taskBlock.invoke()
                runs++
            }
            handle
        }
        return scheduler
    }
}

