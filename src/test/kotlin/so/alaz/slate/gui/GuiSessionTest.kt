package so.alaz.slate.gui

import io.mockk.mockk
import java.util.UUID
import org.bukkit.inventory.Inventory
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GuiSessionTest {
    @Test
    fun `debounce blocks immediate second click`() {
        val session = GuiSession(
            playerId = UUID.randomUUID(),
            gui = mockk(relaxed = true),
            inventory = mockk<Inventory>(relaxed = true),
        )

        assertTrue(session.passesDebounce(100))
        assertFalse(session.passesDebounce(100))
    }
}

