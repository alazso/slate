package so.alaz.slate.command.sub

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerKeysCommandTest {
    @Test
    fun `keys command is self-only and has no player argument`() {
        val node = PlayerKeysCommand.node()
        assertTrue(node.children.any { it.name == "crate" })
        assertFalse(node.children.any { it.name == "player" })
    }
}
