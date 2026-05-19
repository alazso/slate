package so.alaz.slate.gui.editor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RewardEditorInputParserTest {
    @Test
    fun `parse reward id validates format`() {
        assertEquals("reward_1", RewardEditorInputParser.parseRewardId(" reward_1 "))
        assertNull(RewardEditorInputParser.parseRewardId("reward one"))
    }

    @Test
    fun `parse chance parses valid range`() {
        assertEquals(12.5, RewardEditorInputParser.parseChance("12.5"))
        assertNull(RewardEditorInputParser.parseChance("0"))
        assertNull(RewardEditorInputParser.parseChance("101"))
    }

    @Test
    fun `parse prize item supports material spec`() {
        val item = RewardEditorInputParser.parsePrizeItem("material=DIAMOND;amount=2")
        assertNotNull(item)
        assertEquals("DIAMOND", item?.material)
        assertEquals(2, item?.amount)
    }
}
