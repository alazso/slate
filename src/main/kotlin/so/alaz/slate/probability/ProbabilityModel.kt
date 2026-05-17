package so.alaz.slate.probability

import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.Prize

enum class SelectionMode {
    WEIGHTED,
    PERCENT,
    ;

    companion object {
        fun parse(raw: String?): SelectionMode? = when (raw?.trim()?.lowercase()) {
            null, "", "weighted" -> WEIGHTED
            "percent" -> PERCENT
            else -> null
        }
    }
}

enum class SelectionStructure {
    FLAT,
    TIERS,
    ;

    companion object {
        fun parse(raw: String?): SelectionStructure? = when (raw?.trim()?.lowercase()) {
            null, "", "flat" -> FLAT
            "tiers" -> TIERS
            else -> null
        }
    }
}

data class SelectionDefinition(
    val mode: SelectionMode = SelectionMode.WEIGHTED,
    val structure: SelectionStructure = SelectionStructure.FLAT,
    val allowEmpty: Boolean = false,
    val emptyWeight: Int = 0,
)

data class ProbabilityRow(
    val prize: Prize,
    val tierId: String?,
    val configuredValue: Double,
    val tierChance: Double?,
    val localChance: Double,
    val effectiveChance: Double,
)

data class ProbabilityTable(
    val crateId: String,
    val mode: SelectionMode,
    val structure: SelectionStructure,
    val rows: List<ProbabilityRow>,
    val totalConfiguredValue: Double,
    val emptyChance: Double,
) {
    fun rowByPrizeId(prizeId: String): ProbabilityRow? = rows.firstOrNull { it.prize.id == prizeId }
}

data class SelectionRoll(
    val table: ProbabilityTable,
    val prize: Prize?,
)

fun CrateDefinition.isTieredWeighted(): Boolean =
    selection.mode == SelectionMode.WEIGHTED && selection.structure == SelectionStructure.TIERS
