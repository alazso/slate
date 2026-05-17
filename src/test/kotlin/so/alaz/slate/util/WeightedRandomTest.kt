package so.alaz.slate.util

import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class WeightedRandomTest {
    @Test
    fun `select is deterministic with seeded random`() {
        val items = listOf("a", "b", "c")
        val resultA = (1..10).map {
            WeightedRandom.select(items, { if (it == "a") 1 else 3 }, Random(42))
        }
        val resultB = (1..10).map {
            WeightedRandom.select(items, { if (it == "a") 1 else 3 }, Random(42))
        }
        assertEquals(resultA, resultB)
    }

    @Test
    fun `selectFrom excludes below threshold`() {
        val items = listOf(1, 2, 3, 4)
        val selected = WeightedRandom.selectFrom(items, { it }, excludeBelow = 4, random = Random(1))
        assertEquals(4, selected)
    }

    @Test
    fun `select fails on empty list`() {
        assertThrows(IllegalArgumentException::class.java) {
            WeightedRandom.select(emptyList<Int>(), { it })
        }
    }
}
