package so.alaz.slate.command.sub

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandFlagUtilTest {
    @Test
    fun `tokenize splits flags safely`() {
        val tokens = CommandFlagUtil.tokenize("--confirm --silent   --reason \"cleanup\"")
        assertEquals(listOf("--confirm", "--silent", "--reason", "\"cleanup\""), tokens)
        assertTrue(CommandFlagUtil.hasFlag(tokens, "--confirm"))
    }

    @Test
    fun `extractReason returns trimmed reason`() {
        assertEquals("cleanup run", CommandFlagUtil.extractReason("--confirm --reason \"cleanup run\""))
    }

    @Test
    fun `extractReason returns null when missing`() {
        assertNull(CommandFlagUtil.extractReason("--confirm"))
    }
}
