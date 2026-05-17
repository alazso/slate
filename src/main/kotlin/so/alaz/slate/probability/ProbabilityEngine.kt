package so.alaz.slate.probability

import kotlin.random.Random
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.Prize

class ProbabilityEngine(
    private val random: Random = Random.Default,
) {
    fun buildTable(crate: CrateDefinition): ProbabilityTable = when {
        crate.selection.mode == SelectionMode.WEIGHTED && crate.selection.structure == SelectionStructure.FLAT ->
            buildWeightedFlatTable(crate)
        crate.selection.mode == SelectionMode.WEIGHTED && crate.selection.structure == SelectionStructure.TIERS ->
            buildWeightedTierTable(crate)
        crate.selection.mode == SelectionMode.PERCENT && crate.selection.structure == SelectionStructure.FLAT ->
            buildPercentFlatTable(crate)
        else -> error("Unsupported selection mode/structure: ${crate.selection.mode}/${crate.selection.structure}")
    }

    fun roll(crate: CrateDefinition, pityTier: String? = null): SelectionRoll {
        val table = buildTable(crate)
        val rolled = when (table.structure) {
            SelectionStructure.FLAT -> rollFlat(table)
            SelectionStructure.TIERS -> rollTiered(table, pityTier)
        }
        return SelectionRoll(table = table, prize = rolled)
    }

    private fun buildWeightedFlatTable(crate: CrateDefinition): ProbabilityTable {
        val totalWeight = crate.prizes.sumOf { it.weight.coerceAtLeast(0) }
        val emptyWeight = if (crate.selection.allowEmpty) crate.selection.emptyWeight.coerceAtLeast(0) else 0
        val denominator = (totalWeight + emptyWeight).coerceAtLeast(0)
        val rows = crate.prizes.map { prize ->
            val local = if (denominator == 0) 0.0 else prize.weight.coerceAtLeast(0).toDouble() / denominator.toDouble()
            ProbabilityRow(
                prize = prize,
                tierId = null,
                configuredValue = prize.weight.toDouble(),
                tierChance = null,
                localChance = local,
                effectiveChance = local,
            )
        }
        val emptyChance = if (denominator == 0) 0.0 else emptyWeight.toDouble() / denominator.toDouble()
        return ProbabilityTable(
            crateId = crate.id,
            mode = SelectionMode.WEIGHTED,
            structure = SelectionStructure.FLAT,
            rows = rows,
            totalConfiguredValue = totalWeight.toDouble(),
            emptyChance = emptyChance,
        )
    }

    private fun buildPercentFlatTable(crate: CrateDefinition): ProbabilityTable {
        val rows = crate.prizes.map { prize ->
            val chance = prize.chance?.coerceAtLeast(0.0) ?: 0.0
            val normalized = chance / 100.0
            ProbabilityRow(
                prize = prize,
                tierId = null,
                configuredValue = chance,
                tierChance = null,
                localChance = normalized,
                effectiveChance = normalized,
            )
        }
        val total = rows.sumOf { it.configuredValue }
        val emptyChance = if (crate.selection.allowEmpty) (100.0 - total).coerceIn(0.0, 100.0) / 100.0 else 0.0
        return ProbabilityTable(
            crateId = crate.id,
            mode = SelectionMode.PERCENT,
            structure = SelectionStructure.FLAT,
            rows = rows,
            totalConfiguredValue = total,
            emptyChance = emptyChance,
        )
    }

    private fun buildWeightedTierTable(crate: CrateDefinition): ProbabilityTable {
        val totalTierWeight = crate.tiers.values.sumOf { it.weight.coerceAtLeast(0) }
        val prizesByTier = crate.prizes.groupBy { it.tier }
        val rows = crate.prizes.map { prize ->
            val tierId = prize.tier
            val tier = crate.tiers[tierId]
            val tierWeight = tier?.weight?.coerceAtLeast(0) ?: 0
            val tierChance = if (totalTierWeight == 0) 0.0 else tierWeight.toDouble() / totalTierWeight.toDouble()
            val tierPrizes = prizesByTier[tierId].orEmpty()
            val localTotalWeight = tierPrizes.sumOf { it.weight.coerceAtLeast(0) }
            val localChance = if (localTotalWeight == 0) 0.0 else prize.weight.coerceAtLeast(0).toDouble() / localTotalWeight.toDouble()
            ProbabilityRow(
                prize = prize,
                tierId = tierId,
                configuredValue = prize.weight.toDouble(),
                tierChance = tierChance,
                localChance = localChance,
                effectiveChance = tierChance * localChance,
            )
        }
        return ProbabilityTable(
            crateId = crate.id,
            mode = SelectionMode.WEIGHTED,
            structure = SelectionStructure.TIERS,
            rows = rows,
            totalConfiguredValue = totalTierWeight.toDouble(),
            emptyChance = 0.0,
        )
    }

    private fun rollFlat(table: ProbabilityTable): Prize? {
        if (table.rows.isEmpty()) return null
        val roll = random.nextDouble()
        var cursor = 0.0
        for (row in table.rows) {
            cursor += row.effectiveChance
            if (roll < cursor) return row.prize
        }
        return if (roll < cursor + table.emptyChance) null else table.rows.last().prize
    }

    private fun rollTiered(table: ProbabilityTable, pityTier: String?): Prize? {
        if (table.rows.isEmpty()) return null
        if (!pityTier.isNullOrBlank()) {
            val pityRows = table.rows.filter { it.tierId == pityTier }
            if (pityRows.isNotEmpty()) return rollRowsByChance(pityRows)
        }
        val byTier = table.rows.groupBy { it.tierId }
        val tierChances = byTier.mapNotNull { (tierId, rows) ->
            val tierChance = rows.firstOrNull()?.tierChance ?: return@mapNotNull null
            tierId to tierChance
        }.sortedBy { it.first.orEmpty() }

        val pickedTierId = rollPairsByChance(tierChances) ?: return null
        return rollRowsByChance(byTier[pickedTierId].orEmpty())
    }

    private fun rollRowsByChance(rows: List<ProbabilityRow>): Prize? {
        if (rows.isEmpty()) return null
        val roll = random.nextDouble()
        var cursor = 0.0
        for (row in rows) {
            cursor += row.localChance
            if (roll < cursor) return row.prize
        }
        return rows.last().prize
    }

    private fun <T> rollPairsByChance(values: List<Pair<T, Double>>): T? {
        if (values.isEmpty()) return null
        val roll = random.nextDouble()
        var cursor = 0.0
        for ((value, chance) in values) {
            cursor += chance
            if (roll < cursor) return value
        }
        return values.last().first
    }
}
