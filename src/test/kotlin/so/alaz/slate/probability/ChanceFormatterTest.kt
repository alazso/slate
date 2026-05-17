package so.alaz.slate.probability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChanceFormatterTest {
    @Test
    fun `formats percent with decimal support`() {
        assertEquals("0.05%", ChanceFormatter.percent(0.0005))
        assertEquals("12.5%", ChanceFormatter.percent(0.125))
    }

    @Test
    fun `formats ratio`() {
        assertEquals("1 in 2000", ChanceFormatter.ratio(0.0005))
        assertEquals("always", ChanceFormatter.ratio(1.0))
        assertEquals("never", ChanceFormatter.ratio(0.0))
    }

    @Test
    fun `hidden mode masks chance`() {
        assertEquals("hidden", ChanceFormatter.display(0.25, hidden = true))
    }
}
