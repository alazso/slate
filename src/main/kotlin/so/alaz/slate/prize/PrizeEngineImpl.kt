package so.alaz.slate.prize

import kotlin.random.Random
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.Prize
import so.alaz.slate.probability.ProbabilityEngine
import so.alaz.slate.probability.ProbabilityTable

class PrizeEngineImpl(
    private val random: Random = Random.Default,
) : PrizeEngine {
    private val probabilityEngine = ProbabilityEngine(random)

    override fun selectPrize(crate: CrateDefinition, pityTier: String?, pityCount: Int): Prize? =
        probabilityEngine.roll(
            crate = crate,
            pityTier = pityTier.takeIf { pityCount > 0 },
        ).prize

    override fun probabilityTable(crate: CrateDefinition): ProbabilityTable =
        probabilityEngine.buildTable(crate)

    override fun displayChance(prize: Prize, crate: CrateDefinition): Double =
        probabilityTable(crate).rowByPrizeId(prize.id)?.effectiveChance ?: 0.0
}
