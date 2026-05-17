package so.alaz.slate.prize

import kotlin.random.Random
import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.CrateType
import so.alaz.slate.crate.KeyDefinition
import so.alaz.slate.crate.KeyProvider
import so.alaz.slate.crate.Prize
import so.alaz.slate.probability.SelectionDefinition
import so.alaz.slate.probability.SelectionMode
import so.alaz.slate.probability.SelectionStructure

class PrizeEngineImplTest {
    @Test
    fun `seeded random gives deterministic picks`() {
        val prizes = listOf(
            prize("a", 1, "common"),
            prize("b", 5, "rare"),
            prize("c", 10, "legendary"),
        )
        val crate = testCrate(prizes)

        val picksA = (1..5).map { PrizeEngineImpl(Random(7)).selectPrize(crate)!!.id }
        val picksB = (1..5).map { PrizeEngineImpl(Random(7)).selectPrize(crate)!!.id }
        assertEquals(picksA, picksB)
    }

    @Test
    fun `pity tier selection constrains pool`() {
        val engine = PrizeEngineImpl(Random(7))
        val prizes = listOf(prize("a", 1, "common"), prize("b", 1, "rare"), prize("c", 1, "rare"))
        val crate = testCrate(
            prizes,
            selection = SelectionDefinition(mode = SelectionMode.WEIGHTED, structure = SelectionStructure.TIERS),
        )
        val pick = engine.selectPrize(crate, pityTier = "rare", pityCount = 10)
        assertEquals(true, pick?.tier == "rare")
    }

    @Test
    fun `empty prize list returns empty result`() {
        val engine = PrizeEngineImpl()
        val crate = testCrate(emptyList())
        assertEquals(null, engine.selectPrize(crate))
    }

    private fun prize(id: String, weight: Int, tier: String?) = Prize(
        id = id,
        name = id,
        tier = tier,
        weight = weight,
        broadcast = false,
        broadcastMessage = "",
        items = emptyList(),
        commands = emptyList(),
        messages = emptyList(),
    )

    private fun testCrate(
        prizes: List<Prize>,
        selection: SelectionDefinition = SelectionDefinition(),
    ) = CrateDefinition(
        id = "test",
        name = "Test",
        type = CrateType.Unknown,
        keyDefinition = KeyDefinition("Key", emptyList(), Material.TRIPWIRE_HOOK, emptyList(), null, KeyProvider.Vanilla, null, false),
        prizes = prizes,
        tiers = mapOf("common" to so.alaz.slate.crate.PrizeTier("common", "Common", 1), "rare" to so.alaz.slate.crate.PrizeTier("rare", "Rare", 1), "legendary" to so.alaz.slate.crate.PrizeTier("legendary", "Legendary", 1)),
        selection = selection,
    )
}
