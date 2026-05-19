package so.alaz.slate.command.sub

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TierMutationSupportTest {
    @Test
    fun `renameTier updates reward tier references`() {
        val yaml = baseYaml()
        val state = TierMutationSupport.fromYaml(yaml)

        val result = TierMutationSupport.renameTier(state, "common", "starter")

        assertTrue(result.success)
        assertEquals("starter", state.rewardTierAssignments["reward-a"])
        assertTrue(state.tiers.containsKey("starter"))
        assertFalse(state.tiers.containsKey("common"))
    }

    @Test
    fun `deleteTier blocks referenced tiers`() {
        val yaml = baseYaml()
        val state = TierMutationSupport.fromYaml(yaml)

        val result = TierMutationSupport.deleteTier(state, "common")

        assertFalse(result.success)
        assertEquals(TierMutationSupport.Error.TIER_REFERENCED, result.error)
        assertEquals(listOf("reward-a"), result.referencedRewards)
    }

    @Test
    fun `writeToYaml rewrites tiers and prize assignments`() {
        val yaml = baseYaml()
        val state = TierMutationSupport.fromYaml(yaml)

        TierMutationSupport.setTierName(state, "rare", "Rare+")
        TierMutationSupport.setTierWeight(state, "rare", 9)
        TierMutationSupport.renameTier(state, "common", "starter")
        TierMutationSupport.writeToYaml(state, yaml)

        assertEquals("Rare+", yaml.getString("tiers.rare.name"))
        assertEquals(9, yaml.getInt("tiers.rare.weight"))
        assertEquals("starter", yaml.getString("prizes.reward-a.tier"))
        assertEquals("rare", yaml.getString("prizes.reward-b.tier"))
        assertFalse(yaml.contains("tiers.common"))
    }

    private fun baseYaml(): YamlConfiguration =
        YamlConfiguration().apply {
            set("tiers.common.name", "Common")
            set("tiers.common.weight", 5)
            set("tiers.rare.name", "Rare")
            set("tiers.rare.weight", 2)
            set("prizes.reward-a.tier", "common")
            set("prizes.reward-b.tier", "rare")
        }
}
