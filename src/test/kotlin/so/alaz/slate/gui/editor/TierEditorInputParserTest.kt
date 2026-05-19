package so.alaz.slate.gui.editor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TierEditorInputParserTest {
    @Test
    fun `parseTierId accepts word-like ids`() {
        assertEquals("my_tier-1", TierEditorInputParser.parseTierId(" my_tier-1 "))
    }

    @Test
    fun `parseTierId rejects invalid ids`() {
        assertNull(TierEditorInputParser.parseTierId("tier one"))
        assertNull(TierEditorInputParser.parseTierId(""))
    }

    @Test
    fun `parsePositiveWeight parses positive integers`() {
        assertEquals(7, TierEditorInputParser.parsePositiveWeight(" 7 "))
    }

    @Test
    fun `parsePositiveWeight rejects zero and non-numeric`() {
        assertNull(TierEditorInputParser.parsePositiveWeight("0"))
        assertNull(TierEditorInputParser.parsePositiveWeight("abc"))
    }
}
