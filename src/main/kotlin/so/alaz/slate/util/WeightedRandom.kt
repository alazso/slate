package so.alaz.slate.util

import kotlin.random.Random

object WeightedRandom {
    fun <T> select(items: List<T>, weightOf: (T) -> Int, random: Random = Random.Default): T {
        require(items.isNotEmpty()) { "Cannot select from an empty list" }

        val total = items.sumOf { weightOf(it).coerceAtLeast(0) }
        require(total > 0) { "Total weight must be > 0" }

        var roll = random.nextInt(total)
        for (item in items) {
            val weight = weightOf(item).coerceAtLeast(0)
            if (weight == 0) continue
            if (roll < weight) return item
            roll -= weight
        }

        return items.last()
    }

    fun <T> selectFrom(
        items: List<T>,
        weightOf: (T) -> Int,
        excludeBelow: Int,
        random: Random = Random.Default,
    ): T {
        val filtered = items.filter { weightOf(it) >= excludeBelow }
        require(filtered.isNotEmpty()) { "No items are eligible for selection" }
        return select(filtered, weightOf, random)
    }
}
