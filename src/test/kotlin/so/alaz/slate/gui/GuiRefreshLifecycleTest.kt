package so.alaz.slate.gui

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * Pins the rebuild-before-refresh contract used by every stateful GUI.
 *
 * `SlateGui.refresh()` only re-renders the existing slot map. Click handlers
 * that mutate state (cycled fields, pagination, filters, draft edits) must
 * call `rebuild(player)` so that `onBeforeOpen` re-runs and stale static slots
 * are replaced before the framework's automatic refresh fires.
 */
class GuiRefreshLifecycleTest {
    @Test
    fun `rebuild reruns onBeforeOpen so static slots reflect new state`() = runTest {
        val player = mockk<Player>(relaxed = true)
        val gui = CounterGui()
        gui.exposedOnBeforeOpen(player)
        assertEquals(1, gui.beforeOpenCalls)

        val initial = gui.renderSlot(player, 0)
        assertSame(CounterGui.STONE_STACK, initial)

        gui.counter = 1
        gui.exposedRebuild(player)

        assertEquals(2, gui.beforeOpenCalls)
        assertSame(CounterGui.DIRT_STACK, gui.renderSlot(player, 0))
    }

    @Test
    fun `rebuild clears slots that the previous layout populated but the new one omits`() = runTest {
        val player = mockk<Player>(relaxed = true)
        val gui = CounterGui()
        gui.exposedOnBeforeOpen(player)

        assertSame(CounterGui.GRASS_STACK, gui.renderSlot(player, 1))

        gui.counter = 1
        gui.exposedRebuild(player)

        assertNull(
            gui.renderSlot(player, 1),
            "rebuild must drop slots that the previous layout set but the new layout omits",
        )
    }

    private class CounterGui : SlateGui(title = "Test", rows = 1) {
        var counter: Int = 0
        var beforeOpenCalls: Int = 0

        override suspend fun onBeforeOpen(player: Player) {
            beforeOpenCalls++
            if (counter == 0) {
                slot(0, StaticSlot(STONE_STACK))
                slot(1, StaticSlot(GRASS_STACK))
            } else {
                slot(0, StaticSlot(DIRT_STACK))
            }
        }

        suspend fun exposedOnBeforeOpen(player: Player) = onBeforeOpen(player)
        suspend fun exposedRebuild(player: Player) = rebuild(player)

        companion object {
            val STONE_STACK: ItemStack = stackOf(Material.STONE)
            val DIRT_STACK: ItemStack = stackOf(Material.DIRT)
            val GRASS_STACK: ItemStack = stackOf(Material.GRASS_BLOCK)

            private fun stackOf(material: Material): ItemStack {
                val stack = mockk<ItemStack>(relaxed = true)
                every { stack.type } returns material
                every { stack.clone() } returns stack
                return stack
            }
        }
    }
}
