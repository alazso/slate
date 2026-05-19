package so.alaz.slate.gui.animation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DecelerationCurveTest {
    @Test
    fun `slowSpin produces widening gaps toward deceleration start`() {
        val ticks = slowSpin(endTick = 80, cuts = 9)
        assertEquals(
            setOf(80, 71, 63, 56, 50, 45, 41, 38, 36),
            ticks,
        )
    }

    @Test
    fun `slowSpin returns empty set for non-positive cuts`() {
        assertTrue(slowSpin(80, 0).isEmpty())
        assertTrue(slowSpin(80, -1).isEmpty())
    }
}
