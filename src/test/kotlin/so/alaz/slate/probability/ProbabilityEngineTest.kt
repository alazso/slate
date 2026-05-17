package so.alaz.slate.probability

import kotlin.random.Random
import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.CrateType
import so.alaz.slate.crate.KeyDefinition
import so.alaz.slate.crate.KeyProvider
import so.alaz.slate.crate.Prize
import so.alaz.slate.crate.PrizeTier

class ProbabilityEngineTest {
    @Test
    fun `weighted flat computes effective chance`() {
        val crate = crate(
            selection = SelectionDefinition(mode = SelectionMode.WEIGHTED, structure = SelectionStructure.FLAT),
            prizes = listOf(prize("a", weight = 1), prize("b", weight = 3)),
        )

        val table = ProbabilityEngine(Random(1)).buildTable(crate)
        assertEquals(0.25, table.rowByPrizeId("a")!!.effectiveChance, 1.0e-9)
        assertEquals(0.75, table.rowByPrizeId("b")!!.effectiveChance, 1.0e-9)
    }

    @Test
    fun `percent flat computes empty chance from remainder`() {
        val crate = crate(
            selection = SelectionDefinition(mode = SelectionMode.PERCENT, structure = SelectionStructure.FLAT, allowEmpty = true),
            prizes = listOf(prize("a", chance = 70.0), prize("b", chance = 20.0)),
        )

        val table = ProbabilityEngine(Random(2)).buildTable(crate)
        assertEquals(0.10, table.emptyChance, 1.0e-9)
    }

    @Test
    fun `tiered weighted computes tier and local chance`() {
        val crate = crate(
            selection = SelectionDefinition(mode = SelectionMode.WEIGHTED, structure = SelectionStructure.TIERS),
            tiers = mapOf(
                "common" to PrizeTier("common", "Common", 80),
                "rare" to PrizeTier("rare", "Rare", 20),
            ),
            prizes = listOf(
                prize("c1", tier = "common", weight = 4),
                prize("c2", tier = "common", weight = 1),
                prize("r1", tier = "rare", weight = 1),
            ),
        )

        val table = ProbabilityEngine(Random(3)).buildTable(crate)
        val c1 = table.rowByPrizeId("c1")!!
        val r1 = table.rowByPrizeId("r1")!!
        assertEquals(0.8, c1.tierChance!!, 1.0e-9)
        assertEquals(0.8, c1.localChance, 1.0e-9)
        assertEquals(0.64, c1.effectiveChance, 1.0e-9)
        assertEquals(0.2, r1.effectiveChance, 1.0e-9)
    }

    @Test
    fun `tier pity chooses inside tier only`() {
        val crate = crate(
            selection = SelectionDefinition(mode = SelectionMode.WEIGHTED, structure = SelectionStructure.TIERS),
            tiers = mapOf(
                "common" to PrizeTier("common", "Common", 1),
                "rare" to PrizeTier("rare", "Rare", 1),
            ),
            prizes = listOf(
                prize("c1", tier = "common", weight = 1),
                prize("r1", tier = "rare", weight = 1),
            ),
        )
        val engine = ProbabilityEngine(Random(5))
        repeat(20) {
            assertEquals("rare", engine.roll(crate, pityTier = "rare").prize?.tier)
        }
    }

    @Test
    fun `simulation approximates expected weighted distribution`() {
        val crate = crate(
            selection = SelectionDefinition(mode = SelectionMode.WEIGHTED, structure = SelectionStructure.FLAT),
            prizes = listOf(prize("a", weight = 1), prize("b", weight = 9)),
        )
        val engine = ProbabilityEngine(Random(42))
        var aCount = 0
        val total = 50_000
        repeat(total) {
            if (engine.roll(crate).prize?.id == "a") aCount++
        }
        val observed = aCount.toDouble() / total.toDouble()
        assertTrue(observed in 0.08..0.12, "observed=$observed")
    }

    private fun crate(
        selection: SelectionDefinition = SelectionDefinition(),
        tiers: Map<String, PrizeTier> = emptyMap(),
        prizes: List<Prize>,
    ) = CrateDefinition(
        id = "demo",
        name = "Demo",
        type = CrateType.Unknown,
        keyDefinition = KeyDefinition("Key", emptyList(), Material.TRIPWIRE_HOOK, emptyList(), null, KeyProvider.Vanilla, null, false),
        prizes = prizes,
        tiers = tiers,
        selection = selection,
    )

    private fun prize(
        id: String,
        tier: String? = null,
        weight: Int = 1,
        chance: Double? = null,
    ) = Prize(
        id = id,
        name = id,
        tier = tier,
        weight = weight,
        chance = chance,
        broadcast = false,
        broadcastMessage = "",
        items = emptyList(),
        commands = emptyList(),
        messages = listOf("ok"),
    )
}
