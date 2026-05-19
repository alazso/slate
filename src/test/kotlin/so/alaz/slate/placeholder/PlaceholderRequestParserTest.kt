package so.alaz.slate.placeholder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlaceholderRequestParserTest {
    @Test
    fun `parses explicit colon syntax`() {
        val request = PlaceholderRequestParser.parse("keys:astral")
        assertNotNull(request)
        assertEquals("keys", request!!.key)
        assertEquals(listOf("astral"), request.args)
    }

    @Test
    fun `parses reward syntax with crate and reward id`() {
        val colon = PlaceholderRequestParser.parse("reward_chance:astral:mythic_blade")
        assertNotNull(colon)
        assertEquals("reward_chance", colon!!.key)
        assertEquals(listOf("astral", "mythic_blade"), colon.args)
    }

    @Test
    fun `rejects non strict underscore argument syntax`() {
        assertNull(PlaceholderRequestParser.parse("keys_astral"))
        assertNull(PlaceholderRequestParser.parse("reward_chance_astral_mythic_blade"))
    }

    @Test
    fun `rejects blank and malformed placeholders`() {
        assertNull(PlaceholderRequestParser.parse("   "))
        assertNull(PlaceholderRequestParser.parse("keys:"))
        assertNull(PlaceholderRequestParser.parse("keys::astral"))
    }
}
