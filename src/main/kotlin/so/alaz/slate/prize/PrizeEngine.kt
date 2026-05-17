package so.alaz.slate.prize

import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.Prize
import so.alaz.slate.probability.ProbabilityTable

interface PrizeEngine {
    fun selectPrize(crate: CrateDefinition, pityTier: String? = null, pityCount: Int = 0): Prize?
    fun probabilityTable(crate: CrateDefinition): ProbabilityTable
    fun displayChance(prize: Prize, crate: CrateDefinition): Double
}
