package so.alaz.slate.probability

import java.util.concurrent.ConcurrentHashMap
import so.alaz.slate.api.SlateAPI

object ProbabilityVariableService {
    private const val TTL_MS = 10_000L
    private val cache = ConcurrentHashMap<String, CachedTable>()

    fun clearCache() {
        cache.clear()
    }

    fun crateVariables(crateId: String): Map<String, String> {
        val table = getTable(crateId) ?: return fallbackCrateVariables()
        val rows = table.rows.sortedByDescending { it.effectiveChance }
        val top = rows.firstOrNull()
        val rarest = rows.lastOrNull()
        return mapOf(
            "mode" to table.mode.name.lowercase(),
            "structure" to table.structure.name.lowercase(),
            "empty_chance" to ChanceFormatter.percent(table.emptyChance),
            "top_reward" to (top?.prize?.id ?: "N/A"),
            "top_chance" to ChanceFormatter.percent(top?.effectiveChance ?: 0.0),
            "rarest_reward" to (rarest?.prize?.id ?: "N/A"),
            "rarest_chance" to ChanceFormatter.percent(rarest?.effectiveChance ?: 0.0),
        )
    }

    fun rewardVariables(crateId: String, rewardId: String): Map<String, String> {
        val table = getTable(crateId) ?: return fallbackRewardVariables()
        val row = table.rowByPrizeId(rewardId) ?: return fallbackRewardVariables()
        return mapOf(
            "reward" to row.prize.id,
            "tier" to (row.tierId ?: "N/A"),
            "chance" to ChanceFormatter.percent(row.effectiveChance),
            "chance_percent" to ChanceFormatter.percent(row.effectiveChance),
            "chance_ratio" to ChanceFormatter.ratio(row.effectiveChance),
            "tier_chance" to ChanceFormatter.percent(row.tierChance ?: 0.0),
            "local_chance" to ChanceFormatter.percent(row.localChance),
            "effective_chance" to ChanceFormatter.percent(row.effectiveChance),
        )
    }

    fun tierVariables(crateId: String, tierId: String): Map<String, String> {
        val table = getTable(crateId) ?: return fallbackTierVariables()
        val rows = table.rows.filter { it.tierId == tierId }
        if (rows.isEmpty()) return fallbackTierVariables()
        val tierChance = rows.firstOrNull()?.tierChance ?: 0.0
        val top = rows.maxByOrNull { it.effectiveChance }
        return mapOf(
            "tier" to tierId,
            "tier_chance" to ChanceFormatter.percent(tierChance),
            "tier_reward_count" to rows.size.toString(),
            "tier_top_reward" to (top?.prize?.id ?: "N/A"),
            "tier_top_effective_chance" to ChanceFormatter.percent(top?.effectiveChance ?: 0.0),
        )
    }

    fun render(template: String, variables: Map<String, String>): String {
        var result = template
        variables.forEach { (k, v) ->
            result = result.replace("<$k>", v)
        }
        return result
    }

    private fun getTable(crateId: String): ProbabilityTable? {
        val now = System.currentTimeMillis()
        val cached = cache[crateId]
        if (cached != null && now - cached.atMs <= TTL_MS) {
            return cached.table
        }
        val crate = SlateAPI.crateManager()?.byId(crateId) ?: return null
        val table = ProbabilityEngine().buildTable(crate)
        cache[crateId] = CachedTable(atMs = now, table = table)
        return table
    }

    private fun fallbackCrateVariables(): Map<String, String> = mapOf(
        "mode" to "N/A",
        "structure" to "N/A",
        "empty_chance" to "N/A",
        "top_reward" to "N/A",
        "top_chance" to "N/A",
        "rarest_reward" to "N/A",
        "rarest_chance" to "N/A",
    )

    private fun fallbackRewardVariables(): Map<String, String> = mapOf(
        "reward" to "N/A",
        "tier" to "N/A",
        "chance" to "N/A",
        "chance_percent" to "N/A",
        "chance_ratio" to "N/A",
        "tier_chance" to "N/A",
        "local_chance" to "N/A",
        "effective_chance" to "N/A",
    )

    private fun fallbackTierVariables(): Map<String, String> = mapOf(
        "tier" to "N/A",
        "tier_chance" to "N/A",
        "tier_reward_count" to "N/A",
        "tier_top_reward" to "N/A",
        "tier_top_effective_chance" to "N/A",
    )

    private data class CachedTable(
        val atMs: Long,
        val table: ProbabilityTable,
    )
}
