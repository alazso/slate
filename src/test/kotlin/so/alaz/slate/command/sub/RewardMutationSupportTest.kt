package so.alaz.slate.command.sub

import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.KeyDefinition
import so.alaz.slate.crate.KeyProvider
import so.alaz.slate.crate.Prize
import so.alaz.slate.crate.PrizeItem
import so.alaz.slate.crate.PrizeTier
import so.alaz.slate.probability.SelectionDefinition
import so.alaz.slate.probability.SelectionMode
import so.alaz.slate.probability.SelectionStructure

class RewardMutationSupportTest {
    @Test
    fun `rename reward updates reward map key`() {
        val state = RewardMutationSupport.fromCrate(weightedCrate())
        val result = RewardMutationSupport.renameReward(state, "reward-a", "reward-renamed")
        assertTrue(result.success)
        assertTrue(state.rewards.containsKey("reward-renamed"))
        assertFalse(state.rewards.containsKey("reward-a"))
    }

    @Test
    fun `set chance rejects weighted mode`() {
        val state = RewardMutationSupport.fromCrate(weightedCrate())
        val result = RewardMutationSupport.setRewardChance(state, weightedCrate(), "reward-a", 10.0)
        assertFalse(result.success)
        assertEquals(RewardMutationSupport.Error.INVALID_MODE, result.error)
    }

    @Test
    fun `set tier rejects unknown tier`() {
        val crate = tieredCrate()
        val state = RewardMutationSupport.fromCrate(crate)
        val result = RewardMutationSupport.setRewardTier(state, crate, "reward-a", "unknown")
        assertFalse(result.success)
        assertEquals(RewardMutationSupport.Error.INVALID_TIER, result.error)
    }

    @Test
    fun `writeToYaml persists commands messages and items`() {
        val crate = weightedCrate()
        val state = RewardMutationSupport.fromCrate(crate)
        RewardMutationSupport.setCommands(state, "reward-a", listOf("say hello"))
        RewardMutationSupport.setMessages(state, "reward-a", listOf("<green>won</green>"))
        RewardMutationSupport.setItems(state, "reward-a", listOf(PrizeItem(material = "DIAMOND", amount = 2)))

        val yaml = YamlConfiguration()
        RewardMutationSupport.writeToYaml(state, yaml)

        assertEquals(listOf("say hello"), yaml.getStringList("prizes.reward-a.commands"))
        assertEquals(listOf("<green>won</green>"), yaml.getStringList("prizes.reward-a.messages"))
        val itemMap = yaml.getMapList("prizes.reward-a.items").firstOrNull().orEmpty()
        assertEquals("DIAMOND", itemMap["material"])
    }

    private fun weightedCrate(): CrateDefinition =
        CrateDefinition(
            id = "alpha",
            name = "Alpha",
            keyDefinition = KeyDefinition(
                name = "Key",
                lore = emptyList(),
                material = Material.TRIPWIRE_HOOK,
                customModelData = emptyList(),
                customItemId = null,
                provider = KeyProvider.Vanilla,
                providerItemId = null,
                glowing = false,
            ),
            selection = SelectionDefinition(mode = SelectionMode.WEIGHTED, structure = SelectionStructure.FLAT),
            prizes = listOf(
                Prize(
                    id = "reward-a",
                    name = "Reward A",
                    tier = null,
                    weight = 2,
                    chance = null,
                    broadcast = false,
                    broadcastMessage = "",
                    items = listOf(PrizeItem(material = "STONE")),
                    commands = emptyList(),
                    messages = emptyList(),
                ),
            ),
        )

    private fun tieredCrate(): CrateDefinition =
        weightedCrate().copy(
            selection = SelectionDefinition(mode = SelectionMode.WEIGHTED, structure = SelectionStructure.TIERS),
            tiers = mapOf("common" to PrizeTier(id = "common", displayName = "Common", weight = 1)),
            prizes = weightedCrate().prizes.map { it.copy(tier = "common") },
        )
}
